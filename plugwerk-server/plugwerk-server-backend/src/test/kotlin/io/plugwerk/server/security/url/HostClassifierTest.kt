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
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class HostClassifierTest {

    private val classifier = HostClassifier()

    @ParameterizedTest
    @ValueSource(
        strings = [
            // RFC 1918 — class A
            "10.0.0.1",
            "10.255.255.255",
            // RFC 1918 — class B
            "172.16.0.1",
            "172.31.255.255",
            // RFC 1918 — class C
            "192.168.0.1",
            "192.168.255.255",
            // Loopback IPv4
            "127.0.0.1",
            "127.255.255.255",
            // Loopback hostname (case-insensitive)
            "localhost",
            "LOCALHOST",
            "LocalHost",
            // *.localhost suffix (per RFC 6761)
            "foo.localhost",
            "deep.nested.localhost",
            // Loopback IPv6
            "::1",
            "0:0:0:0:0:0:0:1",
            // Link-local IPv4 + cloud metadata
            "169.254.0.1",
            "169.254.169.254",
            // Link-local IPv6
            "fe80::1",
            "fe80:0:0:0:0:0:0:1",
            // Unspecified
            "0.0.0.0",
            "::",
            // Unique-local IPv6 (fc00::/7)
            "fc00::1",
            "fd00::1",
            "fdff:ffff:ffff:ffff:ffff:ffff:ffff:ffff",
            // Cloud metadata exact-match names
            "metadata.google.internal",
            "metadata",
            // mDNS / corporate-internal suffixes
            "printer.local",
            "fileshare.lan",
            "intranet.internal",
            "PRINTER.LOCAL",
            // IPv4-mapped IPv6 onto private/loopback ranges
            "::ffff:127.0.0.1",
            "::ffff:10.0.0.1",
            "::ffff:169.254.169.254",
        ],
    )
    fun `rejects private, loopback, link-local, ULA, metadata, and mDNS hosts`(host: String) {
        assertThat(classifier.isPublicRoutable(host))
            .`as`("host=%s should be classified as non-public", host)
            .isFalse()
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            // RFC 1918 boundary negatives
            "11.0.0.0",
            "172.15.255.255",
            "172.32.0.0",
            "192.167.255.255",
            "192.169.0.0",
            // Public DNS names
            "accounts.google.com",
            "login.microsoftonline.com",
            "github.com",
            "auth.example.com",
            // Public IPv4
            "8.8.8.8",
            "1.1.1.1",
            // Public IPv6
            "2001:4860:4860::8888",
            "2606:4700:4700::1111",
        ],
    )
    fun `accepts public hosts`(host: String) {
        assertThat(classifier.isPublicRoutable(host))
            .`as`("host=%s should be classified as public", host)
            .isTrue()
    }

    @ParameterizedTest
    @ValueSource(strings = ["", " ", "\t"])
    fun `rejects blank input`(host: String) {
        assertThat(classifier.isPublicRoutable(host)).isFalse()
    }

    @org.junit.jupiter.api.Test
    fun `requirePublicHost throws IllegalArgumentException with field-prefixed message for non-public host`() {
        val ex = org.junit.jupiter.api.assertThrows<IllegalArgumentException> {
            classifier.requirePublicHost("169.254.169.254", "issuerUri")
        }
        assertThat(ex.message)
            .startsWith("issuerUri")
            .contains("private, loopback, link-local, or metadata")
    }

    @org.junit.jupiter.api.Test
    fun `requirePublicHost is silent for public host`() {
        classifier.requirePublicHost("login.example.com", "issuerUri")
    }

    // -- override tests ------------------------------------------------------

    @org.junit.jupiter.api.Test
    fun `custom blockedNames replaces the defaults entirely`() {
        // Operator override: drop `localhost` etc. from the name list, add
        // their own. Default suffixes still apply because we only overrode
        // the names list.
        val custom = HostClassifier(blockedNames = setOf("private-idp", "internal-only"))

        assertThat(custom.isPublicRoutable("private-idp")).isFalse()
        assertThat(custom.isPublicRoutable("internal-only")).isFalse()
        // `metadata` was a default name-block but is no longer in the
        // override → public (it is not an IP literal so IP-class checks
        // do not catch it).
        assertThat(custom.isPublicRoutable("metadata")).isTrue()
        // default suffix still in effect.
        assertThat(custom.isPublicRoutable("printer.local")).isFalse()
    }

    @org.junit.jupiter.api.Test
    fun `custom blockedSuffixes replaces the defaults entirely`() {
        val custom = HostClassifier(blockedSuffixes = listOf(".corp.example.com", "intra"))

        assertThat(custom.isPublicRoutable("idp.corp.example.com")).isFalse()
        // suffix without a leading `.` gets one prepended → matches a label,
        // not a substring.
        assertThat(custom.isPublicRoutable("foo.intra")).isFalse()
        assertThat(custom.isPublicRoutable("evilcorp.example.com")).isTrue()
        // default suffix `.local` no longer blocked (we replaced the list).
        assertThat(custom.isPublicRoutable("printer.local")).isTrue()
    }

    @org.junit.jupiter.api.Test
    fun `IP range blocks remain hardcoded even with empty hostname overrides`() {
        // Operator passes empty lists explicitly — RFC 1918 / loopback /
        // metadata IP-LITERALS must still be rejected. The escape hatch
        // `allow-private-discovery-uris` is the only way to disable
        // those.
        val custom = HostClassifier(blockedNames = emptySet(), blockedSuffixes = emptyList())

        assertThat(custom.isPublicRoutable("169.254.169.254")).isFalse()
        assertThat(custom.isPublicRoutable("10.0.0.1")).isFalse()
        assertThat(custom.isPublicRoutable("127.0.0.1")).isFalse()
        assertThat(custom.isPublicRoutable("::1")).isFalse()
    }

    @org.junit.jupiter.api.Test
    fun `override entries are normalised case-insensitively and whitespace-trimmed`() {
        val custom = HostClassifier(
            blockedNames = setOf("  PRIVATE-IDP  ", "Other.Internal"),
            blockedSuffixes = listOf("  .CORP.EXAMPLE.COM  "),
        )

        assertThat(custom.isPublicRoutable("private-idp")).isFalse()
        assertThat(custom.isPublicRoutable("PRIVATE-IDP")).isFalse()
        assertThat(custom.isPublicRoutable("other.internal")).isFalse()
        assertThat(custom.isPublicRoutable("idp.corp.example.com")).isFalse()
    }
}
