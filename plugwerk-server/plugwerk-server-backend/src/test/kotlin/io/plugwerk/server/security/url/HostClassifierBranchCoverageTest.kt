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

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class HostClassifierBranchCoverageTest {

    private val classifier = HostClassifier()

    // -- bracketed IPv6 unwrap branch ----------------------------------------

    @ParameterizedTest
    @ValueSource(
        strings = [
            // Bracketed forms exercise the `host.startsWith("[") && endsWith("]")`
            // unwrap arm, which the unbracketed cases in the base test skip.
            "[::1]",
            "[fe80::1]",
            "[fc00::1]",
            "[::ffff:127.0.0.1]",
        ],
    )
    fun `rejects bracketed private and loopback IPv6 literals`(host: String) {
        assertThat(classifier.isPublicRoutable(host))
            .`as`("host=%s should be non-public", host)
            .isFalse()
    }

    @Test
    fun `accepts a bracketed public IPv6 literal`() {
        // Bracketed unwrap + public classification arm.
        assertThat(classifier.isPublicRoutable("[2001:4860:4860::8888]")).isTrue()
    }

    // -- malformed-literal → falls through to "DNS name, public" -------------

    @Test
    fun `treats a dotted-quad out of range as a non-IP and classifies it public`() {
        // Matches IPV4_LITERAL_REGEX but InetAddress.getByName fails → runCatching
        // getOrNull returns null → parseIpLiteral returns null → public.
        assertThat(classifier.isPublicRoutable("999.999.999.999")).isTrue()
    }

    @Test
    fun `treats a malformed IPv6 literal as a non-IP and classifies it public`() {
        // Contains ':' so the IPv6 arm is taken, but it does not parse → null → public.
        assertThat(classifier.isPublicRoutable("::ffff::bogus::")).isTrue()
    }

    @Test
    fun `classifies a label-only token without dots or colons as public`() {
        // No IPv4 regex match and no ':' → parseIpLiteral returns null → public.
        assertThat(classifier.isPublicRoutable("intranetbox")).isTrue()
    }

    // -- isAnyLocalAddress (unspecified) and explicit 169.254 IPv4 arm -------

    @Test
    fun `rejects the IPv4 unspecified address`() {
        // 0.0.0.0 → isAnyLocalAddress branch.
        assertThat(classifier.isPublicRoutable("0.0.0.0")).isFalse()
    }

    @Test
    fun `rejects an explicit 169_254 link-local IPv4 address`() {
        // Covers the explicit first==169 && second==254 Inet4Address arm.
        assertThat(classifier.isPublicRoutable("169.254.42.42")).isFalse()
    }

    // -- whitespace trimming on the input host -------------------------------

    @Test
    fun `trims surrounding whitespace before classifying`() {
        // host.trim() then non-empty → still resolves to loopback.
        assertThat(classifier.isPublicRoutable("  127.0.0.1  ")).isFalse()
        assertThat(classifier.isPublicRoutable("  github.com  ")).isTrue()
    }

    // -- blockedSuffixes false arm with a public host ------------------------

    @Test
    fun `accepts a host that matches no blocked name or suffix`() {
        // normalized !in blockedNames AND no suffix endsWith → IP-literal parse →
        // null → public. Exercises both negative arms of the name/suffix gates.
        assertThat(classifier.isPublicRoutable("login.example.org")).isTrue()
    }

    // -- IPv4-mapped public address re-classification ------------------------

    @Test
    fun `accepts an IPv4-mapped IPv6 wrapping a public IPv4`() {
        // ::ffff:8.8.8.8 → isIpv4Mapped → re-classify embedded 8.8.8.8 → public.
        assertThat(classifier.isPublicRoutable("::ffff:8.8.8.8")).isTrue()
    }
}
