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

import io.plugwerk.api.NamespaceMembersApi
import io.plugwerk.api.model.NamespaceMemberCreateRequest
import io.plugwerk.api.model.NamespaceMemberDto
import io.plugwerk.api.model.NamespaceMemberUpdateRequest
import io.plugwerk.api.model.NamespaceMembershipDto
import io.plugwerk.api.model.NamespaceRole
import io.plugwerk.server.domain.NamespaceMemberEntity
import io.plugwerk.server.domain.UserSource
import io.plugwerk.server.repository.NamespaceMemberRepository
import io.plugwerk.server.repository.NamespaceRepository
import io.plugwerk.server.repository.OidcIdentityRepository
import io.plugwerk.server.repository.UserRepository
import io.plugwerk.server.security.CurrentUserResolver
import io.plugwerk.server.security.NamespaceAuthorizationService
import io.plugwerk.server.security.currentAuthentication
import io.plugwerk.server.service.ConflictException
import io.plugwerk.server.service.EntityNotFoundException
import io.plugwerk.server.service.NamespaceNotFoundException
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.net.URI
import java.util.UUID
import io.plugwerk.api.model.NamespaceMemberDto.Source as MemberSource
import io.plugwerk.server.domain.NamespaceRole as DomainRole

@RestController
@RequestMapping("/api/v1")
@Transactional
class NamespaceMemberController(
    private val namespaceRepository: NamespaceRepository,
    private val namespaceMemberRepository: NamespaceMemberRepository,
    private val userRepository: UserRepository,
    private val oidcIdentityRepository: OidcIdentityRepository,
    private val namespaceAuthorizationService: NamespaceAuthorizationService,
    private val currentUserResolver: CurrentUserResolver,
) : NamespaceMembersApi {

    override fun getMyMembership(ns: String): ResponseEntity<NamespaceMembershipDto> {
        val authentication = currentAuthentication()
        val namespace = namespaceRepository.findBySlug(ns)
            .orElseThrow { NamespaceNotFoundException(ns) }

        // Superadmin has implicit ADMIN — no member entry needed
        if (namespaceAuthorizationService.isSuperadmin(authentication)) {
            return ResponseEntity.ok(NamespaceMembershipDto(role = NamespaceRole.ADMIN))
        }

        // Access keys have READ_ONLY access to their namespace
        if (authentication.name.startsWith("key:")) {
            return ResponseEntity.ok(NamespaceMembershipDto(role = NamespaceRole.READ_ONLY))
        }

        val userId = currentUserResolver.currentUserId()
        val member = namespaceMemberRepository.findByNamespaceIdAndUserId(namespace.id!!, userId)
            .orElseThrow { EntityNotFoundException("NamespaceMember", userId.toString()) }
        return ResponseEntity.ok(NamespaceMembershipDto(role = member.role.toDto()))
    }

    @PreAuthorize("@namespaceAuthorizationService.hasRole(#ns, 'ADMIN')")
    override fun listNamespaceMembers(ns: String): ResponseEntity<List<NamespaceMemberDto>> {
        namespaceAuthorizationService.requireRole(
            ns,
            currentAuthentication(),
            DomainRole.ADMIN,
        )
        val namespace = namespaceRepository.findBySlug(ns).orElseThrow { NamespaceNotFoundException(ns) }
        val rows = namespaceMemberRepository.findAllByNamespaceId(namespace.id!!)
        // Single batched lookup for the EXTERNAL subset's provider names so
        // the members-table response (issue #412) is one extra SQL query —
        // never N+1 — regardless of how many EXTERNAL members the page
        // contains. Empty subset short-circuits because some JDBC drivers
        // reject an empty `IN (…)` clause.
        val externalUserIds = rows.asSequence()
            .filter { it.user.source == UserSource.EXTERNAL }
            .mapNotNull { it.user.id }
            .toList()
        val providerNames: Map<UUID, String> = if (externalUserIds.isEmpty()) {
            emptyMap()
        } else {
            oidcIdentityRepository.findProviderNamesForUsers(externalUserIds)
                .associate { it.userId to it.providerName }
        }
        return ResponseEntity.ok(rows.map { it.toDto(providerNames) })
    }

    @PreAuthorize("@namespaceAuthorizationService.hasRole(#ns, 'ADMIN')")
    override fun addNamespaceMember(
        ns: String,
        namespaceMemberCreateRequest: NamespaceMemberCreateRequest,
    ): ResponseEntity<NamespaceMemberDto> {
        namespaceAuthorizationService.requireRole(
            ns,
            currentAuthentication(),
            DomainRole.ADMIN,
        )
        val namespace = namespaceRepository.findBySlug(ns).orElseThrow { NamespaceNotFoundException(ns) }
        val userId = namespaceMemberCreateRequest.userId
        val user = userRepository.findById(userId)
            .orElseThrow { EntityNotFoundException("User", userId.toString()) }
        val exists = namespaceMemberRepository.findByNamespaceIdAndUserId(namespace.id!!, userId).isPresent
        if (exists) {
            throw ConflictException("User '${user.displayName}' already has a role in namespace '$ns'")
        }
        val member = namespaceMemberRepository.save(
            NamespaceMemberEntity(
                namespace = namespace,
                user = user,
                role = namespaceMemberCreateRequest.role.toDomain(),
            ),
        )
        return ResponseEntity.created(URI("/api/v1/namespaces/$ns/members/${user.id}")).body(member.toDto())
    }

    @PreAuthorize("@namespaceAuthorizationService.hasRole(#ns, 'ADMIN')")
    override fun updateNamespaceMember(
        ns: String,
        userId: UUID,
        namespaceMemberUpdateRequest: NamespaceMemberUpdateRequest,
    ): ResponseEntity<NamespaceMemberDto> {
        namespaceAuthorizationService.requireRole(
            ns,
            currentAuthentication(),
            DomainRole.ADMIN,
        )
        val namespace = namespaceRepository.findBySlug(ns).orElseThrow { NamespaceNotFoundException(ns) }
        val member = namespaceMemberRepository.findByNamespaceIdAndUserId(namespace.id!!, userId)
            .orElseThrow { EntityNotFoundException("NamespaceMember", userId.toString()) }
        member.role = namespaceMemberUpdateRequest.role.toDomain()
        return ResponseEntity.ok(namespaceMemberRepository.save(member).toDto())
    }

    @PreAuthorize("@namespaceAuthorizationService.hasRole(#ns, 'ADMIN')")
    override fun removeNamespaceMember(ns: String, userId: UUID): ResponseEntity<Unit> {
        namespaceAuthorizationService.requireRole(
            ns,
            currentAuthentication(),
            DomainRole.ADMIN,
        )
        val namespace = namespaceRepository.findBySlug(ns).orElseThrow { NamespaceNotFoundException(ns) }
        if (!namespaceMemberRepository.findByNamespaceIdAndUserId(namespace.id!!, userId).isPresent) {
            throw EntityNotFoundException("NamespaceMember", userId.toString())
        }
        namespaceMemberRepository.deleteByNamespaceIdAndUserId(namespace.id!!, userId)
        return ResponseEntity.noContent().build()
    }

    /**
     * Single-row mapper used by add / update responses. Resolves the
     * EXTERNAL user's provider name with one targeted lookup against
     * `oidc_identity` — fine for a single row, would be N+1 across a list
     * (which is why [listNamespaceMembers] uses the batched variant).
     */
    private fun NamespaceMemberEntity.toDto(): NamespaceMemberDto {
        val providerName = if (user.source == UserSource.EXTERNAL) {
            oidcIdentityRepository.findByUserId(requireNotNull(user.id))
                .map { it.oidcProvider.name }
                .orElse(null)
        } else {
            null
        }
        return toDto(providerName)
    }

    /**
     * List-mapper variant. Receives the precomputed `userId → providerName`
     * map built once by [listNamespaceMembers] so the per-row mapping costs
     * zero extra database round-trips.
     */
    private fun NamespaceMemberEntity.toDto(providerNames: Map<UUID, String>): NamespaceMemberDto {
        val providerName = if (user.source == UserSource.EXTERNAL) {
            providerNames[user.id]
        } else {
            null
        }
        return toDto(providerName)
    }

    /**
     * Inner mapper — assembles the DTO from the entity plus the resolved
     * provider name (already filtered to EXTERNAL or null).
     */
    private fun NamespaceMemberEntity.toDto(providerName: String?): NamespaceMemberDto = NamespaceMemberDto(
        userId = requireNotNull(user.id),
        displayName = user.displayName,
        username = user.username,
        source = when (user.source) {
            UserSource.INTERNAL -> MemberSource.INTERNAL
            UserSource.EXTERNAL -> MemberSource.EXTERNAL
        },
        role = role.toDto(),
        createdAt = createdAt,
        providerName = providerName,
    )

    private fun DomainRole.toDto(): NamespaceRole = when (this) {
        DomainRole.ADMIN -> NamespaceRole.ADMIN
        DomainRole.MEMBER -> NamespaceRole.MEMBER
        DomainRole.READ_ONLY -> NamespaceRole.READ_ONLY
    }

    private fun NamespaceRole.toDomain(): DomainRole = when (this) {
        NamespaceRole.ADMIN -> DomainRole.ADMIN
        NamespaceRole.MEMBER -> DomainRole.MEMBER
        NamespaceRole.READ_ONLY -> DomainRole.READ_ONLY
    }
}
