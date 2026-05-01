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
import org.slf4j.LoggerFactory
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken
import org.springframework.stereotype.Component

/**
 * Adapter for the pure-OAuth2 GitHub provider type (#357 phase 3).
 *
 * Differences from [OidcPrincipalAdapter] that justify the dedicated class:
 *
 *   - **Subject** lives at `attributes["id"]` (a numeric account id) and is
 *     stable across email/handle changes. The OIDC `sub` claim does not exist.
 *   - **No ID token.** GitHub OAuth2 returns an opaque access token, no JWT
 *     and no JWKS. `upstreamIdToken` is therefore always `null`; downstream
 *     RP-Initiated Logout (#352) falls back gracefully.
 *   - **Email** may be missing from the `/user` response when the GitHub
 *     user has set their primary email to private. The adapter recovers via
 *     [GitHubEmailFetcher.fetchPrimaryVerified] using the access token, which
 *     requires the `user:email` scope (configured in
 *     [DbClientRegistrationRepository] for this provider type).
 *   - **Display name** falls back to `login` (the @handle) when `name` is
 *     blank — many GitHub users leave their full name unset.
 *
 * If the email cannot be resolved by either path (`/user` returned no email
 * AND `/user/emails` returned no primary verified row), the adapter returns
 * `email = null` and the downstream `OidcIdentityService` raises the
 * provider-aware `OidcEmailMissingException` from #357 phase 2.
 */
@Component
class GitHubPrincipalAdapter(
    private val authorizedClientService: OAuth2AuthorizedClientService,
    private val emailFetcher: GitHubEmailFetcher,
) : ProviderPrincipalAdapter {

    private val log = LoggerFactory.getLogger(GitHubPrincipalAdapter::class.java)

    override val providerTypes: Set<OidcProviderType> = setOf(OidcProviderType.GITHUB)

    override fun resolve(authentication: OAuth2AuthenticationToken): ResolvedPrincipal {
        val registrationId = authentication.authorizedClientRegistrationId
        val principal = requireNotNull(authentication.principal) {
            "OAuth2AuthenticationToken for $registrationId has no principal"
        }

        // GitHub's user id is numeric; we keep it as a string for storage in
        // `oidc_identity.subject` — that column is text-typed, and stringifying
        // here keeps the per-provider schema consistent.
        val rawId = principal.attributes["id"]
            ?: error(
                "GitHub provider $registrationId returned a user response without an `id` attribute — " +
                    "cannot map to a Plugwerk user.",
            )
        val subject = rawId.toString()

        val displayName = (principal.attributes["name"] as? String)?.trim()?.takeIf { it.isNotBlank() }
            ?: (principal.attributes["login"] as? String)?.trim()?.takeIf { it.isNotBlank() }

        // First try the public email from the /user response — fastest path,
        // no extra HTTP call. If absent, fall back to /user/emails which
        // requires the `user:email` scope and surfaces private primary emails.
        val publicEmail = (principal.attributes["email"] as? String)?.trim()?.takeIf { it.isNotBlank() }
        val email = publicEmail ?: fetchPrimaryVerifiedEmail(authentication, subject)

        return ResolvedPrincipal(
            subject = subject,
            email = email,
            displayName = displayName,
            upstreamIdToken = null,
        )
    }

    private fun fetchPrimaryVerifiedEmail(authentication: OAuth2AuthenticationToken, subject: String): String? {
        val authorizedClient = authorizedClientService.loadAuthorizedClient<OAuth2AuthorizedClient>(
            authentication.authorizedClientRegistrationId,
            authentication.name,
        )
        val accessToken = authorizedClient?.accessToken?.tokenValue
        if (accessToken == null) {
            log.warn(
                "GitHub callback for sub={} produced no access token — cannot fetch /user/emails",
                subject,
            )
            return null
        }
        return emailFetcher.fetchPrimaryVerified(accessToken)
    }
}
