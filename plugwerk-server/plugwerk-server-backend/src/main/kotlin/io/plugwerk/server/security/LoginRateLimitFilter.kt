/*
 * Plugwerk — Plugin Marketplace for the PF4J Ecosystem
 * Copyright (C) 2026 devtank42 GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
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
 * Spring Security filter that rate-limits login attempts by client IP.
 *
 * Only applies to `POST /api/v1/auth/login`. All other requests pass through
 * without rate-limit checks.
 *
 * When the limit is exceeded, the filter short-circuits the chain and returns
 * HTTP 429 with a `Retry-After` header and a JSON error body matching the
 * [io.plugwerk.api.model.ErrorResponse] schema.
 *
 * **Reverse-proxy note:** In production the reverse proxy (nginx, ALB, etc.)
 * should set `X-Forwarded-For` to the true client IP. The filter uses the
 * leftmost value from `X-Forwarded-For` if present, falling back to
 * `request.remoteAddr`.
 */
@Component
class LoginRateLimitFilter(private val rateLimitService: LoginRateLimitService) : OncePerRequestFilter() {

    companion object {
        private const val LOGIN_PATH = "/api/v1/auth/login"
    }

    override fun shouldNotFilter(request: HttpServletRequest): Boolean =
        !(request.method == "POST" && request.requestURI == LOGIN_PATH)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val clientIp = resolveClientIp(request)
        when (val result = rateLimitService.tryConsume(clientIp)) {
            is RateLimitResult.Allowed -> filterChain.doFilter(request, response)

            is RateLimitResult.Rejected -> {
                response.status = HttpStatus.TOO_MANY_REQUESTS.value()
                response.setHeader("Retry-After", result.retryAfterSeconds.toString())
                response.contentType = MediaType.APPLICATION_JSON_VALUE
                response.writer.write(
                    """{"status":429,"error":"Too Many Requests","message":"Too many login attempts. Please try again later.","timestamp":"${OffsetDateTime.now()}"}""",
                )
            }
        }
    }

    /**
     * Extracts the client IP from `X-Forwarded-For` (leftmost entry) or falls
     * back to [HttpServletRequest.getRemoteAddr].
     */
    private fun resolveClientIp(request: HttpServletRequest): String {
        val forwarded = request.getHeader("X-Forwarded-For")
        if (!forwarded.isNullOrBlank()) {
            return forwarded.split(",").first().trim()
        }
        return request.remoteAddr
    }
}
