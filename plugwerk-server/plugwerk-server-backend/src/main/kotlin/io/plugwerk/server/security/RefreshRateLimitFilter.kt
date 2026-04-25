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

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.time.OffsetDateTime

/**
 * Per-IP rate limiting for `POST /api/v1/auth/refresh` (ADR-0027 / #294).
 *
 * Defends against refresh-spam DoS and covers the edge case where an attacker who cannot
 * read the httpOnly cookie might still try to hammer the endpoint to detect timing or
 * error-response differences between known-revoked and unknown tokens.
 *
 * Defaults (30 req / 60 s per IP) are more permissive than [LoginRateLimitFilter] because
 * a legitimate SPA with multiple tabs can trigger several refreshes concurrently around
 * access-token expiry, especially after hibernation resume.
 *
 * Uses the same reverse-proxy-aware IP resolution as [LoginRateLimitFilter].
 */
@Component
class RefreshRateLimitFilter(
    private val bucketService: BucketRateLimitService,
    private val clientIpResolver: HttpClientIpResolver,
) : OncePerRequestFilter() {

    companion object {
        private const val REFRESH_PATH = "/api/v1/auth/refresh"
        private const val SCOPE = "refresh"
        private const val MAX_ATTEMPTS = 30
        private const val WINDOW_SECONDS = 60L
    }

    override fun shouldNotFilter(request: HttpServletRequest): Boolean =
        !(request.method == "POST" && request.requestURI == REFRESH_PATH)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val clientIp = clientIpResolver.resolve(request)
        when (val result = bucketService.tryConsume(SCOPE, clientIp, MAX_ATTEMPTS, WINDOW_SECONDS)) {
            is RateLimitResult.Allowed -> filterChain.doFilter(request, response)

            is RateLimitResult.Rejected -> {
                response.status = HttpStatus.TOO_MANY_REQUESTS.value()
                response.setHeader("Retry-After", result.retryAfterSeconds.toString())
                response.contentType = MediaType.APPLICATION_JSON_VALUE
                response.writer.write(
                    """{"status":429,"error":"Too Many Requests","message":"Too many refresh attempts. Please try again later.","timestamp":"${OffsetDateTime.now()}"}""",
                )
            }
        }
    }
}
