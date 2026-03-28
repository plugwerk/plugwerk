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

import io.plugwerk.api.NamespaceMembersApi
import io.plugwerk.api.model.NamespaceMemberCreateRequest
import io.plugwerk.api.model.NamespaceMemberDto
import io.plugwerk.api.model.NamespaceMemberUpdateRequest
import io.plugwerk.api.model.NamespaceMembershipDto
import io.plugwerk.api.model.NamespaceRole
import io.plugwerk.server.domain.NamespaceMemberEntity
import io.plugwerk.server.repository.NamespaceMemberRepository
import io.plugwerk.server.repository.NamespaceRepository
import io.plugwerk.server.security.NamespaceAuthorizationService
import io.plugwerk.server.service.ConflictException
import io.plugwerk.server.service.EntityNotFoundException
import io.plugwerk.server.service.NamespaceNotFoundException
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.net.URI
import io.plugwerk.server.domain.NamespaceRole as DomainRole

@RestController
@RequestMapping("/api/v1")
@Transactional
class NamespaceMemberController(
    private val namespaceRepository: NamespaceRepository,
    private val namespaceMemberRepository: NamespaceMemberRepository,
    private val namespaceAuthorizationService: NamespaceAuthorizationService,
) : NamespaceMembersApi {

    override fun getMyMembership(ns: String): ResponseEntity<NamespaceMembershipDto> {
        val authentication = SecurityContextHolder.getContext().authentication
            ?: return ResponseEntity.status(401).build()
        val namespace = namespaceRepository.findBySlug(ns)
            .orElseThrow { NamespaceNotFoundException(ns) }

        // Superadmin and access keys have implicit ADMIN — no member entry needed
        if (namespaceAuthorizationService.isSuperadmin(authentication) ||
            authentication.name.startsWith("key:")
        ) {
            return ResponseEntity.ok(NamespaceMembershipDto(role = NamespaceRole.ADMIN))
        }

        val member = namespaceMemberRepository.findByNamespaceIdAndUserSubject(
            namespace.id!!,
            authentication.name,
        ).orElseThrow { EntityNotFoundException("NamespaceMember", authentication.name) }
        return ResponseEntity.ok(NamespaceMembershipDto(role = member.role.toDto()))
    }

    override fun listNamespaceMembers(ns: String): ResponseEntity<List<NamespaceMemberDto>> {
        namespaceAuthorizationService.requireRole(
            ns,
            SecurityContextHolder.getContext().authentication!!,
            DomainRole.ADMIN,
        )
        val namespace = namespaceRepository.findBySlug(ns).orElseThrow { NamespaceNotFoundException(ns) }
        val members = namespaceMemberRepository.findAllByNamespaceId(namespace.id!!).map { it.toDto() }
        return ResponseEntity.ok(members)
    }

    override fun addNamespaceMember(
        ns: String,
        namespaceMemberCreateRequest: NamespaceMemberCreateRequest,
    ): ResponseEntity<NamespaceMemberDto> {
        namespaceAuthorizationService.requireRole(
            ns,
            SecurityContextHolder.getContext().authentication!!,
            DomainRole.ADMIN,
        )
        val namespace = namespaceRepository.findBySlug(ns).orElseThrow { NamespaceNotFoundException(ns) }
        val exists = namespaceMemberRepository.findByNamespaceIdAndUserSubject(
            namespace.id!!,
            namespaceMemberCreateRequest.userSubject,
        ).isPresent
        if (exists) {
            throw ConflictException(
                "Subject '${namespaceMemberCreateRequest.userSubject}' already has a role in namespace '$ns'",
            )
        }
        val member = namespaceMemberRepository.save(
            NamespaceMemberEntity(
                namespace = namespace,
                userSubject = namespaceMemberCreateRequest.userSubject,
                role = namespaceMemberCreateRequest.role.toDomain(),
            ),
        )
        return ResponseEntity.created(URI("/api/v1/namespaces/$ns/members/${member.userSubject}")).body(member.toDto())
    }

    override fun updateNamespaceMember(
        ns: String,
        userSubject: String,
        namespaceMemberUpdateRequest: NamespaceMemberUpdateRequest,
    ): ResponseEntity<NamespaceMemberDto> {
        namespaceAuthorizationService.requireRole(
            ns,
            SecurityContextHolder.getContext().authentication!!,
            DomainRole.ADMIN,
        )
        val namespace = namespaceRepository.findBySlug(ns).orElseThrow { NamespaceNotFoundException(ns) }
        val member = namespaceMemberRepository.findByNamespaceIdAndUserSubject(namespace.id!!, userSubject)
            .orElseThrow { EntityNotFoundException("NamespaceMember", userSubject) }
        member.role = namespaceMemberUpdateRequest.role.toDomain()
        return ResponseEntity.ok(namespaceMemberRepository.save(member).toDto())
    }

    override fun removeNamespaceMember(ns: String, userSubject: String): ResponseEntity<Unit> {
        namespaceAuthorizationService.requireRole(
            ns,
            SecurityContextHolder.getContext().authentication!!,
            DomainRole.ADMIN,
        )
        val namespace = namespaceRepository.findBySlug(ns).orElseThrow { NamespaceNotFoundException(ns) }
        if (!namespaceMemberRepository.findByNamespaceIdAndUserSubject(namespace.id!!, userSubject).isPresent) {
            throw EntityNotFoundException("NamespaceMember", userSubject)
        }
        namespaceMemberRepository.deleteByNamespaceIdAndUserSubject(namespace.id!!, userSubject)
        return ResponseEntity.noContent().build()
    }

    private fun NamespaceMemberEntity.toDto() = NamespaceMemberDto(
        userSubject = userSubject,
        role = role.toDto(),
        createdAt = createdAt,
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
