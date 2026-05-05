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
 * IP-keyed rate-limit filter for the public password-reset endpoints
 * (#421). Fires before any body parsing so the cheapest abuse vector
 * (volume from one source) is dropped immediately.
 *
 * The token-keyed bucket fires inside the controller after the body
 * has been parsed — same split as [RegisterRateLimitFilter].
 */
@Component
class PasswordResetRateLimitFilter(
    private val rateLimitService: PasswordResetRateLimitService,
    private val clientIpResolver: HttpClientIpResolver,
) : OncePerRequestFilter() {

    companion object {
        private const val FORGOT_PATH = "/api/v1/auth/forgot-password"
        private const val RESET_PATH = "/api/v1/auth/reset-password"
    }

    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        if (request.method != "POST") return true
        return request.requestURI != FORGOT_PATH && request.requestURI != RESET_PATH
    }

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
                    """{"status":429,"error":"Too Many Requests","message":"Too many password-reset attempts. Please try again later.","timestamp":"${OffsetDateTime.now()}"}""",
                )
            }
        }
    }
}
