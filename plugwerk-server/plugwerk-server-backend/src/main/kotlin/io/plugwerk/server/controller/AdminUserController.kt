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
import io.plugwerk.api.model.AdminPasswordResetResponse
import io.plugwerk.api.model.UserCreateRequest
import io.plugwerk.api.model.UserDto
import io.plugwerk.api.model.UserPagedResponse
import io.plugwerk.api.model.UserUpdateRequest
import io.plugwerk.server.domain.UserEntity
import io.plugwerk.server.domain.UserSource
import io.plugwerk.server.repository.OidcIdentityRepository
import io.plugwerk.server.security.NamespaceAuthorizationService
import io.plugwerk.server.security.currentAuthentication
import io.plugwerk.server.service.UserService
import io.plugwerk.server.service.auth.AdminPasswordResetService
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.net.URI
import java.util.UUID

@RestController
@RequestMapping("/api/v1")
class AdminUserController(
    private val userService: UserService,
    private val namespaceAuthorizationService: NamespaceAuthorizationService,
    private val namespaceMemberRepository: io.plugwerk.server.repository.NamespaceMemberRepository,
    private val oidcIdentityRepository: OidcIdentityRepository,
    private val adminPasswordResetService: AdminPasswordResetService,
) : AdminUsersApi {

    private val log = LoggerFactory.getLogger(AdminUserController::class.java)

    @PreAuthorize("@namespaceAuthorizationService.isCurrentUserSuperadmin()")
    override fun listUsers(enabled: Boolean?, page: Int, size: Int, sort: String): ResponseEntity<UserPagedResponse> {
        val auth = currentAuthentication()
        namespaceAuthorizationService.requireSuperadmin(auth)

        // Sort string is also enforced by the OpenAPI regex pattern, but
        // re-validate here so a hand-crafted request bypassing the generated
        // controller still hits a 400 instead of an opaque Spring exception.
        // The OpenAPI regex restricts the field set; this guard mirrors it
        // close to the call site for grep-ability.
        val pageable = parsePageable(page, size, sort)

        val pageResult = if (enabled != null) {
            userService.findAllByEnabled(enabled, pageable)
        } else {
            userService.findAll(pageable)
        }
        val pageContent = pageResult.content

        // Single batched lookup for the EXTERNAL subset's provider names so
        // the response payload (issue #412) stays one extra SQL query — never
        // N+1 — regardless of how many EXTERNAL users land on the page.
        // Empty subset short-circuits because some JDBC drivers reject an
        // empty `IN (…)` clause.
        val externalUserIds = pageContent.asSequence()
            .filter { it.source == UserSource.EXTERNAL }
            .mapNotNull { it.id }
            .toList()
        val providerNames: Map<UUID, String> = if (externalUserIds.isEmpty()) {
            emptyMap()
        } else {
            oidcIdentityRepository.findProviderNamesForUsers(externalUserIds)
                .associate { it.userId to it.providerName }
        }

        return ResponseEntity.ok(
            UserPagedResponse(
                content = pageContent.map { it.toDto(providerNames) },
                totalElements = pageResult.totalElements,
                page = pageResult.number,
                propertySize = pageResult.size,
                totalPages = pageResult.totalPages,
            ),
        )
    }

    private fun parsePageable(page: Int, size: Int, sort: String): PageRequest {
        // OpenAPI regex `^(username|email|createdAt|lastLoginAt),(asc|desc)$`
        // already enforces the shape — split here is safe. Defensive guard
        // included for direct callers that bypass the generated layer.
        val (field, direction) = sort.split(",", limit = 2)
            .takeIf { it.size == 2 }
            ?: throw ResponseStatusException(
                org.springframework.http.HttpStatus.BAD_REQUEST,
                "Invalid sort parameter: '$sort' (expected 'field,asc|desc')",
            )
        if (field !in ALLOWED_SORT_FIELDS) {
            throw ResponseStatusException(
                org.springframework.http.HttpStatus.BAD_REQUEST,
                "Sort field '$field' is not allowed (allowed: ${ALLOWED_SORT_FIELDS.joinToString()})",
            )
        }
        val springDirection = when (direction.lowercase()) {
            "asc" -> Sort.Direction.ASC

            "desc" -> Sort.Direction.DESC

            else -> throw ResponseStatusException(
                org.springframework.http.HttpStatus.BAD_REQUEST,
                "Sort direction '$direction' is not allowed (allowed: asc, desc)",
            )
        }
        return PageRequest.of(page, size, Sort.by(springDirection, field))
    }

    private companion object {
        private val ALLOWED_SORT_FIELDS = setOf("username", "email", "createdAt", "lastLoginAt")
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

    /**
     * Admin-initiated password reset for a target user (#450).
     *
     * Issues a single-use reset token, emails the admin-initiated reset link,
     * revokes every active session (access tokens + refresh-token family).
     * On SMTP-disabled / mail-failure the link is returned in the response
     * body so the operator can deliver it out-of-band.
     *
     * Bypasses `auth.password_reset_enabled` — admin is an independent trust
     * chain (the `@PreAuthorize` on this method is the gate). Refusal cases
     * (EXTERNAL user, self-reset) surface as HTTP 400 via
     * [io.plugwerk.server.controller.GlobalExceptionHandler].
     */
    @PreAuthorize("@namespaceAuthorizationService.isCurrentUserSuperadmin()")
    override fun adminResetUserPassword(userId: UUID): ResponseEntity<AdminPasswordResetResponse> {
        val auth = currentAuthentication()
        namespaceAuthorizationService.requireSuperadmin(auth)
        val actorId = parseAuthSubjectAsUuid(auth.name)
        val result = adminPasswordResetService.trigger(targetUserId = userId, actorUserId = actorId)
        return ResponseEntity.ok(
            AdminPasswordResetResponse(
                tokenIssued = result.tokenIssued,
                emailSent = result.emailSent,
                expiresAt = result.expiresAt,
                resetUrl = result.resetUrl,
            ),
        )
    }

    /**
     * The JWT principal is `plugwerk_user.id` (UUID) since the identity-hub
     * split (#351 / ADR-0029). Defensive parse: if a future change introduces
     * a non-UUID subject we want to fail loudly rather than silently miscompare
     * against the target row.
     */
    private fun parseAuthSubjectAsUuid(subject: String): UUID = try {
        UUID.fromString(subject)
    } catch (ex: IllegalArgumentException) {
        log.error("Cannot derive caller UUID from auth subject '{}'", subject, ex)
        throw ResponseStatusException(
            org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
            "Cannot derive caller identity from authentication context",
            ex,
        )
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
