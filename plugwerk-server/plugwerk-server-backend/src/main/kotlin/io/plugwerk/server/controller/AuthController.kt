/*
 * Copyright (c) 2025-present devtank42 GmbH
 *
 * This file is part of Plugwerk.
 *
 * Plugwerk is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Plugwerk is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Plugwerk. If not, see <https://www.gnu.org/licenses/>.
 */
package io.plugwerk.server.controller

import io.plugwerk.api.AuthApi
import io.plugwerk.api.model.ChangePasswordRequest
import io.plugwerk.api.model.LoginRequest
import io.plugwerk.api.model.LoginResponse
import io.plugwerk.api.model.LogoutResponse
import io.plugwerk.server.domain.UserEntity
import io.plugwerk.server.domain.UserSource
import io.plugwerk.server.repository.OidcIdentityRepository
import io.plugwerk.server.repository.UserRepository
import io.plugwerk.server.security.CurrentUserResolver
import io.plugwerk.server.security.OidcEndSessionUrlResolver
import io.plugwerk.server.security.RefreshTokenCookieFactory
import io.plugwerk.server.security.UserCredentialValidator
import io.plugwerk.server.security.currentAuthentication
import io.plugwerk.server.service.JwtTokenService
import io.plugwerk.server.service.RefreshTokenService
import io.plugwerk.server.service.TokenRevocationService
import io.plugwerk.server.service.UnauthorizedException
import io.plugwerk.server.service.UserService
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import java.util.UUID

