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
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Adapter for [OidcProviderType.OAUTH2_GENERIC] — the operator-configurable
 * OAuth2 provider type that does not have a Plugwerk vendor branch (#357 +
 * the post-#357 generic-provider expansion).
 *
 * Where the other adapters know exactly which keys to read from the
 * principal's attributes (`sub` for OIDC, `id` for GitHub), this adapter
 * reads the *names* of those keys from the [OidcProviderEntity] row itself:
 *
 *   - `subjectAttribute` (defaults to `sub`)
 *   - `emailAttribute` (defaults to `email`)
 *   - `displayNameAttribute` (defaults to `name`)
 *
 * That reflects the contract: an OAUTH2_GENERIC provider is whatever the
 * operator paste-configured. They know the JSON shape of the user-info
 * response — we don't.
 *
 * ## No upstream ID token
 *
 * OAuth2 (without OIDC) has no concept of an ID token. RP-Initiated Logout
 * (#352) does not apply, so [ResolvedPrincipal.upstreamIdToken] is always
 * `null` from this adapter — same posture as [GitHubPrincipalAdapter].
 *
 * ## Email may be missing
 *
 * Many generic OAuth2 providers gate email behind an explicit scope (the
 * operator must include it in `provider.scope`) or omit it entirely. The
 * provider-aware [io.plugwerk.server.service.OidcEmailMissingException]
 * message — produced by [io.plugwerk.server.service.OidcIdentityService]
 * when this adapter returns `email = null` — points the operator at the
 * configured `emailAttribute` name so they can compare against what the
 * user-info endpoint actually returns.
 */
@Component
class GenericOAuth2PrincipalAdapter(private val oidcProviderRepository: OidcProviderRepository) :
    ProviderPrincipalAdapter {

    override val providerTypes: Set<OidcProviderType> = setOf(OidcProviderType.OAUTH2_GENERIC)

    override fun resolve(authentication: OAuth2AuthenticationToken): ResolvedPrincipal {
        val registrationId = authentication.authorizedClientRegistrationId
        val providerId = runCatching { UUID.fromString(registrationId) }.getOrNull()
            ?: error(
                "OAUTH2_GENERIC registrationId $registrationId is not a valid UUID — " +
                    "DbClientRegistrationRepository wiring is broken.",
            )
        val provider = oidcProviderRepository.findById(providerId).orElseThrow {
            IllegalStateException(
                "OAUTH2_GENERIC provider $providerId no longer exists — race with provider deletion.",
            )
        }

        val principal = requireNotNull(authentication.principal) {
            "OAuth2AuthenticationToken for $registrationId has no principal"
        }
        val attributes = principal.attributes

        // Subject is the only field that must be present — without a stable
        // identifier we cannot create or look up the oidc_identity row.
        val subjectKey = provider.subjectAttribute ?: DEFAULT_SUBJECT_ATTRIBUTE
        val subject = attributes[subjectKey]?.toString()?.trim()?.takeIf { it.isNotBlank() }
            ?: error(
                "OAUTH2_GENERIC provider '${provider.name}' returned a user-info response without " +
                    "the configured subject attribute `$subjectKey` — adjust the subject-attribute " +
                    "name on the provider configuration to match what the user-info endpoint returns.",
            )

        val emailKey = provider.emailAttribute ?: DEFAULT_EMAIL_ATTRIBUTE
        val email = (attributes[emailKey] as? String)?.trim()?.takeIf { it.isNotBlank() }

        val displayNameKey = provider.displayNameAttribute ?: DEFAULT_DISPLAY_NAME_ATTRIBUTE
        val displayName = (attributes[displayNameKey] as? String)?.trim()?.takeIf { it.isNotBlank() }

        return ResolvedPrincipal(
            subject = subject,
            email = email,
            displayName = displayName,
            upstreamIdToken = null,
        )
    }

    companion object {
        const val DEFAULT_SUBJECT_ATTRIBUTE = "sub"
        const val DEFAULT_EMAIL_ATTRIBUTE = "email"
        const val DEFAULT_DISPLAY_NAME_ATTRIBUTE = "name"
    }
}
