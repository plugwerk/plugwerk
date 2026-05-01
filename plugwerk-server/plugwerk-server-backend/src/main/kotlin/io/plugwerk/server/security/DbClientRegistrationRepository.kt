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
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider
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
 * OIDC discovery via `issuerUri`), [OidcProviderType.GOOGLE] (same
 * machinery, hardcoded issuer URI — operators only configure clientId +
 * clientSecret, see #357 Phase 1), and [OidcProviderType.GITHUB] (pure
 * OAuth2 via Spring's `CommonOAuth2Provider.GITHUB` template, with the
 * `read:user` and `user:email` scopes hardcoded for this provider type
 * because the operator-supplied scope string from the admin UI defaults to
 * the OIDC vocabulary which GitHub does not understand, see #357 Phase 3).
 * The remaining vendor provider [OidcProviderType.FACEBOOK] has sufficient
 * metadata in [OidcProviderRegistry] to validate incoming bearer tokens
 * but the browser-flow wiring is tracked in #357 Phase 4. It is silently
 * skipped here and emits a single warning at refresh time so operators
 * know the row will not appear on the login page.
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

            // GitHub is pure OAuth2 (no OpenID Connect) — there is no
            // /.well-known/openid-configuration and no ID token. Spring ships a
            // pre-baked client-registration template at CommonOAuth2Provider.GITHUB
            // that knows the right authorization / token / user-info endpoints.
            // We use that as the starting point and let the post-when-block
            // .clientId / .clientSecret / .scope chain finish it. Issue #357 Phase 3.
            OidcProviderType.GITHUB -> {
                CommonOAuth2Provider.GITHUB.getBuilder(registrationId)
            }

            OidcProviderType.FACEBOOK -> error(
                "Browser login flow not yet implemented for provider type ${provider.providerType} " +
                    "(see #357 phase 4). The provider remains usable as a resource-server token issuer.",
            )

            // Stub. Phase A of the OAUTH2_GENERIC rollout adds the schema (entity
            // fields + migration 0022) but not yet the registration build path.
            // The actual ClientRegistration construction from operator-supplied
            // URIs lands in Phase B.
            OidcProviderType.OAUTH2_GENERIC -> error(
                "Browser login flow for OAUTH2_GENERIC is not yet wired — schema exists, " +
                    "registration construction lands in the next PR.",
            )
        }
        return builder
            .registrationId(registrationId)
            .clientId(provider.clientId)
            .clientSecret(textEncryptor.decrypt(provider.clientSecretEncrypted))
            .scope(effectiveScopes(provider))
            .build()
    }

    /**
     * Resolves the effective OAuth2 scope set for a provider.
     *
     * For OIDC and Google the operator's `provider.scope` value is honoured
     * verbatim — both providers share the OIDC vocabulary the admin form
     * defaults to (`openid profile email`).
     *
     * For GitHub the OIDC default is meaningless (GitHub ignores `openid` and
     * `profile`), so we ALWAYS hardcode `read:user user:email`. `read:user`
     * is what powers the `/user` response Spring's user-info call relies on;
     * `user:email` is what makes `/user/emails` fetchable for the
     * private-primary-email recovery path in [GitHubPrincipalAdapter]. If a
     * future operator needs custom GitHub scopes (e.g. `repo` for an app),
     * the override path is to remove this hardcoded branch and accept that
     * the admin UI scope hint must then be GitHub-specific.
     */
    private fun effectiveScopes(provider: OidcProviderEntity): Set<String> = when (provider.providerType) {
        OidcProviderType.GITHUB -> setOf("read:user", "user:email")
        else -> provider.scope.split(" ").filter { it.isNotBlank() }.toSet()
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
