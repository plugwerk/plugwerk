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
import org.hibernate.annotations.UuidGenerator
import java.time.OffsetDateTime
import java.util.UUID

/**
 * Namespace-scoped role that a subject (local user or OIDC identity) holds.
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
 * JPA entity that assigns a [NamespaceRole] to a subject within a [NamespaceEntity].
 *
 * The [userSubject] field is a portable identity key that works for both local users
 * (their [UserEntity.username]) and OIDC identities (their `sub` claim). This design
 * avoids a foreign-key dependency on [UserEntity], so external identities can hold
 * namespace roles without having a local user record.
 *
 * **Data model:** Maps to the `namespace_member` table.
 * Unique constraint on `(namespace_id, user_subject)` — one role per subject per namespace.
 *
 * @property id Primary key, UUIDv7.
 * @property namespace The namespace this membership belongs to.
 * @property userSubject Stable identity key: local username or OIDC `sub` claim.
 * @property role The role granted to the subject within this namespace.
 * @property createdAt Creation timestamp (set automatically, immutable).
 */
@Entity
@Table(
    name = "namespace_member",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uq_namespace_member_ns_subject",
            columnNames = ["namespace_id", "user_subject"],
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
    var namespace: NamespaceEntity,

    @Column(name = "user_subject", nullable = false)
    var userSubject: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 50)
    var role: NamespaceRole,

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: OffsetDateTime = OffsetDateTime.now(),
)
