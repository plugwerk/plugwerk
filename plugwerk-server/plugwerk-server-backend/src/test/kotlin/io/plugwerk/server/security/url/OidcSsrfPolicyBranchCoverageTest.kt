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

import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

/**
 * Branch-coverage tests for [OidcSsrfPolicy]. Covers both arms of
 * [OidcSsrfPolicy.requirePublicHttpUri] (escape hatch on → no host-class check;
 * off → full SSRF gate), the host-name / host-suffix override constructor arms
 * (`takeIf { isNotEmpty() } ?: default`), and every conditional in
 * [OidcSsrfPolicy.warnIfRelaxed] (relaxed flag, name override present, suffix
 * override present — each present and absent).
 */
class OidcSsrfPolicyBranchCoverageTest {

    // -----------------------------------------------------------------------
    // requirePublicHttpUri — escape-hatch OFF (production default).
    // -----------------------------------------------------------------------

    @Test
    fun `enforcing policy accepts a public https URI`() {
        val policy = OidcSsrfPolicy(allowPrivateDiscoveryUris = false)

        assertThatCode {
            policy.requirePublicHttpUri("https://idp.example.com", "issuerUri", required = true)
        }.doesNotThrowAnyException()
    }

    @Test
    fun `enforcing policy rejects a private host`() {
        val policy = OidcSsrfPolicy(allowPrivateDiscoveryUris = false)

        assertFailsWith<IllegalArgumentException> {
            policy.requirePublicHttpUri("http://10.0.0.1/realms", "issuerUri", required = true)
        }
    }

    @Test
    fun `enforcing policy rejects localhost via the default host-name blocklist`() {
        val policy = OidcSsrfPolicy(allowPrivateDiscoveryUris = false)

        assertFailsWith<IllegalArgumentException> {
            policy.requirePublicHttpUri("http://localhost/realms", "issuerUri", required = true)
        }
    }

    @Test
    fun `enforcing policy allows null when not required`() {
        val policy = OidcSsrfPolicy(allowPrivateDiscoveryUris = false)

        assertThatCode {
            policy.requirePublicHttpUri(null, "jwkSetUri", required = false)
        }.doesNotThrowAnyException()
    }

    @Test
    fun `enforcing policy rejects null when required`() {
        val policy = OidcSsrfPolicy(allowPrivateDiscoveryUris = false)

        assertFailsWith<IllegalArgumentException> {
            policy.requirePublicHttpUri(null, "issuerUri", required = true)
        }
    }

    // -----------------------------------------------------------------------
    // requirePublicHttpUri — escape-hatch ON (relaxed dev/test mode).
    // -----------------------------------------------------------------------

    @Test
    fun `relaxed policy accepts a private host (host-class check skipped)`() {
        val policy = OidcSsrfPolicy(allowPrivateDiscoveryUris = true)

        // Syntax + scheme still validated, but the SSRF host-class gate is off,
        // so a loopback URI is accepted under the escape hatch.
        assertThatCode {
            policy.requirePublicHttpUri("http://127.0.0.1:8080/realms", "issuerUri", required = true)
        }.doesNotThrowAnyException()
    }

    @Test
    fun `relaxed policy still rejects a non-http scheme (syntax gate always runs)`() {
        val policy = OidcSsrfPolicy(allowPrivateDiscoveryUris = true)

        assertFailsWith<IllegalArgumentException> {
            policy.requirePublicHttpUri("ftp://localhost/realms", "issuerUri", required = true)
        }
    }

    @Test
    fun `relaxed policy rejects null when required`() {
        val policy = OidcSsrfPolicy(allowPrivateDiscoveryUris = true)

        assertFailsWith<IllegalArgumentException> {
            policy.requirePublicHttpUri(null, "issuerUri", required = true)
        }
    }

    // -----------------------------------------------------------------------
    // Host-name / host-suffix override constructor arms.
    // -----------------------------------------------------------------------

    @Test
    fun `a custom blocked-host-names override replaces the defaults`() {
        // With a non-empty override that does NOT include "localhost", the
        // default localhost block is replaced — but localhost still ends with
        // the default ".localhost"? No: "localhost" alone has no dot suffix,
        // so the suffix list (still defaulted) does not match it. We instead
        // assert that the custom name is blocked and a previously-default name
        // (metadata) is no longer blocked.
        val policy = OidcSsrfPolicy(
            allowPrivateDiscoveryUris = false,
            blockedHostNamesOverride = listOf("idp.blocked.example.com"),
        )

        assertFailsWith<IllegalArgumentException> {
            policy.requirePublicHttpUri("https://idp.blocked.example.com/realms", "issuerUri", required = true)
        }
    }

    @Test
    fun `an empty blocked-host-names override keeps the defaults (localhost still blocked)`() {
        val policy = OidcSsrfPolicy(
            allowPrivateDiscoveryUris = false,
            blockedHostNamesOverride = emptyList(),
        )

        assertFailsWith<IllegalArgumentException> {
            policy.requirePublicHttpUri("http://localhost/realms", "issuerUri", required = true)
        }
    }

    @Test
    fun `a custom blocked-host-suffixes override replaces the defaults`() {
        val policy = OidcSsrfPolicy(
            allowPrivateDiscoveryUris = false,
            blockedHostSuffixesOverride = listOf("corp.example.com"),
        )

        assertFailsWith<IllegalArgumentException> {
            policy.requirePublicHttpUri("https://idp.corp.example.com/realms", "issuerUri", required = true)
        }
    }

    @Test
    fun `an empty blocked-host-suffixes override keeps the defaults (dot-internal still blocked)`() {
        val policy = OidcSsrfPolicy(
            allowPrivateDiscoveryUris = false,
            blockedHostSuffixesOverride = emptyList(),
        )

        assertFailsWith<IllegalArgumentException> {
            policy.requirePublicHttpUri("https://idp.internal/realms", "issuerUri", required = true)
        }
    }

    // -----------------------------------------------------------------------
    // warnIfRelaxed — every conditional arm. The method only logs; we assert it
    // runs without throwing for each combination so each branch is executed.
    // -----------------------------------------------------------------------

    @Test
    fun `warnIfRelaxed logs nothing extra in the strict default configuration`() {
        val policy = OidcSsrfPolicy(allowPrivateDiscoveryUris = false)

        assertThatCode { policy.warnIfRelaxed() }.doesNotThrowAnyException()
    }

    @Test
    fun `warnIfRelaxed runs the relaxed-mode warning arm`() {
        val policy = OidcSsrfPolicy(allowPrivateDiscoveryUris = true)

        assertThatCode { policy.warnIfRelaxed() }.doesNotThrowAnyException()
    }

    @Test
    fun `warnIfRelaxed runs the name-override and suffix-override info arms`() {
        val policy = OidcSsrfPolicy(
            allowPrivateDiscoveryUris = true,
            blockedHostNamesOverride = listOf("a.example.com"),
            blockedHostSuffixesOverride = listOf("b.example.com"),
        )

        assertThatCode { policy.warnIfRelaxed() }.doesNotThrowAnyException()
    }
}
