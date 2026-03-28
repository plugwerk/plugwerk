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

import io.plugwerk.api.AdminUsersApi
import io.plugwerk.api.model.UserCreateRequest
import io.plugwerk.api.model.UserDto
import io.plugwerk.api.model.UserUpdateRequest
import io.plugwerk.server.security.NamespaceAuthorizationService
import io.plugwerk.server.service.UnauthorizedException
import io.plugwerk.server.service.UserService
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.net.URI
import java.util.UUID

@RestController
@RequestMapping("/api/v1")
class AdminUserController(
    private val userService: UserService,
    private val namespaceAuthorizationService: NamespaceAuthorizationService,
) : AdminUsersApi {

    override fun listUsers(): ResponseEntity<List<UserDto>> {
        val auth = SecurityContextHolder.getContext().authentication
            ?: throw UnauthorizedException("Not authenticated")
        namespaceAuthorizationService.requireSuperadmin(auth)
        return ResponseEntity.ok(userService.findAll().map { it.toDto() })
    }

    override fun createUser(userCreateRequest: UserCreateRequest): ResponseEntity<UserDto> {
        val auth = SecurityContextHolder.getContext().authentication
            ?: throw UnauthorizedException("Not authenticated")
        namespaceAuthorizationService.requireSuperadmin(auth)
        val user = userService.create(
            username = userCreateRequest.username,
            email = userCreateRequest.email,
            password = userCreateRequest.password,
        )
        return ResponseEntity.created(URI("/api/v1/admin/users/${user.id}")).body(user.toDto())
    }

    override fun updateUser(userId: UUID, userUpdateRequest: UserUpdateRequest): ResponseEntity<UserDto> {
        val auth = SecurityContextHolder.getContext().authentication
            ?: throw UnauthorizedException("Not authenticated")
        namespaceAuthorizationService.requireSuperadmin(auth)
        var user = userService.findById(userId)
        userUpdateRequest.enabled?.let { user = userService.setEnabled(userId, it) }
        userUpdateRequest.newPassword?.let { user = userService.resetPassword(userId, it) }
        return ResponseEntity.ok(user.toDto())
    }

    override fun deleteUser(userId: UUID): ResponseEntity<Unit> {
        val auth = SecurityContextHolder.getContext().authentication
            ?: throw UnauthorizedException("Not authenticated")
        namespaceAuthorizationService.requireSuperadmin(auth)
        userService.delete(userId)
        return ResponseEntity.noContent().build()
    }

    private fun io.plugwerk.server.domain.UserEntity.toDto() = UserDto(
        id = id!!,
        username = username,
        email = email,
        enabled = enabled,
        passwordChangeRequired = passwordChangeRequired,
        isSuperadmin = isSuperadmin,
        createdAt = createdAt,
    )
}
