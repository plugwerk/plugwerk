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

import io.plugwerk.server.domain.OidcProviderEntity
import io.plugwerk.server.domain.OidcProviderType
import io.plugwerk.server.repository.OidcProviderRepository
import org.slf4j.LoggerFactory
import org.springframework.security.crypto.encrypt.TextEncryptor
import org.springframework.security.oauth2.client.registration.ClientRegistration
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.security.oauth2.client.registration.ClientRegistrations
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicReference

/**
 * Spring Security `ClientRegistrationRepository` backed by the
 * `oidc_provider` table (issue #79).
 *
 * Builds one [ClientRegistration] per `enabled = true` row and exposes them
 * to Spring's OAuth2 client filter, which uses them to drive the browser
 * Authorization Code Flow at
 * `/oauth2/authorization/{registrationId}` and the redirect URI
 * `/login/oauth2/code/{registrationId}`.
 *
 * The `registrationId` is sourced from [OidcRegistrationIds.of] — the same
 * value the frontend gets back from `/api/v1/config` and uses to navigate.
 *
 * ## Refresh semantics
 *
 * Built once at startup and rebuilt on demand via [refresh]. The admin UI
 * paths that flip `enabled` (or change a provider's secret/issuer) call this
 * method through [io.plugwerk.server.service.OidcProviderService] so a newly
 * activated provider becomes available without a server restart.
 *
 * The internal map sits in an [AtomicReference] so [findByRegistrationId]
 * never blocks while a refresh is in flight; readers either see the old map
 * or the new map, never a half-built one.
 *
 * ## Provider type support
 *
 * Browser login is wired for [OidcProviderType.OIDC] (relies on RFC-8414 /
 * OIDC discovery via `issuerUri`) and for [OidcProviderType.GOOGLE] (same
 * machinery, hardcoded issuer URI — operators only configure clientId +
 * clientSecret, see #357 Phase 1). The remaining vendor providers
 * ([OidcProviderType.GITHUB], [OidcProviderType.FACEBOOK]) have sufficient
 * metadata in [OidcProviderRegistry] to validate incoming bearer tokens,
 * but the browser-flow client metadata (authorization endpoint with the
 * right vendor quirks, OAuth2-not-OIDC principal handling) is tracked in
 * #357 Phase 3 / 4. They are silently skipped here and emit a single
 * warning at refresh time so operators know the row will not appear on the
 * login page.
 *
 * ## Failure isolation
 *
 * One unreachable issuer must not break authentication for every other
 * provider, so each registration build is wrapped in `runCatching` and a
 * failed entry is logged + skipped. The same defence-in-depth pattern is
 * used by [OidcProviderRegistry] for the resource-server side.
 */
@Component
class DbClientRegistrationRepository(
    private val oidcProviderRepository: OidcProviderRepository,
    private val textEncryptor: TextEncryptor,
) : ClientRegistrationRepository,
    Iterable<ClientRegistration> {

    private val log = LoggerFactory.getLogger(DbClientRegistrationRepository::class.java)

    private val activeRegistrations = AtomicReference<Map<String, ClientRegistration>>(emptyMap())

    init {
        refresh()
    }

    override fun findByRegistrationId(registrationId: String): ClientRegistration? =
        activeRegistrations.get()[registrationId]

    override fun iterator(): Iterator<ClientRegistration> = activeRegistrations.get().values.iterator()

    /**
     * Reload the registration map from the database. Called at startup and
     * by [io.plugwerk.server.service.OidcProviderService] after any change
     * that affects which providers participate in the browser login flow.
     */
    fun refresh() {
        val byId = oidcProviderRepository.findAllByEnabledTrue()
            .mapNotNull { provider ->
                runCatching { buildRegistration(provider) }
                    .onFailure {
                        log.warn(
                            "Skipping OIDC provider {} (registrationId={}) — failed to build ClientRegistration: {}",
                            provider.name,
                            OidcRegistrationIds.of(provider),
                            it.message,
                        )
                    }
                    .getOrNull()
            }
            .associateBy { it.registrationId }
        activeRegistrations.set(byId)
        log.info("OAuth2 client registrations loaded: {} active provider(s)", byId.size)
    }

    private fun buildRegistration(provider: OidcProviderEntity): ClientRegistration {
        val registrationId = OidcRegistrationIds.of(provider)
        val builder = when (provider.providerType) {
            OidcProviderType.OIDC -> {
                val issuerUri = requireNotNull(provider.issuerUri) {
                    "issuerUri is required for provider type ${provider.providerType}"
                }
                ClientRegistrations.fromIssuerLocation(issuerUri)
            }

            // Google is OIDC-conformant — same `sub`, `email`, `name` claims as a
            // generic OIDC provider. The only thing the operator should NOT have to
            // configure is the issuer URI, which is fixed for Google's identity
            // platform. We hardcode it and ignore any value the operator put in
            // `provider.issuerUri` (no error: the field is documented as ignored
            // for vendor-typed providers, see OidcProviderEntity Javadoc).
            // Issue #357 Phase 1.
            OidcProviderType.GOOGLE -> {
                ClientRegistrations.fromIssuerLocation(GOOGLE_ISSUER_URI)
            }

            OidcProviderType.GITHUB,
            OidcProviderType.FACEBOOK,
            -> error(
                "Browser login flow not yet implemented for provider type ${provider.providerType} " +
                    "(see #357). The provider remains usable as a resource-server token issuer.",
            )
        }
        return builder
            .registrationId(registrationId)
            .clientId(provider.clientId)
            .clientSecret(textEncryptor.decrypt(provider.clientSecretEncrypted))
            .scope(provider.scope.split(" ").filter { it.isNotBlank() }.toSet())
            .build()
    }

    companion object {
        /**
         * Google's OpenID Connect issuer URI. Hardcoded because (a) it is fixed by
         * Google and (b) hardcoding spares operators from having to know it. The
         * `/.well-known/openid-configuration` lives at `${this}/.well-known/...`.
         */
        const val GOOGLE_ISSUER_URI = "https://accounts.google.com"
    }
}
