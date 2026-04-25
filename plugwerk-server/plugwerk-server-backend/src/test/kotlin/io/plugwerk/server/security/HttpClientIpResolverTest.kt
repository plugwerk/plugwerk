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

import io.plugwerk.server.PlugwerkProperties
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest

/**
 * Unit tests for [HttpClientIpResolver] — the proxy-trust-aware client IP
 * extraction introduced for SBS-006 / #265. The resolver must:
 *
 *   - return `request.remoteAddr` whenever `X-Forwarded-For` is missing or
 *     blank, regardless of trust configuration;
 *   - **ignore** `X-Forwarded-For` entirely when the trust list is empty
 *     (the secure default for a server with no reverse proxy in front);
 *   - **ignore** `X-Forwarded-For` when the immediate hop (`remoteAddr`) is
 *     not in the trust list — a forged header from an untrusted client
 *     cannot rotate the rate-limit bucket;
 *   - return the **leftmost** `X-Forwarded-For` value (the original client)
 *     when the immediate hop matches a trusted CIDR.
 */
class HttpClientIpResolverTest {

    @Test
    fun `returns remoteAddr when X-Forwarded-For is missing`() {
        val resolver = resolver(trustedCidrs = listOf("10.0.0.0/8"))
        val request = MockHttpServletRequest().apply { remoteAddr = "10.1.2.3" }

        assertThat(resolver.resolve(request)).isEqualTo("10.1.2.3")
    }

    @Test
    fun `returns remoteAddr when X-Forwarded-For is blank`() {
        val resolver = resolver(trustedCidrs = listOf("10.0.0.0/8"))
        val request = MockHttpServletRequest().apply {
            remoteAddr = "10.1.2.3"
            addHeader("X-Forwarded-For", "   ")
        }

        assertThat(resolver.resolve(request)).isEqualTo("10.1.2.3")
    }

    @Test
    fun `ignores X-Forwarded-For when trust list is empty (SBS-006 default)`() {
        // The secure default: no proxy is configured as trusted, so the
        // header is attacker-controlled and must be discarded — even if the
        // client legitimately came through a proxy that operators forgot to
        // configure. Forgetting to trust > silently honouring spoofs.
        val resolver = resolver(trustedCidrs = emptyList())
        val request = MockHttpServletRequest().apply {
            remoteAddr = "10.1.2.3"
            addHeader("X-Forwarded-For", "203.0.113.5")
        }

        assertThat(resolver.resolve(request)).isEqualTo("10.1.2.3")
    }

    @Test
    fun `ignores X-Forwarded-For when immediate hop is not in trust list`() {
        // Trust list is configured, but the request did not come from one of
        // the trusted proxies — so the X-Forwarded-For header is forged by an
        // untrusted client and must be ignored. This is the SBS-006 vector.
        val resolver = resolver(trustedCidrs = listOf("10.0.0.0/8"))
        val request = MockHttpServletRequest().apply {
            remoteAddr = "203.0.113.50" // public client, not in 10.0.0.0/8
            addHeader("X-Forwarded-For", "1.2.3.4")
        }

        assertThat(resolver.resolve(request)).isEqualTo("203.0.113.50")
    }

    @Test
    fun `honours X-Forwarded-For when immediate hop matches a trusted CIDR`() {
        val resolver = resolver(trustedCidrs = listOf("10.0.0.0/8"))
        val request = MockHttpServletRequest().apply {
            remoteAddr = "10.1.2.3" // trusted reverse proxy
            addHeader("X-Forwarded-For", "203.0.113.50")
        }

        assertThat(resolver.resolve(request)).isEqualTo("203.0.113.50")
    }

    @Test
    fun `returns leftmost entry when X-Forwarded-For has multiple hops`() {
        // Multi-hop chain: 203.0.113.50 (real client) → upstream-cdn (172.16.0.7)
        // → our trusted nginx (10.1.2.3 = remoteAddr). The leftmost value is the
        // original client; the rest is the chain through which the request flowed.
        val resolver = resolver(trustedCidrs = listOf("10.0.0.0/8"))
        val request = MockHttpServletRequest().apply {
            remoteAddr = "10.1.2.3"
            addHeader("X-Forwarded-For", "203.0.113.50, 172.16.0.7")
        }

        assertThat(resolver.resolve(request)).isEqualTo("203.0.113.50")
    }

    @Test
    fun `single-IP CIDR exact match is honoured`() {
        // A /32 CIDR pinpoints exactly one trusted proxy.
        val resolver = resolver(trustedCidrs = listOf("127.0.0.1/32"))
        val trustedRequest = MockHttpServletRequest().apply {
            remoteAddr = "127.0.0.1"
            addHeader("X-Forwarded-For", "203.0.113.50")
        }
        val untrustedRequest = MockHttpServletRequest().apply {
            remoteAddr = "127.0.0.2"
            addHeader("X-Forwarded-For", "203.0.113.50")
        }

        assertThat(resolver.resolve(trustedRequest)).isEqualTo("203.0.113.50")
        assertThat(resolver.resolve(untrustedRequest)).isEqualTo("127.0.0.2")
    }

    @Test
    fun `IPv6 CIDR is supported`() {
        val resolver = resolver(trustedCidrs = listOf("::1/128"))
        val request = MockHttpServletRequest().apply {
            remoteAddr = "0:0:0:0:0:0:0:1" // ::1 expanded form
            addHeader("X-Forwarded-For", "2001:db8::5")
        }

        assertThat(resolver.resolve(request)).isEqualTo("2001:db8::5")
    }

    @Test
    fun `trims surrounding whitespace from leftmost X-Forwarded-For value`() {
        // Some proxies emit "client , next" with stray whitespace around the
        // commas. The leftmost value must be returned without the spaces.
        val resolver = resolver(trustedCidrs = listOf("10.0.0.0/8"))
        val request = MockHttpServletRequest().apply {
            remoteAddr = "10.1.2.3"
            addHeader("X-Forwarded-For", "  203.0.113.50  , 172.16.0.7")
        }

        assertThat(resolver.resolve(request)).isEqualTo("203.0.113.50")
    }

    @Test
    fun `multiple trusted CIDRs all honoured (any-match wins)`() {
        // Operators commonly configure several CIDR ranges (loopback +
        // internal LAN + a CDN egress range). The matcher fires if any
        // entry matches; first/last order does not matter.
        val resolver = resolver(
            trustedCidrs = listOf("127.0.0.1/32", "10.0.0.0/8", "192.168.0.0/16"),
        )
        listOf("127.0.0.1", "10.5.6.7", "192.168.99.42").forEach { proxyIp ->
            val request = MockHttpServletRequest().apply {
                remoteAddr = proxyIp
                addHeader("X-Forwarded-For", "203.0.113.50")
            }
            assertThat(resolver.resolve(request))
                .withFailMessage("expected leftmost XFF to be honoured for trusted hop %s", proxyIp)
                .isEqualTo("203.0.113.50")
        }
    }

    private fun resolver(trustedCidrs: List<String>): HttpClientIpResolver {
        // jwtSecret / encryptionKey are required by JSR-303 validation in
        // production; here we construct AuthProperties directly — annotations
        // are not enforced outside Spring binding, so any values work. The
        // resolver only reads `trustedProxyCidrs`.
        val properties = PlugwerkProperties(
            auth = PlugwerkProperties.AuthProperties(
                jwtSecret = "test-jwt-secret-at-least-32-characters-long",
                encryptionKey = "test-encryption-key-at-least-16",
                trustedProxyCidrs = trustedCidrs,
            ),
        )
        return HttpClientIpResolver(properties)
    }
}
