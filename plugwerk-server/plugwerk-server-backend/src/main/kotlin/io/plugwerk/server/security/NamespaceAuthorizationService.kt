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

import io.plugwerk.server.domain.NamespaceEntity
import io.plugwerk.server.domain.NamespaceRole
import io.plugwerk.server.repository.NamespaceMemberRepository
import io.plugwerk.server.repository.NamespaceRepository
import io.plugwerk.server.repository.UserRepository
import io.plugwerk.server.service.ForbiddenException
import io.plugwerk.server.service.NamespaceNotFoundException
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Service
import java.util.UUID

/**
 * Enforces namespace-scoped role-based access control (RBAC).
 *
 * The caller's identity is resolved from the [Authentication] principal's `name` field,
 * which holds the JWT `sub` claim. After the identity-hub split (#351) the `sub` is
 * always the `plugwerk_user.id` UUID — local users and OIDC users alike. Authorization
 * is FK-based against `namespace_member.user_id`, never against a free-text subject.
 *
 * The [NamespaceAccessKeyAuthFilter] sets the principal's name to the namespace slug
 * prefixed with `key:` — access keys have READ_ONLY access to their namespace. They can
 * list, search, and download plugins but cannot perform write operations (upload, delete,
 * approve, manage members/keys). Write operations require a JWT Bearer token.
 *
 * **Superadmin:** A user with [isSuperadmin = true] implicitly holds ADMIN rights in every
 * namespace and does not need a [NamespaceMemberEntity] entry. Use [requireSuperadmin] to
 * gate operations that only the superadmin may perform (e.g. namespace create/delete).
 *
 * **Usage:**
 * ```kotlin
 * namespaceAuthorizationService.requireRole(namespaceSlug, authentication, NamespaceRole.ADMIN)
 * namespaceAuthorizationService.requireSuperadmin(authentication)
 * ```
 * Throws [ForbiddenException] if the subject does not hold the required role (or higher).
 */
