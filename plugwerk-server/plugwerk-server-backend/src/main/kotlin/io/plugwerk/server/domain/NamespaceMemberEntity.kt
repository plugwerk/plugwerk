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
package io.plugwerk.server.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.OnDelete
import org.hibernate.annotations.OnDeleteAction
import org.hibernate.annotations.UuidGenerator
import java.time.OffsetDateTime
import java.util.UUID

/**
 * Namespace-scoped role that a Plugwerk user holds.
 *
 * - [ADMIN]: Full write access within the namespace — upload releases, manage access keys,
 *   approve reviews, manage namespace members.
 * - [MEMBER]: Read-write access — upload releases, view review queue. Cannot manage members
 *   or access keys.
 * - [READ_ONLY]: Read access only — can browse the catalog and download artifacts.
 */
enum class NamespaceRole {
    ADMIN,
    MEMBER,
    READ_ONLY,
}

/**
 * JPA entity that assigns a [NamespaceRole] to a Plugwerk user within a [NamespaceEntity].
 *
 * Pre-#351 this used a free-text `user_subject` string that worked for both local users
 * (their username) and the synthetic `<provider-uuid>:<sub>` for OIDC identities. The
 * identity-hub refactor (issue #351, migration 0017) replaced that with a proper
 * `user_id` FK on `plugwerk_user(id)` — authorization is now PK-based, identity
 * resolution lives in the auth layer (CurrentUserResolver), and OIDC subjects no
 * longer leak into membership rows.
 *
 * **Data model:** Maps to the `namespace_member` table.
 * Unique constraint on `(namespace_id, user_id)` — one role per user per namespace.
 *
 * @property id Primary key, UUIDv7.
 * @property namespace The namespace this membership belongs to.
 * @property user The Plugwerk user holding this role.
 * @property role The role granted to the user within this namespace.
 * @property createdAt Creation timestamp (set automatically, immutable).
 */
@Entity
@Table(
    name = "namespace_member",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uq_namespace_member_ns_user",
            columnNames = ["namespace_id", "user_id"],
        ),
    ],
)
class NamespaceMemberEntity(
    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(name = "id", updatable = false)
    var id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "namespace_id", nullable = false, updatable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    var namespace: NamespaceEntity,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    var user: UserEntity,

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 50)
    var role: NamespaceRole,

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: OffsetDateTime = OffsetDateTime.now(),
)
