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

import io.plugwerk.server.PlugwerkProperties
import org.springframework.stereotype.Component
import org.springframework.web.util.UriComponentsBuilder

/**
 * Builds an [OpenID Connect RP-Initiated Logout 1.0](https://openid.net/specs/openid-connect-rpinitiated-1_0.html)
 * URL for an OIDC-sourced session (issue #352).
 *
 * The output looks like:
 *
 * ```
 * https://auth.example.com/realms/plugwerk/protocol/openid-connect/logout
 *   ?id_token_hint=eyJ...
 *   &post_logout_redirect_uri=https%3A%2F%2Fapp.example.com%2Flogin
 * ```
 *
 * The browser is supposed to navigate to this URL on logout; the IdP destroys its
 * session and redirects back to `post_logout_redirect_uri`. Without this round-trip
 * the IdP keeps its long-lived session cookies (`KEYCLOAK_IDENTITY` / `KEYCLOAK_SESSION`,
 * 10 h Max-Age in our dev realm) and the next "Login with Keycloak" silently re-uses
 * them — exactly the UX bug this resolver fixes.
 *
 * ## Discovery
 *
 * The end-session endpoint is not part of the OAuth2 spec — it lives in OIDC's session
 * management extension and ships through provider metadata under the
 * `end_session_endpoint` key. Spring Security's [ClientRegistration.providerDetails]
 * exposes that as part of the discovery-driven registration build (see
 * [DbClientRegistrationRepository.buildRegistration]). Providers that do not advertise
 * the key (vanilla OAuth2 surfaces like the GitHub OAuth provider) fall through to
 * `null` here, and the caller degrades gracefully to a local-cookie-clear-only logout.
 *
 * ## Allow-list considerations
 *
 * `post_logout_redirect_uri` must match an entry in the IdP's allow-list (Keycloak:
 * client `attributes."post.logout.redirect.uris"`). We pin it to
 * `${plugwerk.server.base-url}/login` — the SPA route the operator has already had
 * to whitelist for the OAuth2 redirect-URI machinery. This avoids a per-provider
 * `postLogoutRedirectUri` column for now; a richer model can grow alongside the
 * provider-edit work in #353 if it turns out one global value is not enough.
 *
 * ## Threat model
 *
 * The RP-Initiated Logout request itself is unauthenticated — it relies on the
 * `id_token_hint` to identify which session to terminate. An attacker who steals an
 * ID token cannot use the endpoint to do anything more harmful than log the user
 * out (and only at the same IdP), so the leak surface is small. We still avoid
 * logging the URL (it embeds the ID token) and never persist the resolved URL —
 * it lives only in the HTTP response body.
 */
@Component
class OidcEndSessionUrlResolver(
    private val clientRegistrationRepository: DbClientRegistrationRepository,
    private val props: PlugwerkProperties,
) {

    /**
     * Resolves the RP-Initiated Logout URL for [registrationId], or `null` when the
     * provider does not advertise an `end_session_endpoint`. Caller is responsible
     * for falling back to a plain cookie-clear in the null case.
     *
     * @param registrationId UUID-shaped Spring Security registration id (matches
     *   [OidcRegistrationIds.of] for the source provider).
     * @param idTokenHint Raw upstream ID token captured at login time. Optional —
     *   most IdPs accept the request without it (and rely on a session-cookie
     *   match), but Keycloak strongly prefers the hint and the spec recommends it.
     */
    fun resolve(registrationId: String, idTokenHint: String?): String? {
        val registration = clientRegistrationRepository.findByRegistrationId(registrationId) ?: return null
        val endpoint = registration.providerDetails.configurationMetadata["end_session_endpoint"] as? String
            ?: return null
        val postLogoutRedirect = "${props.server.baseUrl.trimEnd('/')}/login"
        return UriComponentsBuilder.fromUriString(endpoint)
            .apply { idTokenHint?.let { queryParam("id_token_hint", it) } }
            .queryParam("post_logout_redirect_uri", postLogoutRedirect)
            .encode()
            .build()
            .toUriString()
    }
}
