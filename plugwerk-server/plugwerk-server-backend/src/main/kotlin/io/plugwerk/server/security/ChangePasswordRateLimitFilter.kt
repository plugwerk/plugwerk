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
import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.time.OffsetDateTime

/**
 * Rate-limits `POST /api/v1/auth/change-password` by authenticated subject.
 *
 * Registered in the Spring Security chain **after** `BearerTokenAuthenticationFilter`
 * so `SecurityContextHolder` is populated with the JWT principal by the time this
 * filter runs. Unauthenticated / anonymous requests are passed through without
 * consuming a bucket token — the downstream chain rejects them with 401.
 *
 * When the limit is exceeded the filter short-circuits the chain and returns
 * HTTP 429 with a `Retry-After` header and a JSON body matching the
 * [io.plugwerk.api.model.ErrorResponse] schema.
 */
@Component
class ChangePasswordRateLimitFilter(private val rateLimitService: ChangePasswordRateLimitService) :
    OncePerRequestFilter() {

    override fun shouldNotFilter(request: HttpServletRequest): Boolean =
        !(request.method == "POST" && request.requestURI == CHANGE_PASSWORD_PATH)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val subject = currentSubject()
        if (subject == null) {
            // No authenticated principal — do not consume a bucket token. The downstream
            // chain (resource server + controller) will return 401.
            filterChain.doFilter(request, response)
            return
        }

        when (val result = rateLimitService.tryConsume(subject)) {
            is RateLimitResult.Allowed -> filterChain.doFilter(request, response)

            is RateLimitResult.Rejected -> {
                response.status = HttpStatus.TOO_MANY_REQUESTS.value()
                response.setHeader("Retry-After", result.retryAfterSeconds.toString())
                response.contentType = MediaType.APPLICATION_JSON_VALUE
                response.writer.write(
                    """{"status":429,"error":"Too Many Requests","message":"Too many password-change attempts. Please try again later.","timestamp":"${OffsetDateTime.now()}"}""",
                )
            }
        }
    }

    private fun currentSubject(): String? {
        val auth = currentAuthenticationOrNull() ?: return null
        if (auth is AnonymousAuthenticationToken) return null
        if (!auth.isAuthenticated) return null
        val name = auth.name
        if (name.isNullOrBlank()) return null
        // Public-catalog carve-out tokens (PublicNamespaceFilter) are not real subjects;
        // they should not consume a per-user rate-limit bucket.
        if (PublicNamespaceFilter.isPublicCatalogPrincipal(name)) return null
        return name
    }

    private companion object {
        const val CHANGE_PASSWORD_PATH = "/api/v1/auth/change-password"
    }
}
