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
 * Two further properties — `plugwerk.auth.oidc.blocked-host-names` and
 * `plugwerk.auth.oidc.blocked-host-suffixes` (both comma-separated, env
 * `PLUGWERK_AUTH_OIDC_BLOCKED_HOST_NAMES` /
 * `PLUGWERK_AUTH_OIDC_BLOCKED_HOST_SUFFIXES`) — replace the hardcoded
 * hostname blocklists in [HostClassifier] when an operator needs a
 * different policy (e.g. legitimate `*.internal` public domain or an
 * additional `*.corp.example.com`). IP-range blocks (RFC 1918, loopback,
 * link-local, ULA) remain hardcoded — those are Internet standards and
 * the only switch is [allowPrivateDiscoveryUris].
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
    @param:Value("\${plugwerk.auth.oidc.blocked-host-names:}")
    private val blockedHostNamesOverride: List<String> = emptyList(),
    @param:Value("\${plugwerk.auth.oidc.blocked-host-suffixes:}")
    private val blockedHostSuffixesOverride: List<String> = emptyList(),
) {

    private val log = LoggerFactory.getLogger(OidcSsrfPolicy::class.java)

    /**
     * Empty configured list → use the [HostClassifier] defaults. A
     * deliberate `BLOCKED_HOST_NAMES=` (empty) override therefore still
     * yields the defaults; to actually disable a hostname class the
     * operator passes a non-empty replacement set. This avoids the
     * "accidentally cleared all hostname blocks" footgun while keeping
     * the override simple to express.
     */
    private val hostClassifier: HostClassifier = HostClassifier(
        blockedNames = blockedHostNamesOverride.takeIf { it.isNotEmpty() }
            ?: HostClassifier.DEFAULT_BLOCKED_NAMES,
        blockedSuffixes = blockedHostSuffixesOverride.takeIf { it.isNotEmpty() }
            ?: HostClassifier.DEFAULT_BLOCKED_SUFFIXES,
    )

    @PostConstruct
    fun warnIfRelaxed() {
        if (allowPrivateDiscoveryUris) {
            log.warn(
                "plugwerk.auth.oidc.allow-private-discovery-uris=true — OIDC SSRF guard is " +
                    "RELAXED. This is intended for local development against Keycloak or " +
                    "mock-oauth2-server only. NEVER set this in production.",
            )
        }
        if (blockedHostNamesOverride.isNotEmpty()) {
            log.info(
                "OIDC SSRF guard: blocked-host-names overridden ({} entries)",
                blockedHostNamesOverride.size,
            )
        }
        if (blockedHostSuffixesOverride.isNotEmpty()) {
            log.info(
                "OIDC SSRF guard: blocked-host-suffixes overridden ({} entries)",
                blockedHostSuffixesOverride.size,
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
            UriGuards.requirePublicHttpUri(value, fieldName, required, hostClassifier)
        }
    }
}