@Service
class NamespaceAuthorizationService(
    private val namespaceRepository: NamespaceRepository,
    private val namespaceMemberRepository: NamespaceMemberRepository,
    private val userRepository: UserRepository,
) {

    /**
     * Asserts that the authenticated principal is the superadmin.
     *
     * Access key principals (`key:` prefix) are service accounts and are never superadmin.
     *
     * @throws ForbiddenException if the principal is not the superadmin.
     */
    fun requireSuperadmin(authentication: Authentication) {
        if (isSuperadmin(authentication)) return
        throw ForbiddenException("Superadmin privileges required")
    }

    /**
     * Asserts that the authenticated principal holds at least [minimumRole] in [namespaceSlug].
     *
     * Role hierarchy: ADMIN > MEMBER > READ_ONLY.
     *
     * Superadmin users bypass the member-table check and implicitly hold ADMIN in every namespace.
     *
     * Namespace Access Key principals (name starts with `key:`) have READ_ONLY access to
     * the namespace they were issued for. Write operations require a JWT Bearer token.
     *
     * @throws NamespaceNotFoundException if the namespace does not exist.
     * @throws ForbiddenException if the principal lacks the required role.
     */
    fun requireRole(namespaceSlug: String, authentication: Authentication, minimumRole: NamespaceRole) {
        // Access keys are namespace-scoped and have READ_ONLY access to their namespace
        if (authentication.name.startsWith("key:")) {
            val keyNamespaceSlug = authentication.name.removePrefix("key:")
            if (keyNamespaceSlug != namespaceSlug) {
                throw ForbiddenException(
                    "Access key is not valid for namespace '$namespaceSlug'",
                )
            }
            if (minimumRole != NamespaceRole.READ_ONLY) {
                throw ForbiddenException(
                    "Access keys have read-only access. This operation requires $minimumRole role.",
                )
            }
            return
        }

        // Superadmin has implicit ADMIN in every namespace.
        if (isSuperadmin(authentication)) return

        val userId = parseUserId(authentication)
            ?: throw ForbiddenException("Authentication subject is not a Plugwerk user")
        val namespace = namespaceRepository.findBySlug(namespaceSlug)
            .orElseThrow { NamespaceNotFoundException(namespaceSlug) }

        val allowedRoles = rolesAtOrAbove(minimumRole)
        val hasRole = namespaceMemberRepository.existsByNamespaceIdAndUserIdAndRoleIn(
            namespaceId = namespace.id!!,
            userId = userId,
            roles = allowedRoles,
        )

        if (!hasRole) {
            throw ForbiddenException("Insufficient role in namespace '$namespaceSlug': requires $minimumRole")
        }
    }

    /**
     * Returns `true` if the authenticated principal is the superadmin.
     *
     * Access key principals (`key:` prefix) are service accounts and are never superadmin.
     */
    fun isSuperadmin(authentication: Authentication): Boolean {
        if (authentication.name.startsWith("key:")) return false
        val userId = parseUserId(authentication) ?: return false
        return userRepository.findById(userId).map { it.isSuperadmin }.orElse(false)
    }

    /**
     * SpEL-friendly mirror of [requireRole] for use in `@PreAuthorize` annotations.
     * Reads the current [Authentication] from [SecurityContextHolder] and returns a boolean
     * instead of throwing. Unknown namespaces return `false` so the SpEL pass-through
     * produces a 403; the subsequent service call will re-raise the 404 on the happy path.
     */
    fun hasRole(namespaceSlug: String, minimumRole: NamespaceRole): Boolean =
        currentAuthenticationOrElse(default = false) { auth ->
            try {
                requireRole(namespaceSlug, auth, minimumRole)
                true
            } catch (_: ForbiddenException) {
                false
            } catch (_: NamespaceNotFoundException) {
                false
            }
        }

    /**
     * Overload that accepts the role as a string literal so `@PreAuthorize` SpEL can use
     * `@namespaceAuthorizationService.hasRole(#ns, 'ADMIN')` without a fully-qualified
     * enum reference.
     */
    fun hasRole(namespaceSlug: String, minimumRole: String): Boolean =
        hasRole(namespaceSlug, NamespaceRole.valueOf(minimumRole))

    /**
     * SpEL-friendly mirror of [requireSuperadmin] that reads the current [Authentication]
     * from [SecurityContextHolder] and returns a boolean.
     */
    fun isCurrentUserSuperadmin(): Boolean = currentAuthenticationOrElse(default = false) { auth -> isSuperadmin(auth) }

    /**
     * Returns the namespaces visible to the authenticated principal:
     * - Superadmin: all namespaces
     * - Access key (`key:<slug>`): only the namespace the key was issued for
     * - Regular user: only namespaces where they hold a [NamespaceMemberEntity] entry
     */
    fun listVisibleNamespaces(authentication: Authentication): List<NamespaceEntity> {
        if (isSuperadmin(authentication)) return namespaceRepository.findAll()

        if (authentication.name.startsWith("key:")) {
            val slug = authentication.name.removePrefix("key:")
            return namespaceRepository.findBySlug(slug)
                .map { listOf(it) }.orElse(emptyList())
        }

        val userId = parseUserId(authentication) ?: return emptyList()
        return namespaceMemberRepository.findNamespacesByUserId(userId)
    }

    /**
     * Parses the JWT `sub` claim into a `plugwerk_user.id` UUID. Returns `null` for
     * access-key principals (handled in their own branch by every caller) and for
     * non-UUID subjects (forged or pre-#351 tokens — both should land on the
     * deny-by-default path).
     */
    private fun parseUserId(authentication: Authentication): UUID? {
        if (authentication.name.startsWith("key:")) return null
        return runCatching { UUID.fromString(authentication.name) }.getOrNull()
    }

    private fun rolesAtOrAbove(minimumRole: NamespaceRole): List<NamespaceRole> = when (minimumRole) {
        NamespaceRole.READ_ONLY -> NamespaceRole.entries
        NamespaceRole.MEMBER -> listOf(NamespaceRole.MEMBER, NamespaceRole.ADMIN)
        NamespaceRole.ADMIN -> listOf(NamespaceRole.ADMIN)
    }
}
