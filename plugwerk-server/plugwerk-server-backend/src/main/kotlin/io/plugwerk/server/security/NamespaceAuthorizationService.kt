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

import io.plugwerk.server.domain.NamespaceRole
import io.plugwerk.server.repository.NamespaceMemberRepository
import io.plugwerk.server.repository.NamespaceRepository
import io.plugwerk.server.repository.UserRepository
import io.plugwerk.server.service.ForbiddenException
import io.plugwerk.server.service.NamespaceNotFoundException
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Service

/**
 * Enforces namespace-scoped role-based access control (RBAC).
 *
 * The caller's identity ([userSubject]) is extracted from the [Authentication] principal's
 * `name` field, which holds the JWT `sub` claim. For locally issued tokens this equals the
 * username; for OIDC tokens this is the provider's `sub` claim.
 *
 * The [namespace_access_key] auth filter sets the principal's name to the namespace slug
 * prefixed with `key:` — access keys are treated as implicit ADMIN for their namespace and
 * bypass the member table check (they are already namespace-scoped by design).
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
        if (isSuperadmin(authentication.name)) return
        throw ForbiddenException("Superadmin privileges required")
    }

    /**
     * Asserts that the authenticated principal holds at least [minimumRole] in [namespaceSlug].
     *
     * Role hierarchy: ADMIN > MEMBER > READ_ONLY.
     *
     * Superadmin users bypass the member-table check and implicitly hold ADMIN in every namespace.
     *
     * Namespace Access Key principals (name starts with `key:`) are treated as ADMIN for
     * the namespace they were issued for — the filter already validated namespace scope.
     *
     * @throws NamespaceNotFoundException if the namespace does not exist.
     * @throws ForbiddenException if the principal lacks the required role.
     */
    fun requireRole(namespaceSlug: String, authentication: Authentication, minimumRole: NamespaceRole) {
        // Access keys are namespace-scoped and implicitly ADMIN
        if (authentication.name.startsWith("key:")) return

        // Superadmin has implicit ADMIN in every namespace
        if (isSuperadmin(authentication.name)) return

        val namespace = namespaceRepository.findBySlug(namespaceSlug)
            .orElseThrow { NamespaceNotFoundException(namespaceSlug) }

        val allowedRoles = rolesAtOrAbove(minimumRole)
        val hasRole = namespaceMemberRepository.existsByNamespaceIdAndUserSubjectAndRoleIn(
            namespaceId = namespace.id!!,
            userSubject = authentication.name,
            roles = allowedRoles,
        )

        if (!hasRole) {
            throw ForbiddenException("Insufficient role in namespace '$namespaceSlug': requires $minimumRole")
        }
    }

    private fun isSuperadmin(username: String): Boolean =
        userRepository.findByUsername(username).map { it.isSuperadmin }.orElse(false)

    private fun rolesAtOrAbove(minimumRole: NamespaceRole): List<NamespaceRole> = when (minimumRole) {
        NamespaceRole.READ_ONLY -> NamespaceRole.entries
        NamespaceRole.MEMBER -> listOf(NamespaceRole.MEMBER, NamespaceRole.ADMIN)
        NamespaceRole.ADMIN -> listOf(NamespaceRole.ADMIN)
    }
}
