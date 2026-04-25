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
import io.plugwerk.server.repository.UserRepository
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
) : AuthApi {

    override fun login(loginRequest: LoginRequest): ResponseEntity<LoginResponse> {
        if (!credentialValidator.validate(loginRequest.username, loginRequest.password)) {
            return ResponseEntity.status(401).build()
        }
        val user = userRepository.findByUsername(loginRequest.username).orElse(null)
        val passwordChangeRequired = user?.passwordChangeRequired ?: false
        val isSuperadmin = user?.isSuperadmin ?: false
        val accessToken = jwtTokenService.generateToken(loginRequest.username)
        val refresh = refreshTokenService.issue(loginRequest.username)
        val cookie = refreshTokenCookieFactory.build(refresh.plaintext, refresh.maxAge)
        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, cookie.toString())
            .body(
                LoginResponse(
                    accessToken = accessToken,
                    tokenType = "Bearer",
                    expiresIn = jwtTokenService.tokenValiditySeconds(),
                    passwordChangeRequired = passwordChangeRequired,
                    isSuperadmin = isSuperadmin,
                ),
            )
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
                val accessToken = jwtTokenService.generateToken(user.username)
                val cookie = refreshTokenCookieFactory.build(
                    result.issuedToken.plaintext,
                    result.issuedToken.maxAge,
                )
                ResponseEntity.ok()
                    .header(HttpHeaders.SET_COOKIE, cookie.toString())
                    .body(
                        LoginResponse(
                            accessToken = accessToken,
                            tokenType = "Bearer",
                            expiresIn = jwtTokenService.tokenValiditySeconds(),
                            passwordChangeRequired = user.passwordChangeRequired,
                            isSuperadmin = user.isSuperadmin,
                        ),
                    )
            }

            RefreshTokenService.RotationResult.Reused,
            RefreshTokenService.RotationResult.Unknown,
            -> unauthorizedWithClearedCookie()
        }
    }

    override fun logout(): ResponseEntity<Unit> {
        val authentication = currentAuthentication()
        val jwt = authentication.credentials as? Jwt
            ?: throw UnauthorizedException("Bearer token required for logout")
        val jti = jwt.id ?: throw UnauthorizedException("Token missing jti claim")
        val expiresAt = jwt.expiresAt ?: throw UnauthorizedException("Token missing exp claim")
        tokenRevocationService.revokeToken(jti, jwt.subject, expiresAt)

        // Also revoke the refresh-token family (if a cookie is attached — it will not be
        // on cross-origin Bearer-only API clients) and clear the cookie on the response.
        readRefreshCookie()?.let { refreshTokenService.revokePresentedFamily(it) }
        return ResponseEntity.noContent()
            .header(HttpHeaders.SET_COOKIE, refreshTokenCookieFactory.clear().toString())
            .build()
    }

    override fun changePassword(changePasswordRequest: ChangePasswordRequest): ResponseEntity<Unit> {
        val username = currentAuthentication().name
            ?: throw UnauthorizedException("Authentication required to change password")
        userService.changePassword(username, changePasswordRequest.currentPassword, changePasswordRequest.newPassword)
        tokenRevocationService.revokeAllForUser(username)
        refreshTokenService.revokeAllForUser(username, RefreshTokenService.LOGOUT)
        return ResponseEntity.noContent()
            .header(HttpHeaders.SET_COOKIE, refreshTokenCookieFactory.clear().toString())
            .build()
    }

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
