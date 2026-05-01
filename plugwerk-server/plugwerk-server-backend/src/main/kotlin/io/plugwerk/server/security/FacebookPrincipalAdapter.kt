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
import org.springframework.stereotype.Component

/**
 * Adapter for the pure-OAuth2 Facebook provider type (#357 phase 4).
 *
 * Facebook's shape sits between OIDC and GitHub:
 *
 *   - Like GitHub, there is no `/.well-known/openid-configuration`, no ID
 *     token, and the stable subject is `attributes["id"]` rather than the
 *     OIDC `sub` claim.
 *   - Unlike GitHub, the userinfo response (`/me`) carries `email` directly
 *     when the app has been granted the `email` permission. There is no
 *     out-of-band fetch endpoint to recover a hidden primary email — if
 *     `email` is missing here, the only path forward is for the operator
 *     to complete Facebook App Review for the `email` permission (apps
 *     in Development mode can authenticate developer/tester accounts only).
 *
 * The `Facebook App Review`-shaped error message lives in
 * [io.plugwerk.server.service.OidcEmailMissingException] (#357 phase 2);
 * this adapter just returns `email = null` when the userinfo response did
 * not surface one and lets the downstream raise the exception.
 *
 * `upstreamIdToken` is always `null` — there is no ID token. The
 * `/auth/logout` RP-Initiated Logout fallback (#352) handles that path
 * gracefully.
 */
@Component
class FacebookPrincipalAdapter : ProviderPrincipalAdapter {

    override val providerTypes: Set<OidcProviderType> = setOf(OidcProviderType.FACEBOOK)

    override fun resolve(authentication: OAuth2AuthenticationToken): ResolvedPrincipal {
        val registrationId = authentication.authorizedClientRegistrationId
        val principal = requireNotNull(authentication.principal) {
            "OAuth2AuthenticationToken for $registrationId has no principal"
        }

        // Facebook's user-id is a numeric string already (returned as a String,
        // unlike GitHub which returns an Integer/Long). `toString()` is a
        // safety net for either shape.
        val rawId = principal.attributes["id"]
            ?: error(
                "Facebook provider $registrationId returned a userinfo response without an `id` attribute — " +
                    "cannot map to a Plugwerk user.",
            )
        val subject = rawId.toString()

        // `name` is Facebook's canonical display label (full name). It is
        // present whenever the `public_profile` permission is granted, which
        // is implicit in every Facebook OAuth flow.
        val displayName = (principal.attributes["name"] as? String)?.trim()?.takeIf { it.isNotBlank() }

        // `email` is present when the `email` permission was granted. Apps in
        // Development mode can request it for developer/tester accounts;
        // production apps need Facebook App Review approval. When absent we
        // surface null and the caller raises OidcEmailMissingException with
        // the App-Review-shaped message from #357 phase 2.
        val email = (principal.attributes["email"] as? String)?.trim()?.takeIf { it.isNotBlank() }

        return ResolvedPrincipal(
            subject = subject,
            email = email,
            displayName = displayName,
            upstreamIdToken = null,
        )
    }
}
