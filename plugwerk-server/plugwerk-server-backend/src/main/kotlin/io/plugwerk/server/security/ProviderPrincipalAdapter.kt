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

/**
 * Per-provider extraction of the four facts Plugwerk needs from an OAuth2/OIDC
 * callback in order to materialise a `plugwerk_user` row and a refresh-token cookie:
 *
 *  - **subject** — stable, provider-local user identifier. For OIDC providers this
 *    is the `sub` claim; for pure-OAuth2 providers (GitHub, Facebook) it is the
 *    provider's own integer/string id surfaced as `attributes["id"]`.
 *  - **email** — required by [io.plugwerk.server.domain.UserEntity.email]. May be
 *    `null` for providers that do not surface it in the userinfo response (e.g.
 *    GitHub when the user has no public email and the `user:email` scope was not
 *    granted, or when the primary email is private). Callers translate `null`
 *    into [io.plugwerk.server.service.OidcEmailMissingException].
 *  - **displayName** — best-effort human label. Falls back to the subject when
 *    no claim/attribute is suitable.
 *  - **upstreamIdToken** — captured for RP-Initiated Logout (#352). `null` for
 *    pure-OAuth2 providers; the logout path handles that gracefully.
 *
 * Implementations live in [OidcPrincipalAdapter] (covers `OIDC` and `GOOGLE`),
 * [GitHubPrincipalAdapter] (#357 Phase 3), and [FacebookPrincipalAdapter] (#357
 * Phase 4). Lookup happens via [ProviderPrincipalAdapterRegistry] keyed on
 * [io.plugwerk.server.domain.OidcProviderType].
 */
sealed interface ProviderPrincipalAdapter {

    /** Provider types this adapter handles. Registry uses this for lookup. */
    val providerTypes: Set<OidcProviderType>

    /**
     * Pulls the four [ResolvedPrincipal] fields out of the authenticated token
     * Spring Security hands us. Implementations may perform additional HTTP
     * calls to the provider (e.g. GitHub's `/user/emails`) when the principal
     * itself does not carry every needed value.
     */
    fun resolve(authentication: OAuth2AuthenticationToken): ResolvedPrincipal
}

/**
 * Provider-agnostic snapshot of the four facts a successful login produces.
 * Crossing this boundary, callers no longer care which provider type backed
 * the login — the downstream `OidcIdentityService.upsertOnLogin` and
 * `OidcLoginSuccessHandler` work uniformly off this struct.
 *
 * `email = null` is the explicit "no email available" signal; downstream
 * raises [io.plugwerk.server.service.OidcEmailMissingException].
 */
data class ResolvedPrincipal(
    val subject: String,
    val email: String?,
    val displayName: String?,
    val upstreamIdToken: String?,
)
