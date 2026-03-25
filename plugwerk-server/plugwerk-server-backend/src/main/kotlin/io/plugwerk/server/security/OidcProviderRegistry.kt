/*
 * Plugwerk — Plugin Marketplace for the PF4J Ecosystem
 * Copyright (C) 2026 devtank42 GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package io.plugwerk.server.security

import io.plugwerk.server.domain.OidcProviderType
import io.plugwerk.server.repository.OidcProviderRepository
import org.slf4j.LoggerFactory
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtDecoders
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicReference

/**
 * Maintains a live registry of [JwtDecoder] instances for all enabled OIDC providers.
 *
 * The registry is loaded at application startup and can be reloaded at runtime (without
 * server restart) whenever an administrator enables or disables a provider via the admin UI.
 * Calls to [refresh] rebuild the decoder list from the database.
 *
 * **Well-known JWKS URIs** for pre-configured providers:
 * - GitHub: `https://token.actions.githubusercontent.com/.well-known/jwks` (Actions tokens)
 *   and `https://api.github.com/meta/public_keys/oidc` (user tokens)
 * - Google: discovered via `https://accounts.google.com` issuer URI
 * - Facebook: discovered via `https://www.facebook.com` issuer URI
 *
 * **Thread safety:** The active decoder list is held in an [AtomicReference] so that
 * [refresh] and [decoders] can be called concurrently without locking.
 */
@Component
class OidcProviderRegistry(private val oidcProviderRepository: OidcProviderRepository) {

    private val log = LoggerFactory.getLogger(OidcProviderRegistry::class.java)

    private val activeDecoders = AtomicReference<List<JwtDecoder>>(emptyList())

    init {
        refresh()
    }

    /** Returns the current list of active OIDC [JwtDecoder] instances. */
    fun decoders(): List<JwtDecoder> = activeDecoders.get()

    /**
     * Reloads the decoder list from the database.
     *
     * Called at startup and whenever an administrator changes a provider's [enabled] state.
     * Providers that fail to initialise (e.g. unreachable JWKS URI) are logged and skipped
     * so that one misconfigured provider cannot break authentication for all other providers.
     */
    fun refresh() {
        val providers = oidcProviderRepository.findAllByEnabledTrue()
        val decoders = providers.mapNotNull { provider ->
            runCatching {
                when (provider.providerType) {
                    OidcProviderType.GENERIC_OIDC, OidcProviderType.KEYCLOAK -> {
                        val issuerUri = requireNotNull(provider.issuerUri) {
                            "issuerUri is required for provider type ${provider.providerType}"
                        }
                        JwtDecoders.fromIssuerLocation(issuerUri)
                    }

                    OidcProviderType.GOOGLE ->
                        JwtDecoders.fromIssuerLocation("https://accounts.google.com")

                    OidcProviderType.GITHUB ->
                        NimbusJwtDecoder.withJwkSetUri(
                            "https://token.actions.githubusercontent.com/.well-known/jwks",
                        ).build()

                    OidcProviderType.FACEBOOK ->
                        NimbusJwtDecoder.withJwkSetUri(
                            "https://www.facebook.com/.well-known/oauth/openid/jwks/",
                        ).build()
                }
            }.onFailure { ex ->
                log.warn(
                    "Failed to initialise OIDC decoder for provider '${provider.name}' (${provider.providerType}): ${ex.message}",
                )
            }.getOrNull()
        }
        activeDecoders.set(decoders)
        log.info("OIDC provider registry refreshed: {} active decoder(s)", decoders.size)
    }
}
