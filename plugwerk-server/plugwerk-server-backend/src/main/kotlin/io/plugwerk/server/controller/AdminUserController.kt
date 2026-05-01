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

import io.plugwerk.api.AdminUsersApi
import io.plugwerk.api.model.UserCreateRequest
import io.plugwerk.api.model.UserDto
import io.plugwerk.api.model.UserUpdateRequest
import io.plugwerk.server.domain.UserEntity
import io.plugwerk.server.domain.UserSource
import io.plugwerk.server.repository.OidcIdentityRepository
import io.plugwerk.server.security.NamespaceAuthorizationService
import io.plugwerk.server.security.currentAuthentication
import io.plugwerk.server.service.UserService
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.net.URI
import java.util.UUID

@RestController
@RequestMapping("/api/v1")
class AdminUserController(
    private val userService: UserService,
    private val namespaceAuthorizationService: NamespaceAuthorizationService,
    private val namespaceMemberRepository: io.plugwerk.server.repository.NamespaceMemberRepository,
    private val oidcIdentityRepository: OidcIdentityRepository,
) : AdminUsersApi {

    override fun listUsers(enabled: Boolean?): ResponseEntity<List<UserDto>> {
        val auth = currentAuthentication()
        namespaceAuthorizationService.requireSuperadmin(auth)
        val users = if (enabled != null) userService.findAllByEnabled(enabled) else userService.findAll()
        // Single batched lookup for the EXTERNAL subset's provider names so
        // the response payload (issue #412) stays one extra SQL query — never
        // N+1 — regardless of how many EXTERNAL users land on the page.
        // Empty subset short-circuits because some JDBC drivers reject an
        // empty `IN (…)` clause.
        val externalUserIds = users.asSequence()
            .filter { it.source == UserSource.EXTERNAL }
            .mapNotNull { it.id }
            .toList()
        val providerNames: Map<UUID, String> = if (externalUserIds.isEmpty()) {
            emptyMap()
        } else {
            oidcIdentityRepository.findProviderNamesForUsers(externalUserIds)
                .associate { it.userId to it.providerName }
        }
        return ResponseEntity.ok(users.map { it.toDto(providerNames) })
    }

    @PreAuthorize("@namespaceAuthorizationService.isCurrentUserSuperadmin()")
    override fun createUser(userCreateRequest: UserCreateRequest): ResponseEntity<UserDto> {
        val auth = currentAuthentication()
        namespaceAuthorizationService.requireSuperadmin(auth)
        val user = userService.create(
            username = userCreateRequest.username,
            email = userCreateRequest.email,
            password = userCreateRequest.password,
            displayName = userCreateRequest.displayName,
        )
        return ResponseEntity.created(URI("/api/v1/admin/users/${user.id}")).body(user.toDto())
    }

    @PreAuthorize("@namespaceAuthorizationService.isCurrentUserSuperadmin()")
    override fun updateUser(userId: UUID, userUpdateRequest: UserUpdateRequest): ResponseEntity<UserDto> {
        val auth = currentAuthentication()
        namespaceAuthorizationService.requireSuperadmin(auth)
        var user = userService.findById(userId)
        userUpdateRequest.enabled?.let { user = userService.setEnabled(userId, it) }
        userUpdateRequest.newPassword?.let { user = userService.resetPassword(userId, it) }
        return ResponseEntity.ok(user.toDto())
    }

    @PreAuthorize("@namespaceAuthorizationService.isCurrentUserSuperadmin()")
    override fun deleteUser(userId: UUID): ResponseEntity<Unit> {
        val auth = currentAuthentication()
        namespaceAuthorizationService.requireSuperadmin(auth)
        userService.delete(userId)
        return ResponseEntity.noContent().build()
    }

    /**
     * Single-row mapper used by create/update/delete responses where the
     * caller already knows the provider name is irrelevant or absent —
     * INTERNAL-only paths. EXTERNAL identities never come back through these
     * endpoints (admin-driven creation produces INTERNAL accounts only;
     * `setEnabled` / `resetPassword` likewise apply to INTERNAL flow), so
     * `providerName = null` is correct.
     */
    private fun UserEntity.toDto() = toDto(emptyMap())

    /**
     * List-mapper variant — receives the precomputed
     * `userId → providerName` map produced by the batched
     * [OidcIdentityRepository.findProviderNamesForUsers] call so the
     * per-row mapping costs zero extra database round-trips.
     */
    private fun UserEntity.toDto(providerNames: Map<UUID, String>) = UserDto(
        id = id!!,
        displayName = displayName,
        source = when (source) {
            UserSource.INTERNAL -> UserDto.Source.INTERNAL
            UserSource.EXTERNAL -> UserDto.Source.EXTERNAL
        },
        username = username,
        email = email,
        enabled = enabled,
        passwordChangeRequired = passwordChangeRequired,
        isSuperadmin = isSuperadmin,
        createdAt = createdAt,
        lastLoginAt = lastLoginAt,
        providerName = if (source == UserSource.EXTERNAL) providerNames[id] else null,
        namespaceMembershipCount = namespaceMemberRepository.countByUserId(id!!).toInt(),
    )
}
