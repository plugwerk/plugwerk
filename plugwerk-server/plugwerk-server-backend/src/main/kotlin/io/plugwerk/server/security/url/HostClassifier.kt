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

import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress

/**
 * Pure, DNS-free classifier that decides whether a host string points at a
 * public-routable address or at something an outbound HTTP call should
 * refuse to dial. Designed for SSRF defense in front of any "fetch URL the
 * operator typed" code path (#479: OIDC discovery).
 *
 * **DNS is intentionally not consulted.** Resolving the host before the
 * fetch and again at the fetch opens a DNS-rebinding gap, and the fix for
 * that — pin to one resolved IP and pass it through — is bigger than this
 * file's scope. Instead we operate on the literal: parse it as an IP if
 * possible, otherwise apply hostname rules. The downstream HTTP client
 * still resolves once at request time, which is acceptable risk for an
 * admin-only feature behind a guard at write *and* read time.
 *
 * **IP-range blocks** (RFC 1918, loopback, link-local, ULA, IPv4-mapped
 * forms) are hardcoded — they are Internet standards, not policy. The
 * `plugwerk.auth.oidc.allow-private-discovery-uris` escape hatch
 * disables them wholesale for dev profiles.
 *
 * **Hostname blocks** ([blockedNames], [blockedSuffixes]) ARE configurable
 * — operators with a legitimate `*.internal` public domain or with their
 * own corporate-internal suffixes (`*.corp.example.com`) override the
 * defaults via `plugwerk.auth.oidc.blocked-host-names` /
 * `plugwerk.auth.oidc.blocked-host-suffixes` (env:
 * `PLUGWERK_AUTH_OIDC_BLOCKED_HOST_NAMES` /
 * `PLUGWERK_AUTH_OIDC_BLOCKED_HOST_SUFFIXES`). Default values match
 * [DEFAULT_BLOCKED_NAMES] / [DEFAULT_BLOCKED_SUFFIXES].
 */
class HostClassifier(
    blockedNames: Collection<String> = DEFAULT_BLOCKED_NAMES,
    blockedSuffixes: Collection<String> = DEFAULT_BLOCKED_SUFFIXES,
) {

    /** Lowercased copy — host comparison is case-insensitive (RFC 5890). */
    private val blockedNames: Set<String> = blockedNames
        .map { it.trim().lowercase() }
        .filter { it.isNotEmpty() }
        .toSet()

    /**
     * Lowercased copy. Suffixes that don't already start with `.` get one
     * prepended so a configured `corp.example.com` matches
     * `idp.corp.example.com` but NOT `evilcorp.example.com`.
     */
    private val blockedSuffixes: List<String> = blockedSuffixes
        .map { it.trim().lowercase() }
        .filter { it.isNotEmpty() }
        .map { if (it.startsWith(".")) it else ".$it" }

    fun isPublicRoutable(host: String): Boolean {
        val trimmed = host.trim()
        if (trimmed.isEmpty()) return false

        val normalized = trimmed.lowercase()

        if (normalized in blockedNames) return false
        if (blockedSuffixes.any { normalized.endsWith(it) }) return false

        val literal = parseIpLiteral(normalized) ?: return true
        return !isPrivateOrSpecial(literal)
    }

    fun requirePublicHost(host: String, fieldName: String) {
        require(isPublicRoutable(host)) {
            "$fieldName must not target private, loopback, link-local, or metadata addresses"
        }
    }

    /**
     * Parses [host] as an IP literal (IPv4 dotted-quad or any IPv6 form,
     * including bracketed and IPv4-mapped). Returns null when [host] is a
     * DNS name — callers must NOT fall back to DNS resolution here.
     */
    private fun parseIpLiteral(host: String): InetAddress? {
        val unwrapped = if (host.startsWith("[") && host.endsWith("]")) {
            host.substring(1, host.length - 1)
        } else {
            host
        }

        // IPv4: only accept strict dotted-quad. InetAddress.getByName accepts
        // partial forms ("10" → 0.0.0.10) and would treat plain hostnames
        // as DNS lookups, which we want to avoid here.
        if (IPV4_LITERAL_REGEX.matches(unwrapped)) {
            return runCatching { InetAddress.getByName(unwrapped) }.getOrNull()
        }

        // IPv6 literal must contain a colon and parse without DNS lookup
        // (InetAddress.getByName never resolves a literal IPv6).
        if (':' in unwrapped) {
            return runCatching { InetAddress.getByName(unwrapped) }.getOrNull()
        }

        return null
    }

    private fun isPrivateOrSpecial(addr: InetAddress): Boolean {
        if (addr.isAnyLocalAddress) return true
        if (addr.isLoopbackAddress) return true
        if (addr.isLinkLocalAddress) return true
        if (addr.isSiteLocalAddress) return true

        if (addr is Inet6Address) {
            // Unique-local: fc00::/7 (first byte 0xfc or 0xfd)
            val first = addr.address[0].toInt() and 0xff
            if (first == 0xfc || first == 0xfd) return true

            // IPv4-mapped IPv6 → re-classify as the embedded IPv4 address
            if (addr.isIPv4CompatibleAddress) {
                val v4 = InetAddress.getByAddress(addr.address.copyOfRange(12, 16))
                return isPrivateOrSpecial(v4)
            }
            val bytes = addr.address
            val isIpv4Mapped = bytes.copyOfRange(0, 10).all { it == 0.toByte() } &&
                bytes[10] == 0xff.toByte() &&
                bytes[11] == 0xff.toByte()
            if (isIpv4Mapped) {
                val v4 = InetAddress.getByAddress(bytes.copyOfRange(12, 16))
                return isPrivateOrSpecial(v4)
            }
        }

        if (addr is Inet4Address) {
            // 169.254/16 is link-local (already covered above), but explicit
            // check is cheap and self-documenting.
            val bytes = addr.address
            val first = bytes[0].toInt() and 0xff
            val second = bytes[1].toInt() and 0xff
            if (first == 169 && second == 254) return true
        }

        return false
    }

    companion object {
        /**
         * Default exact-match host blocklist — applied when the operator
         * does not override `plugwerk.auth.oidc.blocked-host-names`.
         */
        val DEFAULT_BLOCKED_NAMES: Set<String> = setOf(
            "localhost",
            "metadata.google.internal",
            "metadata",
        )

        /**
         * Default suffix blocklist — applied when the operator does not
         * override `plugwerk.auth.oidc.blocked-host-suffixes`. Each
         * default starts with `.` so it matches a full DNS label, not a
         * substring.
         */
        val DEFAULT_BLOCKED_SUFFIXES: List<String> = listOf(
            ".localhost",
            ".local",
            ".lan",
            ".internal",
        )

        private val IPV4_LITERAL_REGEX = Regex("""\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}""")
    }
}
