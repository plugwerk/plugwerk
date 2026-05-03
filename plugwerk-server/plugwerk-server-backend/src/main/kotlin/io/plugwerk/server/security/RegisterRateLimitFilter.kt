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
 * IP-keyed rate-limit filter for `POST /api/v1/auth/register` (#420).
 *
 * This is the first of two buckets — the second (email-keyed) fires
 * inside [io.plugwerk.server.controller.AuthRegistrationController] after
 * body parsing. Splitting them avoids the body-caching wrapper
 * gymnastics; the IP layer here drops the cheapest abuse vector
 * (volume) before any payload is even read.
 *
 * Mirrors [LoginRateLimitFilter]: 429 + Retry-After + JSON body matching
 * the `ErrorResponse` shape; reverse-proxy-aware client-IP via
 * [HttpClientIpResolver].
 */
@Component
class RegisterRateLimitFilter(
    private val rateLimitService: RegisterRateLimitService,
    private val clientIpResolver: HttpClientIpResolver,
) : OncePerRequestFilter() {

    companion object {
        private const val REGISTER_PATH = "/api/v1/auth/register"
    }

    override fun shouldNotFilter(request: HttpServletRequest): Boolean =
        !(request.method == "POST" && request.requestURI == REGISTER_PATH)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val clientIp = clientIpResolver.resolve(request)
        when (val result = rateLimitService.tryConsumeIp(clientIp)) {
            is RateLimitResult.Allowed -> filterChain.doFilter(request, response)

            is RateLimitResult.Rejected -> {
                response.status = HttpStatus.TOO_MANY_REQUESTS.value()
                response.setHeader("Retry-After", result.retryAfterSeconds.toString())
                response.contentType = MediaType.APPLICATION_JSON_VALUE
                response.writer.write(
                    """{"status":429,"error":"Too Many Requests","message":"Too many registration attempts. Please try again later.","timestamp":"${OffsetDateTime.now()}"}""",
                )
            }
        }
    }
}
