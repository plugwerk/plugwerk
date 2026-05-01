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
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.stereotype.Component

/**
 * Adapter for fully OIDC-conformant providers. Covers
 * [OidcProviderType.OIDC] (Keycloak / Authentik / Auth0 / Dex /
 * any provider that publishes `/.well-known/openid-configuration`) and
 * [OidcProviderType.GOOGLE] (which is OIDC-conformant; the only difference
 * from `OIDC` is the hard-coded issuer URI in
 * [DbClientRegistrationRepository], not the principal shape).
 *
 * The principal Spring hands us is an [OidcUser], so all four
 * [ResolvedPrincipal] fields come straight from the ID token claims; no
 * out-of-band HTTP calls are needed.
 */
@Component
class OidcPrincipalAdapter : ProviderPrincipalAdapter {

    override val providerTypes: Set<OidcProviderType> =
        setOf(OidcProviderType.OIDC, OidcProviderType.GOOGLE)

    override fun resolve(authentication: OAuth2AuthenticationToken): ResolvedPrincipal {
        val registrationId = authentication.authorizedClientRegistrationId
        val principal = requireNotNull(authentication.principal) {
            "OAuth2AuthenticationToken for $registrationId has no principal"
        }
        val subject = principal.attributes["sub"] as? String
            ?: error(
                "OIDC provider $registrationId returned a token without a `sub` claim — " +
                    "cannot map to a Plugwerk user.",
            )
        val email = (principal.attributes["email"] as? String)?.trim()?.takeIf { it.isNotBlank() }
        // displayName precedence:
        //   1. `name` (full name, what most IdPs render in their own UIs)
        //   2. `preferred_username` (handle / login alias)
        //   3. null (caller falls back to subject)
        val displayName = (principal.attributes["name"] as? String)?.trim()?.takeIf { it.isNotBlank() }
            ?: (principal.attributes["preferred_username"] as? String)?.trim()?.takeIf { it.isNotBlank() }
        val upstreamIdToken = (principal as? OidcUser)?.idToken?.tokenValue
        return ResolvedPrincipal(
            subject = subject,
            email = email,
            displayName = displayName,
            upstreamIdToken = upstreamIdToken,
        )
    }
}
