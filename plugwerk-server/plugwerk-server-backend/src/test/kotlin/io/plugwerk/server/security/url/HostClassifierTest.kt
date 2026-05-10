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
        assertThat(HostClassifier.isPublicRoutable(host))
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
        assertThat(HostClassifier.isPublicRoutable(host))
            .`as`("host=%s should be classified as public", host)
            .isTrue()
    }

    @ParameterizedTest
    @ValueSource(strings = ["", " ", "\t"])
    fun `rejects blank input`(host: String) {
        assertThat(HostClassifier.isPublicRoutable(host)).isFalse()
    }

    @org.junit.jupiter.api.Test
    fun `requirePublicHost throws IllegalArgumentException with field-prefixed message for non-public host`() {
        val ex = org.junit.jupiter.api.assertThrows<IllegalArgumentException> {
            HostClassifier.requirePublicHost("169.254.169.254", "issuerUri")
        }
        assertThat(ex.message)
            .startsWith("issuerUri")
            .contains("private, loopback, link-local, or metadata")
    }

    @org.junit.jupiter.api.Test
    fun `requirePublicHost is silent for public host`() {
        HostClassifier.requirePublicHost("login.example.com", "issuerUri")
    }
}
