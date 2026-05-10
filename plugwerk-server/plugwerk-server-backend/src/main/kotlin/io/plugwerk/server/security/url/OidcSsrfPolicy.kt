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
package io.plugwerk.server.security.url

import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * Single source of truth for whether the OIDC SSRF guard is enforced (#479).
 * Production runs with the default `false` for [allowPrivateDiscoveryUris]
 * → [requirePublicHttpUri] applies the full [UriGuards.requirePublicHttpUri]
 * gate (rejects private/loopback/link-local/metadata).
 *
 * Dev/test profiles can set
 * `plugwerk.auth.oidc.allow-private-discovery-uris=true`
 * (env: `PLUGWERK_AUTH_OIDC_ALLOW_PRIVATE_DISCOVERY_URIS`) to allow
 * Keycloak / mock-oauth2-server on localhost. A startup WARN is logged
 * whenever the escape hatch is enabled so an operator who flipped it
 * accidentally in production sees it in the log.
 *
 * Injected from `OidcProviderService`, `OidcProviderRegistry`, and
 * `DbClientRegistrationRepository` — all three reach Spring's static
 * `ClientRegistrations.fromIssuerLocation` / `JwtDecoders.fromIssuerLocation`
 * and must apply the same policy.
 */
@Component
class OidcSsrfPolicy(
    @param:Value("\${plugwerk.auth.oidc.allow-private-discovery-uris:false}")
    val allowPrivateDiscoveryUris: Boolean = false,
) {

    private val log = LoggerFactory.getLogger(OidcSsrfPolicy::class.java)

    @PostConstruct
    fun warnIfRelaxed() {
        if (allowPrivateDiscoveryUris) {
            log.warn(
                "plugwerk.auth.oidc.allow-private-discovery-uris=true — OIDC SSRF guard is " +
                    "RELAXED. This is intended for local development against Keycloak or " +
                    "mock-oauth2-server only. NEVER set this in production.",
            )
        }
    }

    /**
     * Same contract as [UriGuards.requirePublicHttpUri], but routes through
     * [UriGuards.requireHttpUri] (no host-class check) when the escape hatch
     * is on. Syntax + scheme validation always applies.
     */
    fun requirePublicHttpUri(value: String?, fieldName: String, required: Boolean) {
        if (allowPrivateDiscoveryUris) {
            UriGuards.requireHttpUri(value, fieldName, required)
        } else {
            UriGuards.requirePublicHttpUri(value, fieldName, required)
        }
    }
}