@RestController
@RequestMapping("/api/v1")
class AuthController(
    private val credentialValidator: UserCredentialValidator,
    private val jwtTokenService: JwtTokenService,
    private val tokenRevocationService: TokenRevocationService,
    private val refreshTokenService: RefreshTokenService,
    private val refreshTokenCookieFactory: RefreshTokenCookieFactory,
    private val userRepository: UserRepository,
    private val userService: UserService,
    private val oidcEndSessionUrlResolver: OidcEndSessionUrlResolver,
    private val oidcIdentityRepository: OidcIdentityRepository,
    private val currentUserResolver: CurrentUserResolver,
) : AuthApi {

    override fun login(loginRequest: LoginRequest): ResponseEntity<LoginResponse> {
        if (!credentialValidator.validate(loginRequest.username, loginRequest.password)) {
            return ResponseEntity.status(401).build()
        }
        // The validator's contract guarantees a LOCAL row matches; load it for the
        // post-validation lookup (passwordChangeRequired / isSuperadmin / userId for
        // the JWT subject + refresh-token row).
        val user = userRepository.findByUsernameAndSource(loginRequest.username, UserSource.LOCAL).orElse(null)
            ?: return ResponseEntity.status(401).build()
        return issueLoginResponse(user)
    }

    /**
     * Refresh-cookie endpoint (ADR-0027 / #294). Reads the `plugwerk_refresh` cookie, burns
     * the presented token, issues a successor, and returns a fresh access token. CSRF
     * protection is re-enabled on this path only — the frontend must send the matching
     * `X-XSRF-TOKEN` header. Not part of the OpenAPI-generated [AuthApi] because the
     * generator does not model `Set-Cookie` response headers; the contract lives in
     * ADR-0027.
     */
    @PostMapping("/auth/refresh")
    fun refresh(): ResponseEntity<LoginResponse> {
        val refreshPlaintext = readRefreshCookie() ?: return unauthorizedWithClearedCookie()
        return when (val result = refreshTokenService.rotate(refreshPlaintext)) {
            is RefreshTokenService.RotationResult.Success -> {
                val user = userRepository.findById(result.userId).orElse(null)
                    ?: return unauthorizedWithClearedCookie()
                val accessToken = jwtTokenService.generateToken(requireNotNull(user.id).toString())
                val cookie = refreshTokenCookieFactory.build(
                    result.issuedToken.plaintext,
                    result.issuedToken.maxAge,
                )
                ResponseEntity.ok()
                    .header(HttpHeaders.SET_COOKIE, cookie.toString())
                    .body(buildLoginResponse(user, accessToken))
            }

            RefreshTokenService.RotationResult.Reused,
            RefreshTokenService.RotationResult.Unknown,
            -> unauthorizedWithClearedCookie()
        }
    }

    override fun logout(): ResponseEntity<LogoutResponse> {
        val authentication = currentAuthentication()
        val jwt = authentication.credentials as? Jwt
            ?: throw UnauthorizedException("Bearer token required for logout")
        val jti = jwt.id ?: throw UnauthorizedException("Token missing jti claim")
        val expiresAt = jwt.expiresAt ?: throw UnauthorizedException("Token missing exp claim")
        val subject = jwt.subject ?: throw UnauthorizedException("Token missing sub claim")
        tokenRevocationService.revokeToken(jti, subject, expiresAt)

        // Resolve the upstream RP-Initiated Logout URL *before* burning the refresh-token
        // family — once the row is revoked we lose access to the upstream id_token hint
        // it carried (#352).
        val refreshPlaintext = readRefreshCookie()
        val endSessionUrl = refreshPlaintext?.let { resolveEndSessionUrl(subject, it) }
        refreshPlaintext?.let { refreshTokenService.revokePresentedFamily(it) }

        val cookieClearHeader = refreshTokenCookieFactory.clear().toString()
        return if (endSessionUrl != null) {
            ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookieClearHeader)
                .body(LogoutResponse(endSessionUrl = endSessionUrl))
        } else {
            ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookieClearHeader)
                .build()
        }
    }

    /**
     * Builds the RP-Initiated Logout URL for OIDC subjects (#352, post-#351 implementation).
     *
     * After the identity-hub split (#351), the JWT `sub` is `plugwerk_user.id` regardless
     * of LOCAL vs OIDC source. We discriminate via `oidc_identity.user_id` lookup —
     * presence of a row means OIDC, absence means LOCAL. Returns `null` for LOCAL users,
     * for providers that do not advertise `end_session_endpoint`, and for any path that
     * leaves the row without an upstream id-token hint.
     */
    private fun resolveEndSessionUrl(jwtSubject: String, refreshPlaintext: String): String? {
        val userId = runCatching { UUID.fromString(jwtSubject) }.getOrNull() ?: return null
        val identity = oidcIdentityRepository.findByUserId(userId).orElse(null) ?: return null
        val registrationId = requireNotNull(identity.oidcProvider.id) {
            "OidcProviderEntity referenced from oidc_identity has no id — entity not persisted?"
        }.toString()
        val idTokenHint = refreshTokenService.findUpstreamIdToken(refreshPlaintext)
        return oidcEndSessionUrlResolver.resolve(registrationId, idTokenHint)
    }

    override fun changePassword(changePasswordRequest: ChangePasswordRequest): ResponseEntity<Unit> {
        val userId = currentUserResolver.currentUserId()
        userService.changePassword(userId, changePasswordRequest.currentPassword, changePasswordRequest.newPassword)
        tokenRevocationService.revokeAllForUser(userId)
        refreshTokenService.revokeAllForUser(userId, RefreshTokenService.LOGOUT)
        return ResponseEntity.noContent()
            .header(HttpHeaders.SET_COOKIE, refreshTokenCookieFactory.clear().toString())
            .build()
    }

    /** Issues a fresh access token + refresh cookie pair for [user] and returns the response. */
    private fun issueLoginResponse(user: UserEntity): ResponseEntity<LoginResponse> {
        val userId = requireNotNull(user.id) { "User ${user.username} has no id — entity not persisted?" }
        val accessToken = jwtTokenService.generateToken(userId.toString())
        val refresh = refreshTokenService.issue(userId)
        val cookie = refreshTokenCookieFactory.build(refresh.plaintext, refresh.maxAge)
        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, cookie.toString())
            .body(buildLoginResponse(user, accessToken))
    }

    private fun buildLoginResponse(user: UserEntity, accessToken: String): LoginResponse = LoginResponse(
        accessToken = accessToken,
        tokenType = "Bearer",
        expiresIn = jwtTokenService.tokenValiditySeconds(),
        userId = requireNotNull(user.id),
        displayName = user.displayName,
        email = user.email,
        source = when (user.source) {
            UserSource.LOCAL -> LoginResponse.Source.LOCAL
            UserSource.OIDC -> LoginResponse.Source.OIDC
        },
        passwordChangeRequired = user.passwordChangeRequired,
        isSuperadmin = user.isSuperadmin,
        username = user.username,
    )

    /** Reads the refresh cookie via the request-scoped HttpServletRequest. */
    private fun readRefreshCookie(): String? {
        val request = (RequestContextHolder.getRequestAttributes() as? ServletRequestAttributes)
            ?.request ?: return null
        return readRefreshCookie(request)
    }

    private fun readRefreshCookie(request: HttpServletRequest): String? =
        request.cookies?.firstOrNull { it.name == RefreshTokenCookieFactory.COOKIE_NAME }?.value

    private fun unauthorizedWithClearedCookie(): ResponseEntity<LoginResponse> = ResponseEntity.status(401)
        .header(HttpHeaders.SET_COOKIE, refreshTokenCookieFactory.clear().toString())
        .build()
}
