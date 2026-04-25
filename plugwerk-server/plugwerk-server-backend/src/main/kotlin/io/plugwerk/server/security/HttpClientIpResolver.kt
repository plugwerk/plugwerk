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
import jakarta.servlet.http.HttpServletRequest
import org.springframework.security.web.util.matcher.IpAddressMatcher
import org.springframework.stereotype.Component

/**
 * Extracts the client IP address from an HTTP request, gating `X-Forwarded-For`
 * behind a configurable trusted-proxy allow-list (SBS-006 / #265).
 *
 * Used by [LoginRateLimitFilter], [io.plugwerk.server.security.RefreshRateLimitFilter],
 * and the artifact-download path in
 * [io.plugwerk.server.controller.CatalogController]. Centralising the resolver
 * means the trust check lives in exactly one place — a fix or tightening here
 * propagates to every caller automatically.
 *
 * **Resolution rules:**
 *
 *  1. If `X-Forwarded-For` is missing or blank → return `request.remoteAddr`.
 *  2. If `plugwerk.auth.trusted-proxy-cidrs` is empty → no proxy is trusted,
 *     ignore `X-Forwarded-For` entirely and return `request.remoteAddr`. This
 *     is the secure default for a server with no reverse proxy in front of it,
 *     because an attacker can otherwise spoof the header to rotate per-IP rate
 *     limit buckets at will.
 *  3. If `request.remoteAddr` is **not** in the trust list → the immediate
 *     hop is untrusted, so any `X-Forwarded-For` value is attacker-controlled.
 *     Return `request.remoteAddr`.
 *  4. Otherwise the immediate hop is a trusted proxy — return the leftmost
 *     entry of `X-Forwarded-For` (the original client). This is single-hop
 *     trust; for multi-hop proxy chains a right-to-left walk would be needed,
 *     but no realistic plugwerk deployment runs more than one reverse proxy
 *     today. If that changes, extend the loop here.
 *
 * **Operators behind a reverse proxy MUST configure
 * `PLUGWERK_AUTH_TRUSTED_PROXY_CIDRS`** with the proxy's egress IPs. Otherwise
 * every client appears to come from the proxy IP and per-IP rate-limiting
 * collapses to a single shared bucket for the whole user base.
 *
 * The CIDR list is parsed once at bean construction; invalid CIDR syntax
 * is caught earlier by
 * [io.plugwerk.server.config.PlugwerkPropertiesValidator].
 */
@Component
class HttpClientIpResolver(properties: PlugwerkProperties) {

    private val trustedProxyMatchers: List<IpAddressMatcher> =
        properties.auth.trustedProxyCidrs.map { IpAddressMatcher(it.trim()) }

    fun resolve(request: HttpServletRequest): String {
        val remoteAddr = request.remoteAddr
        val forwarded = request.getHeader(X_FORWARDED_FOR_HEADER)
        if (forwarded.isNullOrBlank()) return remoteAddr
        if (trustedProxyMatchers.isEmpty()) return remoteAddr
        if (trustedProxyMatchers.none { it.matches(remoteAddr) }) return remoteAddr
        return forwarded.split(",").first().trim()
    }

    companion object {
        private const val X_FORWARDED_FOR_HEADER = "X-Forwarded-For"
    }
}
