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

import jakarta.servlet.http.HttpServletRequest

/**
 * Extracts the client IP address from an HTTP request (RC-013 / #283).
 *
 * Prefers the leftmost entry of the `X-Forwarded-For` header when present; otherwise falls
 * back to [HttpServletRequest.getRemoteAddr]. Shared by [LoginRateLimitFilter],
 * [RefreshRateLimitFilter], and the artifact-download path in
 * [io.plugwerk.server.controller.CatalogController] — before RC-013 each of those reimplemented
 * the same snippet, and a fix in one was easy to forget in the others.
 *
 * **Proxy-trust is not yet enforced.** Today the header is accepted unconditionally, which
 * is an IP-spoofing surface (SBS-006 / #265). The long-term plan is to add a CIDR trust-list
 * check here against `remoteAddr` before honouring the header. Because every caller now goes
 * through this single function, that fix will be a one-file change without touching any call
 * site — which is the point of this extraction.
 *
 * Reverse-proxy setup: a correctly-configured nginx / ALB / Traefik in front of the backend
 * sets `X-Forwarded-For` to the true client IP. Without a reverse proxy the header is absent
 * and [HttpServletRequest.getRemoteAddr] is the true source.
 */
fun HttpServletRequest.resolveClientIp(): String {
    val forwarded = getHeader("X-Forwarded-For")
    if (!forwarded.isNullOrBlank()) {
        return forwarded.split(",").first().trim()
    }
    return remoteAddr
}
