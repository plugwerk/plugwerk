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

import io.plugwerk.server.repository.NamespaceRepository
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Grants anonymous access to read-only catalog endpoints for namespaces marked as public.
 *
 * When a GET request targets `/api/v1/namespaces/{ns}/...` and the namespace has
 * `publicCatalog = true`, this filter sets an [AnonymousAuthenticationToken] so that
 * Spring Security's `anyRequest().authenticated()` rule is satisfied without a Bearer token.
 *
 * This filter runs before the OAuth2 resource server JWT filter.
 */
@Component
class PublicNamespaceFilter(private val namespaceRepository: NamespaceRepository) : OncePerRequestFilter() {

    private val namespacePathPattern = Regex("^/api/v1/namespaces/([^/]+)(?:/.*)?$")

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        if (request.method == "GET" && SecurityContextHolder.getContext().authentication == null) {
            val match = namespacePathPattern.find(request.requestURI)
            if (match != null) {
                val ns = match.groupValues[1]
                val isPublic = namespaceRepository.findBySlug(ns)
                    .map { it.publicCatalog }
                    .orElse(false)
                if (isPublic) {
                    val auth = AnonymousAuthenticationToken(
                        "public-namespace",
                        "anonymous",
                        listOf(SimpleGrantedAuthority("ROLE_ANONYMOUS")),
                    )
                    SecurityContextHolder.getContext().authentication = auth
                }
            }
        }
        filterChain.doFilter(request, response)
    }
}
