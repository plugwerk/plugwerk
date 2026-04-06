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

import io.plugwerk.server.repository.NamespaceAccessKeyRepository
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.annotation.Lazy
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.time.OffsetDateTime

@Component
class NamespaceAccessKeyAuthFilter(
    private val apiKeyRepository: NamespaceAccessKeyRepository,
    @Lazy private val passwordEncoder: PasswordEncoder,
) : OncePerRequestFilter() {

    companion object {
        const val HEADER_NAME = "X-Api-Key"
        private const val PREFIX_LENGTH = 8
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val apiKey = request.getHeader(HEADER_NAME)
        if (apiKey != null && apiKey.length >= PREFIX_LENGTH &&
            SecurityContextHolder.getContext().authentication == null
        ) {
            val prefix = apiKey.take(PREFIX_LENGTH)
            val candidates = apiKeyRepository.findByKeyPrefixAndRevokedFalse(prefix)
            for (candidate in candidates) {
                if (candidate.expiresAt != null && candidate.expiresAt!!.isBefore(OffsetDateTime.now())) continue
                if (!passwordEncoder.matches(apiKey, candidate.keyHash)) continue

                val auth = UsernamePasswordAuthenticationToken(
                    "key:${candidate.namespace.slug}",
                    null,
                    listOf(SimpleGrantedAuthority("ROLE_API_CLIENT")),
                )
                SecurityContextHolder.getContext().authentication = auth
                break
            }
        }
        filterChain.doFilter(request, response)
    }
}
