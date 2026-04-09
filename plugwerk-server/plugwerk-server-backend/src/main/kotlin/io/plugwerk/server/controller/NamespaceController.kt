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
package io.plugwerk.server.controller

import io.plugwerk.api.NamespacesApi
import io.plugwerk.api.model.NamespaceCreateRequest
import io.plugwerk.api.model.NamespaceSummary
import io.plugwerk.api.model.NamespaceUpdateRequest
import io.plugwerk.server.domain.NamespaceEntity
import io.plugwerk.server.domain.NamespaceRole
import io.plugwerk.server.security.NamespaceAuthorizationService
import io.plugwerk.server.service.NamespaceAlreadyExistsException
import io.plugwerk.server.service.NamespaceService
import io.plugwerk.server.service.UnauthorizedException
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.net.URI

@RestController
@RequestMapping("/api/v1")
class NamespaceController(
    private val namespaceService: NamespaceService,
    private val namespaceAuthorizationService: NamespaceAuthorizationService,
) : NamespacesApi {

    override fun listNamespaces(): ResponseEntity<List<NamespaceSummary>> {
        val auth = SecurityContextHolder.getContext().authentication
            ?: throw UnauthorizedException("Not authenticated")
        val namespaces = namespaceAuthorizationService.listVisibleNamespaces(auth)
            .map { it.toSummary() }
        return ResponseEntity.ok(namespaces)
    }

    override fun createNamespace(namespaceCreateRequest: NamespaceCreateRequest): ResponseEntity<NamespaceSummary> {
        val auth = SecurityContextHolder.getContext().authentication
            ?: throw UnauthorizedException("Not authenticated")
        namespaceAuthorizationService.requireSuperadmin(auth)
        return try {
            val entity = namespaceService.create(
                slug = namespaceCreateRequest.slug,
                name = namespaceCreateRequest.name,
                description = namespaceCreateRequest.description,
                publicCatalog = namespaceCreateRequest.publicCatalog ?: false,
                autoApproveReleases = namespaceCreateRequest.autoApproveReleases ?: false,
            )
            ResponseEntity.created(URI("/api/v1/namespaces/${entity.slug}")).body(entity.toSummary())
        } catch (_: NamespaceAlreadyExistsException) {
            ResponseEntity.status(409).build()
        }
    }

    override fun updateNamespace(
        ns: String,
        namespaceUpdateRequest: NamespaceUpdateRequest,
    ): ResponseEntity<NamespaceSummary> {
        val auth = SecurityContextHolder.getContext().authentication
            ?: throw UnauthorizedException("Not authenticated")
        namespaceAuthorizationService.requireRole(ns, auth, NamespaceRole.ADMIN)
        val entity = namespaceService.update(
            slug = ns,
            name = namespaceUpdateRequest.name,
            description = namespaceUpdateRequest.description,
            publicCatalog = namespaceUpdateRequest.publicCatalog,
            autoApproveReleases = namespaceUpdateRequest.autoApproveReleases,
        )
        return ResponseEntity.ok(entity.toSummary())
    }

    override fun deleteNamespace(ns: String): ResponseEntity<Unit> {
        val auth = SecurityContextHolder.getContext().authentication
            ?: throw UnauthorizedException("Not authenticated")
        namespaceAuthorizationService.requireSuperadmin(auth)
        namespaceService.delete(ns)
        return ResponseEntity.noContent().build()
    }

    private fun NamespaceEntity.toSummary(): NamespaceSummary = NamespaceSummary(
        slug = slug,
        name = name,
        description = description,
        publicCatalog = publicCatalog,
        autoApproveReleases = autoApproveReleases,
        createdAt = createdAt,
    )
}
