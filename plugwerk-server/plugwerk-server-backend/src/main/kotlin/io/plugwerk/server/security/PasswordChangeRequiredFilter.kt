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

import io.plugwerk.server.repository.UserRepository
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

// Blocks all API requests (except /api/v1/auth/) when the JWT user's
// passwordChangeRequired flag is set. API key clients (not JwtAuthenticationToken)
// are not affected. After #351 the JWT `sub` is `plugwerk_user.id` (UUID).
@Component
class PasswordChangeRequiredFilter(private val userRepository: UserRepository) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val authentication = SecurityContextHolder.getContext().authentication

        if (authentication is JwtAuthenticationToken && requiresPasswordChange(authentication.name)) {
            if (!request.requestURI.startsWith("/api/v1/auth/")) {
                response.status = HttpServletResponse.SC_FORBIDDEN
                response.contentType = "application/json"
                response.writer.write(
                    """{"error":"PASSWORD_CHANGE_REQUIRED","message":"You must change your password before accessing this resource."}""",
                )
                return
            }
        }

        filterChain.doFilter(request, response)
    }

    /**
     * The JWT `sub` is the `plugwerk_user.id` UUID-string after #351. A non-UUID
     * value can only originate from a forged or pre-#351 token; both should be
     * treated as "no password change required" here so that downstream filters
     * (signature/exp validation, revocation check) handle the rejection on the
     * right error path instead of bleeding through as a 403.
     */
    private fun requiresPasswordChange(subject: String): Boolean {
        val userId = runCatching { UUID.fromString(subject) }.getOrNull() ?: return false
        return userRepository.findById(userId).map { it.passwordChangeRequired }.orElse(false)
    }
}
