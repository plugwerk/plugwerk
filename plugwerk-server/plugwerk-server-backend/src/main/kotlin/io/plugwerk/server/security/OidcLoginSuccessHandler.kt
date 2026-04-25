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

import io.plugwerk.server.service.RefreshTokenService
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken
import org.springframework.security.web.authentication.AuthenticationSuccessHandler
import org.springframework.stereotype.Component

/**
 * Bridges Spring Security's OAuth2 browser flow into Plugwerk's existing
 * JWT + refresh-cookie session model (issue #79).
 *
 * Spring's OAuth2 client filter handles the heavy lifting (PKCE state, code
 * exchange with the provider, ID token validation) and hands us a fully
 * authenticated [OAuth2AuthenticationToken] when the user comes back from
 * the provider. From here we:
 *
 *  1. Map the OIDC `sub` claim to a stable Plugwerk `user_subject` of the
 *     form `<registrationId>:<sub>`. The `registrationId` half is anchored
 *     by [OidcRegistrationIds] and matches the provider's UUID, so the
 *     subject survives admin-UI renames of the provider's display name.
 *  2. Set the same httpOnly refresh cookie a local login would set (so the
 *     frontend's existing `hydrate()` flow can immediately exchange it for
 *     a Plugwerk access token via `/api/v1/auth/refresh`). We deliberately
 *     do NOT include the access token in the redirect — that would put a
 *     credential in the browser history and the access log. The frontend
 *     instead picks it up on its first XHR after the redirect.
 *  3. Redirect the browser to `/` (the SPA root). React-Router takes it
 *     from there: `useAuthStore.hydrate()` runs, sees the refresh cookie,
 *     and either lands the user on their dashboard or on `/onboarding`
 *     if no namespace membership exists yet.
 *
 * No HTTP-level access-token leakage, no credentials in URLs, no JSON
 * response (the redirect is the response). Same security properties as the
 * `POST /api/v1/auth/login` endpoint just with a different front-end trigger.
 */
@Component
class OidcLoginSuccessHandler(
    private val refreshTokenService: RefreshTokenService,
    private val refreshTokenCookieFactory: RefreshTokenCookieFactory,
) : AuthenticationSuccessHandler {

    private val log = LoggerFactory.getLogger(OidcLoginSuccessHandler::class.java)

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
        // The OAuth2User principal can technically be null in pure-OAuth2 (non-OIDC)
        // flows, but oauth2Login's standard flow always populates it. Bail loudly if
        // an unexpected configuration ever produces a null principal here.
        val principal = requireNotNull(oauthToken.principal) {
            "OAuth2AuthenticationToken for $registrationId has no principal"
        }
        val sub = principal.attributes["sub"] as? String
            ?: error(
                "OIDC provider $registrationId returned a token without a `sub` claim — " +
                    "cannot map to a Plugwerk user_subject.",
            )

        val userSubject = "$registrationId:$sub"
        val refresh = refreshTokenService.issue(userSubject)
        val cookie = refreshTokenCookieFactory.build(refresh.plaintext, refresh.maxAge)

        log.info("OIDC login success — registrationId={} sub={} userSubject={}", registrationId, sub, userSubject)

        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString())
        // Always redirect to the SPA root; the frontend's hydrate() will run
        // /api/v1/auth/refresh against the cookie we just set and either land
        // the user on their default namespace or on /onboarding.
        response.sendRedirect("/")
    }
}
