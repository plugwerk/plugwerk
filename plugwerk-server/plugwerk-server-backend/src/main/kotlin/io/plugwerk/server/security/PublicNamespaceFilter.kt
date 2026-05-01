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

import io.plugwerk.server.repository.NamespaceRepository
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Grants anonymous access to read-only catalog endpoints for namespaces marked as public
 * (ADR-0011 — anonymous reads on `publicCatalog = true` namespaces).
 *
 * When a GET request targets a catalog read endpoint under
 * `/api/v1/namespaces/{ns}/(plugins|updates/check)` and the namespace has
 * `publicCatalog = true`, this filter installs a [UsernamePasswordAuthenticationToken]
 * with principal `"public:<ns>"` and authority [PUBLIC_CATALOG_AUTHORITY].
 *
 * Why not [org.springframework.security.authentication.AnonymousAuthenticationToken]?
 * Spring's [org.springframework.security.web.access.intercept.AuthorizationFilter] uses
 * `AuthenticatedAuthorizationManager` for `.anyRequest().authenticated()`, which calls
 * `AuthenticationTrustResolver.isAnonymous(authentication)` and rejects every anonymous
 * token regardless of `isAuthenticated()`. Issue #374 was a 401 caused exactly by that
 * gate. A non-anonymous token with a clearly namespaced principal (`public:<ns>`)
 * passes `authenticated()` while remaining trivially distinguishable from real users
 * downstream — see [io.plugwerk.server.security.NamespaceAuthorizationService.parseUserId]
 * which falls through to deny-by-default for any non-UUID principal.
 *
 * The path pattern is intentionally narrow: only catalog-read endpoints are surfaced
 * publicly. Members, access keys, settings and other namespace-scoped GETs remain
 * 401 for unauthenticated callers.
 */
@Component
class PublicNamespaceFilter(private val namespaceRepository: NamespaceRepository) : OncePerRequestFilter() {

    /**
     * Matches read-only catalog endpoints (ADR-0011):
     *   /api/v1/namespaces/{ns}/plugins
     *   /api/v1/namespaces/{ns}/plugins/{id}
     *   /api/v1/namespaces/{ns}/plugins/{id}/releases
     *   /api/v1/namespaces/{ns}/plugins/{id}/releases/{ver}
     *   /api/v1/namespaces/{ns}/plugins/{id}/releases/{ver}/download
     *   /api/v1/namespaces/{ns}/updates/check
     *
     * Anything else under `/api/v1/namespaces/{ns}/` is intentionally excluded.
     */
    private val catalogPathPattern = Regex("^/api/v1/namespaces/([^/]+)/(?:plugins(?:/.*)?|updates/check)$")

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        if (request.method == "GET" && SecurityContextHolder.getContext().authentication == null) {
            val match = catalogPathPattern.find(request.requestURI)
            if (match != null) {
                val ns = match.groupValues[1]
                val isPublic = namespaceRepository.findBySlug(ns)
                    .map { it.publicCatalog }
                    .orElse(false)
                if (isPublic) {
                    val auth = UsernamePasswordAuthenticationToken(
                        publicPrincipal(ns),
                        "",
                        listOf(SimpleGrantedAuthority(PUBLIC_CATALOG_AUTHORITY)),
                    )
                    SecurityContextHolder.getContext().authentication = auth
                }
            }
        }
        filterChain.doFilter(request, response)
    }

    companion object {
        /** Granted authority that identifies a request authenticated via the public-catalog carve-out. */
        const val PUBLIC_CATALOG_AUTHORITY = "ROLE_PUBLIC_CATALOG"

        /** Principal-name prefix used by [PublicNamespaceFilter]. Stable contract for downstream filters. */
        const val PUBLIC_PRINCIPAL_PREFIX = "public:"

        /** Builds the principal string used on the public-catalog token. */
        fun publicPrincipal(namespaceSlug: String): String = "$PUBLIC_PRINCIPAL_PREFIX$namespaceSlug"

        /** True if [authentication] (or its name) was issued by the public-catalog carve-out. */
        fun isPublicCatalogPrincipal(name: String?): Boolean = name != null && name.startsWith(PUBLIC_PRINCIPAL_PREFIX)
    }
}
