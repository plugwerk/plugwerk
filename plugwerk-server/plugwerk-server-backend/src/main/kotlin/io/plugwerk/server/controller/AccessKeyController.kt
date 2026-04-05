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

import io.plugwerk.api.AccessKeysApi
import io.plugwerk.api.model.AccessKeyCreateRequest
import io.plugwerk.api.model.AccessKeyCreateResponse
import io.plugwerk.api.model.AccessKeyDto
import io.plugwerk.server.domain.NamespaceAccessKeyEntity
import io.plugwerk.server.security.NamespaceAuthorizationService
import io.plugwerk.server.service.AccessKeyService
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.net.URI
import java.util.UUID
import io.plugwerk.server.domain.NamespaceRole as DomainRole

@RestController
@RequestMapping("/api/v1")
class AccessKeyController(
    private val accessKeyService: AccessKeyService,
    private val namespaceAuthorizationService: NamespaceAuthorizationService,
) : AccessKeysApi {

    override fun listAccessKeys(ns: String): ResponseEntity<List<AccessKeyDto>> {
        namespaceAuthorizationService.requireRole(
            ns,
            SecurityContextHolder.getContext().authentication!!,
            DomainRole.ADMIN,
        )
        val keys = accessKeyService.listByNamespace(ns).map { it.toDto() }
        return ResponseEntity.ok(keys)
    }

    override fun createAccessKey(
        ns: String,
        accessKeyCreateRequest: AccessKeyCreateRequest,
    ): ResponseEntity<AccessKeyCreateResponse> {
        namespaceAuthorizationService.requireRole(
            ns,
            SecurityContextHolder.getContext().authentication!!,
            DomainRole.ADMIN,
        )
        val (entity, plainKey) = accessKeyService.create(
            namespaceSlug = ns,
            description = accessKeyCreateRequest.description,
            expiresAt = accessKeyCreateRequest.expiresAt,
        )
        val response = AccessKeyCreateResponse(
            id = entity.id!!,
            key = plainKey,
            description = entity.description,
            createdAt = entity.createdAt,
        )
        return ResponseEntity
            .created(URI("/api/v1/namespaces/$ns/access-keys/${entity.id}"))
            .body(response)
    }

    override fun revokeAccessKey(ns: String, keyId: UUID): ResponseEntity<Unit> {
        namespaceAuthorizationService.requireRole(
            ns,
            SecurityContextHolder.getContext().authentication!!,
            DomainRole.ADMIN,
        )
        accessKeyService.revoke(ns, keyId)
        return ResponseEntity.noContent().build()
    }

    private fun NamespaceAccessKeyEntity.toDto() = AccessKeyDto(
        id = id!!,
        description = description,
        keyPrefix = keyHash.take(8),
        revoked = revoked,
        expiresAt = expiresAt,
        createdAt = createdAt,
    )
}
