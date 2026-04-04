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

// Blocks all API requests (except /api/v1/auth/) when the JWT user's passwordChangeRequired flag is set.
// API key clients (not JwtAuthenticationToken) are not affected.
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

    private fun requiresPasswordChange(username: String): Boolean =
        userRepository.findByUsername(username).map { it.passwordChangeRequired }.orElse(false)
}
