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

import io.plugwerk.server.repository.NamespaceAccessKeyRepository
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.security.MessageDigest
import java.time.OffsetDateTime

@Component
class NamespaceAccessKeyAuthFilter(private val apiKeyRepository: NamespaceAccessKeyRepository) :
    OncePerRequestFilter() {

    companion object {
        const val HEADER_NAME = "X-Api-Key"
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val apiKey = request.getHeader(HEADER_NAME)
        if (apiKey != null && SecurityContextHolder.getContext().authentication == null) {
            val keyHash = hashApiKey(apiKey)
            apiKeyRepository.findByKeyHash(keyHash)
                .filter { !it.revoked && (it.expiresAt == null || it.expiresAt!!.isAfter(OffsetDateTime.now())) }
                .ifPresent { keyEntity ->
                    val auth = UsernamePasswordAuthenticationToken(
                        "key:${keyEntity.namespace.slug}",
                        null,
                        listOf(SimpleGrantedAuthority("ROLE_API_CLIENT")),
                    )
                    SecurityContextHolder.getContext().authentication = auth
                }
        }
        filterChain.doFilter(request, response)
    }

    private fun hashApiKey(apiKey: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(apiKey.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
