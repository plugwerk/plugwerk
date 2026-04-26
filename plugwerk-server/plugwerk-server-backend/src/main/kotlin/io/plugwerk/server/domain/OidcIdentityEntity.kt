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
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.OnDelete
import org.hibernate.annotations.OnDeleteAction
import org.hibernate.annotations.UuidGenerator
import java.time.OffsetDateTime
import java.util.UUID

/**
 * Binding between an upstream OIDC subject and a Plugwerk [UserEntity]
 * (issue #351). Replaces the synthetic `<provider-uuid>:<sub>` username
 * hack from PR #350.
 *
 * **No identity linking.** `UNIQUE(user_id)` enforces a strict 1:1
 * relationship — a Plugwerk user has at most one OIDC identity. The
 * "same human across multiple providers" case produces multiple
 * independent [UserEntity] rows by design (Plugwerk does not detect or
 * merge them; see plan discussion of issue #351).
 *
 * **Provider deletion.** `ON DELETE CASCADE` on `oidc_provider_id` removes
 * this row when its provider is deleted. The owning [UserEntity] row is
 * NOT cascade-deleted to preserve audit history; instead, application
 * code in `OidcProviderService.delete` disables the affected users
 * (Politik C from the design discussion) before the SQL cascade fires.
 *
 * @property id Primary key, UUIDv7.
 * @property oidcProvider Source provider this identity belongs to.
 * @property subject The provider's `sub` claim — provider-local identifier.
 * @property user The Plugwerk identity hub row this OIDC subject maps to.
 *   Stored as `user_id` UUID column with UNIQUE constraint.
 * @property createdAt First-login timestamp.
 * @property lastLoginAt Updated by `OidcIdentityService.upsertOnLogin` on
 *   every subsequent successful OIDC callback. Useful for stale-account
 *   reporting later.
 */
@Entity
@Table(
    name = "oidc_identity",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uq_oidc_identity_provider_subject",
            columnNames = ["oidc_provider_id", "subject"],
        ),
        UniqueConstraint(
            name = "uq_oidc_identity_user",
            columnNames = ["user_id"],
        ),
    ],
)
class OidcIdentityEntity(
    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(name = "id", updatable = false)
    var id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "oidc_provider_id", nullable = false, updatable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    var oidcProvider: OidcProviderEntity,

    @Column(name = "subject", nullable = false, length = 255)
    var subject: String,

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false, unique = true)
    @OnDelete(action = OnDeleteAction.CASCADE)
    var user: UserEntity,

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: OffsetDateTime = OffsetDateTime.now(),

    @Column(name = "last_login_at", nullable = false)
    var lastLoginAt: OffsetDateTime = OffsetDateTime.now(),
)
