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

import io.plugwerk.server.domain.OidcProviderType
import io.plugwerk.server.repository.OidcProviderRepository
import jakarta.servlet.http.HttpServletRequest
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Adds the OIDC `prompt` query parameter to the upstream authorization
 * request when the operator requested an account-picker / re-auth screen
 * from the Plugwerk login page (issue #410).
 *
 * ## Why
 *
 * Spring's default `OAuth2AuthorizationRequestResolver` sends no `prompt`
 * hint, so a logged-out Plugwerk user clicking the "Sign in with Google"
 * button gets silently re-authenticated as the same upstream account —
 * the IdP's session cookie is still valid. There is no built-in way for
 * the user to switch to a different account without a private window.
 *
 * The Plugwerk login page now renders a small "Use a different account"
 * link below each provider button. That link points at
 * `/oauth2/authorization/{registrationId}?prompt=…`. This resolver
 * intercepts the request, validates the `prompt` value strictly, and
 * passes it to the upstream as `additionalParameters["prompt"]`.
 *
 * ## Allow-list
 *
 * Only two `prompt` values reach the upstream — anything else is dropped
 * silently (no echo, no error, no upstream forwarding):
 *
 *   - `select_account` — OIDC (and Google's specific dialect): show the
 *     account picker. Used for `OIDC`, `GOOGLE`, `FACEBOOK` providers.
 *   - `login` — OIDC standard: force re-authentication. Used for the
 *     `OAUTH2` (generic OAuth2) provider type as a best-effort hint.
 *
 * Anything else — `consent`, `none`, arbitrary strings, multiple values
 * — is dropped. This is the only line of defence against a malicious
 * caller trying to inject upstream-side parameters via the query string.
 *
 * ## GitHub carve-out
 *
 * GitHub's OAuth2 implementation does not honour the `prompt` parameter
 * — it is silently ignored at GitHub's authorize endpoint today, but
 * passing it is misleading to the operator (suggests something will
 * happen) and may break with future strictness changes at GitHub. For
 * `OidcProviderType.GITHUB` this resolver passes the request through
 * unchanged regardless of the inbound `prompt` value. The frontend
 * mirrors this — no clickable link is rendered for GitHub providers.
 *
 * ## PKCE / state preservation
 *
 * Adding `prompt` rebuilds the `OAuth2AuthorizationRequest` via the
 * builder's `from(...)` constructor and `additionalParameters { mutator }`
 * setter, both of which copy every other field — including the PKCE
 * `code_verifier` (lives in `attributes`), the `state`, the resolved
 * redirect URI, and the scope set. A regression here would be the most
 * dangerous failure mode; the unit test pins it.
 */
@Component
class PromptAwareOAuth2AuthorizationRequestResolver(
    clientRegistrationRepository: ClientRegistrationRepository,
    private val oidcProviderRepository: OidcProviderRepository,
) : OAuth2AuthorizationRequestResolver {

    private val delegate = DefaultOAuth2AuthorizationRequestResolver(
        clientRegistrationRepository,
        DEFAULT_AUTHORIZATION_REQUEST_BASE_URI,
    )

    override fun resolve(request: HttpServletRequest): OAuth2AuthorizationRequest? {
        val resolved = delegate.resolve(request) ?: return null
        return maybeAddPrompt(request, resolved)
    }

    override fun resolve(request: HttpServletRequest, clientRegistrationId: String?): OAuth2AuthorizationRequest? {
        val resolved = delegate.resolve(request, clientRegistrationId) ?: return null
        return maybeAddPrompt(request, resolved)
    }

    private fun maybeAddPrompt(
        request: HttpServletRequest,
        resolved: OAuth2AuthorizationRequest,
    ): OAuth2AuthorizationRequest {
        val promptValue = readWhitelistedPrompt(request) ?: return resolved
        val registrationId = resolved.attributes[REGISTRATION_ID_ATTR] as? String ?: return resolved
        val providerType = providerTypeFor(registrationId) ?: return resolved
        // GitHub does not honour `prompt`. Drop it silently rather than
        // forwarding a parameter the upstream will ignore — keeps the
        // resolver's contract honest with what actually happens.
        if (providerType == OidcProviderType.GITHUB) return resolved
        return OAuth2AuthorizationRequest.from(resolved)
            .additionalParameters { params -> params["prompt"] = promptValue }
            .build()
    }

    /**
     * Reads `?prompt=…` from the inbound query string and returns the
     * value only if it is exactly one of the allow-listed strings.
     * Returns `null` for: missing parameter, unknown value, multiple
     * values (parameter pollution defence), empty value.
     */
    private fun readWhitelistedPrompt(request: HttpServletRequest): String? {
        val values = request.getParameterValues("prompt") ?: return null
        if (values.size != 1) return null
        val candidate = values[0]
        return if (candidate in ALLOWED_PROMPT_VALUES) candidate else null
    }

    /**
     * Resolves the [OidcProviderType] for a Spring `registrationId`. The
     * registrationId is the entity UUID (see [OidcRegistrationIds.of]),
     * so this is a single PK lookup against `oidc_provider`. Authorize
     * requests are not high-frequency (one per user-initiated login
     * click), so we accept the per-request roundtrip rather than
     * mirroring the in-memory map of [DbClientRegistrationRepository].
     *
     * Returns `null` for malformed registration IDs (treated as
     * "skip the prompt addition" — the underlying request will fail
     * downstream when Spring cannot find the registration anyway).
     */
    private fun providerTypeFor(registrationId: String): OidcProviderType? {
        val uuid = runCatching { UUID.fromString(registrationId) }.getOrNull() ?: return null
        return oidcProviderRepository.findById(uuid).map { it.providerType }.orElse(null)
    }

    companion object {
        const val DEFAULT_AUTHORIZATION_REQUEST_BASE_URI = "/oauth2/authorization"

        /**
         * The OIDC standard attribute key Spring uses to carry the
         * registration id on the resolved request. Re-declared here as
         * a constant so a typo cannot silently break the GitHub
         * carve-out.
         */
        const val REGISTRATION_ID_ATTR = "registration_id"

        /**
         * The closed set of `prompt` values we forward upstream.
         *   - `select_account` is the OIDC (and Google) value to pop
         *     the account picker.
         *   - `login` is the OIDC value to force a fresh upstream
         *     authentication; sent for generic OAuth2 providers.
         */
        val ALLOWED_PROMPT_VALUES: Set<String> = setOf("select_account", "login")
    }
}
