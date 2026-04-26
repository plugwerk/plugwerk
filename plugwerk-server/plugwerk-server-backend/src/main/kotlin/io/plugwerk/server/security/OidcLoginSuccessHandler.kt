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
package io.plugwerk.server.security

import io.plugwerk.server.repository.OidcProviderRepository
import io.plugwerk.server.service.OidcEmailMissingException
import io.plugwerk.server.service.OidcIdentityService
import io.plugwerk.server.service.RefreshTokenService
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.security.web.authentication.AuthenticationSuccessHandler
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Bridges Spring Security's OAuth2 browser flow into Plugwerk's existing
 * JWT + refresh-cookie session model (issue #79, refactored for #351).
 *
 * Spring's OAuth2 client filter handles the heavy lifting (PKCE state, code
 * exchange with the provider, ID token validation) and hands us a fully
 * authenticated [OAuth2AuthenticationToken] when the user comes back from
 * the provider. From here we:
 *
 *  1. Resolve or create the Plugwerk identity via [OidcIdentityService.upsertOnLogin].
 *     The synthetic `<provider-uuid>:<sub>` username hack from PR #350 is gone —
 *     OIDC subjects now live in the dedicated `oidc_identity` table and link to
 *     a proper [io.plugwerk.server.domain.UserEntity] row with `source = OIDC`.
 *  2. Set the same httpOnly refresh cookie a local login would set, with the
 *     upstream ID token captured for later RP-Initiated Logout (#352).
 *  3. Redirect the browser to `/` (the SPA root). React-Router takes it
 *     from there: `useAuthStore.hydrate()` runs, sees the refresh cookie,
 *     and either lands the user on their dashboard or on `/onboarding`.
 *
 * No HTTP-level access-token leakage, no credentials in URLs, no JSON
 * response (the redirect is the response).
 */
@Component
class OidcLoginSuccessHandler(
    private val refreshTokenService: RefreshTokenService,
    private val refreshTokenCookieFactory: RefreshTokenCookieFactory,
    private val oidcIdentityService: OidcIdentityService,
    private val oidcProviderRepository: OidcProviderRepository,
) : AuthenticationSuccessHandler {

    private val log = LoggerFactory.getLogger(OidcLoginSuccessHandler::class.java)

    @Transactional
    override fun onAuthenticationSuccess(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authentication: Authentication,
    ) {
        val oauthToken = authentication as? OAuth2AuthenticationToken
            ?: error(
                "OidcLoginSuccessHandler invoked with non-OAuth2 authentication: " +
                    "${authentication.javaClass.name}. Spring Security wiring is broken.",
            )
        val registrationId = oauthToken.authorizedClientRegistrationId
        val principal = requireNotNull(oauthToken.principal) {
            "OAuth2AuthenticationToken for $registrationId has no principal"
        }
        val sub = principal.attributes["sub"] as? String
            ?: error(
                "OIDC provider $registrationId returned a token without a `sub` claim — " +
                    "cannot map to a Plugwerk user.",
            )

        // The registrationId is the OIDC provider's UUID (see OidcRegistrationIds.of).
        // It must exist in the DB or the OAuth2 client filter could not have built a
        // ClientRegistration for it in the first place — so an Optional.empty() here
        // would indicate a race with a provider deletion. Treat as a wiring failure.
        val providerId = runCatching { UUID.fromString(registrationId) }.getOrNull()
            ?: error("OIDC registrationId $registrationId is not a valid UUID")
        val provider = oidcProviderRepository.findById(providerId).orElseThrow {
            IllegalStateException("OIDC provider $providerId no longer exists in the database")
        }

        val user = try {
            oidcIdentityService.upsertOnLogin(provider, sub, principal.attributes)
        } catch (e: OidcEmailMissingException) {
            log.warn("Rejecting OIDC login for provider={} sub={}: {}", provider.name, sub, e.message)
            // 400 + plain-text body — operator-actionable misconfiguration, not a
            // user-fixable condition. The browser shows it as a Spring whitelabel
            // page, which is acceptable for a setup-time error.
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, e.message)
            return
        }

        // Capture the upstream ID token so logout can perform RP-Initiated Logout
        // against the IdP later (#352). Pure-OAuth2 (non-OIDC) flows have no ID
        // token and pass null — RP-Initiated Logout falls back gracefully.
        val idTokenValue = (principal as? OidcUser)?.idToken?.tokenValue
        val userId = requireNotNull(user.id) { "Newly upserted UserEntity has no id" }
        val refresh = refreshTokenService.issue(userId, idTokenValue)
        val cookie = refreshTokenCookieFactory.build(refresh.plaintext, refresh.maxAge)

        log.info("OIDC login success — provider={} sub={} user_id={}", provider.name, sub, userId)

        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString())
        // Always redirect to the SPA root; the frontend's hydrate() will run
        // /api/v1/auth/refresh against the cookie we just set and either land
        // the user on their default namespace or on /onboarding.
        response.sendRedirect("/")
    }
}
