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
import kotlin.test.assertFailsWith

/**
 * Branch-coverage tests for the harder-to-reach arms of [HostClassifier]:
 * constructor normalisation (blank-stripping and the leading-`.` suffix
 * fix-up, both arms), bracketed IPv6 literal unwrapping, the IPv6 special
 * forms (ULA fc00::/7, IPv4-mapped and IPv4-compatible re-classification),
 * and the `requirePublicHost` throw/no-throw arms.
 */
class HostClassifierExtraBranchCoverageTest {

    private val classifier = HostClassifier()

    // -- constructor normalisation ------------------------------------------

    @Test
    fun `blank configured names and suffixes are filtered out`() {
        // Blank entries hit the `.filter { it.isNotEmpty() }` false arm and
        // must not block everything; a real public host still passes.
        val c = HostClassifier(blockedNames = listOf("   ", ""), blockedSuffixes = listOf("  ", ""))

        assertThat(c.isPublicRoutable("example.com")).isTrue()
    }

    @Test
    fun `a suffix without a leading dot is normalised and matches a full label`() {
        // "corp.example.com" -> ".corp.example.com": the else arm of the
        // startsWith(".") check.
        val c = HostClassifier(blockedNames = emptyList(), blockedSuffixes = listOf("corp.example.com"))

        assertThat(c.isPublicRoutable("idp.corp.example.com")).isFalse()
        // Substring-but-not-label-boundary must NOT be blocked.
        assertThat(c.isPublicRoutable("evilcorp.example.com")).isTrue()
    }

    @Test
    fun `a suffix already starting with a dot is kept verbatim`() {
        // The true arm of startsWith(".").
        val c = HostClassifier(blockedNames = emptyList(), blockedSuffixes = listOf(".vpn"))

        assertThat(c.isPublicRoutable("host.vpn")).isFalse()
    }

    @Test
    fun `name matching is case-insensitive`() {
        val c = HostClassifier(blockedNames = listOf("Blocked.Example.COM"))

        assertThat(c.isPublicRoutable("blocked.example.com")).isFalse()
    }

    // -- isPublicRoutable basic arms ----------------------------------------

    @Test
    fun `blank host is not routable`() {
        assertThat(classifier.isPublicRoutable("   ")).isFalse()
    }

    @Test
    fun `a plain DNS hostname is treated as routable (no IP literal)`() {
        assertThat(classifier.isPublicRoutable("idp.example.com")).isTrue()
    }

    @Test
    fun `a public IPv4 literal is routable`() {
        assertThat(classifier.isPublicRoutable("8.8.8.8")).isTrue()
    }

    // -- IPv4 private / special ---------------------------------------------

    @Test
    fun `RFC1918 and loopback and any-local IPv4 are blocked`() {
        assertThat(classifier.isPublicRoutable("10.0.0.1")).isFalse()
        assertThat(classifier.isPublicRoutable("192.168.1.1")).isFalse()
        assertThat(classifier.isPublicRoutable("172.16.5.4")).isFalse()
        assertThat(classifier.isPublicRoutable("127.0.0.1")).isFalse()
        assertThat(classifier.isPublicRoutable("0.0.0.0")).isFalse()
        assertThat(classifier.isPublicRoutable("169.254.169.254")).isFalse()
    }

    // -- IPv6 literal arms ---------------------------------------------------

    @Test
    fun `a bracketed IPv6 loopback literal is unwrapped and blocked`() {
        assertThat(classifier.isPublicRoutable("[::1]")).isFalse()
    }

    @Test
    fun `a unique-local IPv6 address (fc00 7) is blocked`() {
        assertThat(classifier.isPublicRoutable("fd12:3456:789a::1")).isFalse()
    }

    @Test
    fun `an IPv4-mapped IPv6 address is re-classified by its embedded IPv4`() {
        // ::ffff:10.0.0.1 → embedded 10.0.0.1 is RFC1918 → blocked.
        assertThat(classifier.isPublicRoutable("::ffff:10.0.0.1")).isFalse()
    }

    @Test
    fun `a public IPv6 literal is routable`() {
        assertThat(classifier.isPublicRoutable("2001:4860:4860::8888")).isTrue()
    }

    // -- requirePublicHost arms ---------------------------------------------

    @Test
    fun `requirePublicHost passes for a public host`() {
        // No exception thrown.
        classifier.requirePublicHost("8.8.8.8", "issuerUri")
    }

    @Test
    fun `requirePublicHost throws for a private host`() {
        val ex = assertFailsWith<IllegalArgumentException> {
            classifier.requirePublicHost("10.0.0.1", "issuerUri")
        }
        assertThat(ex.message).contains("issuerUri")
    }
}
