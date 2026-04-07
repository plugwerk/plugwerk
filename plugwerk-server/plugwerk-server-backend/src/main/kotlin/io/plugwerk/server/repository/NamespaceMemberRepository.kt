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
package io.plugwerk.server.repository

import io.plugwerk.server.domain.NamespaceEntity
import io.plugwerk.server.domain.NamespaceMemberEntity
import io.plugwerk.server.domain.NamespaceRole
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.Optional
import java.util.UUID

interface NamespaceMemberRepository : JpaRepository<NamespaceMemberEntity, UUID> {

    fun findByNamespaceIdAndUserSubject(namespaceId: UUID, userSubject: String): Optional<NamespaceMemberEntity>

    fun findAllByNamespaceId(namespaceId: UUID): List<NamespaceMemberEntity>

    fun findAllByUserSubject(userSubject: String): List<NamespaceMemberEntity>

    /**
     * Returns the namespaces a user is a member of, eagerly fetching the namespace entity
     * to avoid LazyInitializationException when accessing namespace properties outside a session.
     */
    @Query(
        """
        SELECT m.namespace FROM NamespaceMemberEntity m
        WHERE m.userSubject = :userSubject
        """,
    )
    fun findNamespacesByUserSubject(@Param("userSubject") userSubject: String): List<NamespaceEntity>

    fun existsByNamespaceIdAndUserSubjectAndRoleIn(
        namespaceId: UUID,
        userSubject: String,
        roles: Collection<NamespaceRole>,
    ): Boolean

    fun deleteByNamespaceIdAndUserSubject(namespaceId: UUID, userSubject: String)
}
