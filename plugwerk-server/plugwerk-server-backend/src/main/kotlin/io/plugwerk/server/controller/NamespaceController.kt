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
package io.plugwerk.server.controller

import io.plugwerk.api.NamespacesApi
import io.plugwerk.api.model.NamespaceCreateRequest
import io.plugwerk.api.model.NamespaceSummary
import io.plugwerk.server.security.NamespaceAuthorizationService
import io.plugwerk.server.service.NamespaceAlreadyExistsException
import io.plugwerk.server.service.NamespaceService
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
        val namespaces = namespaceService.findAll()
            .map { NamespaceSummary(slug = it.slug, ownerOrg = it.ownerOrg) }
        return ResponseEntity.ok(namespaces)
    }

    override fun createNamespace(namespaceCreateRequest: NamespaceCreateRequest): ResponseEntity<NamespaceSummary> {
        val auth = SecurityContextHolder.getContext().authentication
            ?: throw io.plugwerk.server.service.ForbiddenException("Not authenticated")
        namespaceAuthorizationService.requireSuperadmin(auth)
        return try {
            val entity = namespaceService.create(
                slug = namespaceCreateRequest.slug,
                ownerOrg = namespaceCreateRequest.ownerOrg ?: "default",
            )
            val summary = NamespaceSummary(slug = entity.slug, ownerOrg = entity.ownerOrg)
            ResponseEntity.created(URI("/api/v1/namespaces/${entity.slug}")).body(summary)
        } catch (_: NamespaceAlreadyExistsException) {
            ResponseEntity.status(409).build()
        }
    }
}
