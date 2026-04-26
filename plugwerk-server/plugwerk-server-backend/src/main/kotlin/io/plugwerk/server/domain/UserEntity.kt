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
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import org.hibernate.annotations.UuidGenerator
import java.time.OffsetDateTime
import java.util.UUID

/**
 * The Plugwerk identity hub (issue #351).
 *
 * One row per Plugwerk principal — the canonical "person" that everything
 * else (refresh tokens, namespace memberships, JWT subject) keys off via
 * [id]. Two flavours of authentication are supported, discriminated by
 * [source]:
 *
 * - [UserSource.LOCAL] — username + BCrypt password hash live on this row.
 *   Created via the admin UI ([io.plugwerk.server.service.UserService.create]).
 * - [UserSource.OIDC] — credentials live with the upstream provider; the
 *   binding to a specific provider/subject pair is held in [OidcIdentityEntity]
 *   (1:1 via `oidc_identity.user_id`, enforced by `UNIQUE(user_id)` so
 *   identity linking is intentionally impossible).
 *
 * The DB CHECK constraint `chk_plugwerk_user_credentials` enforces that
 * LOCAL rows have `username` + `password_hash`, OIDC rows have neither.
 *
 * **Data model:** Maps to the `plugwerk_user` table. (`user` is a reserved
 * word in PostgreSQL.)
 *
 * @property id Primary key, UUIDv7. Used as JWT `sub`, as the FK target on
 *   `refresh_token.user_id`, `namespace_member.user_id`, and `oidc_identity.user_id`.
 * @property displayName Human-readable label shown in profile and member-list
 *   UI. For OIDC users, populated from the `name` / `preferred_username`
 *   ID-token claim at first login. NOT NULL since #351.
 * @property email Email address. NOT NULL since #351 — operators are required
 *   to configure their IdPs to return `email` in the OIDC scope, and a
 *   migration-time placeholder fills any pre-existing NULL rows. Case-insensitive
 *   uniqueness applies to LOCAL rows only via `uq_plugwerk_user_email_local`.
 * @property source Discriminator — see [UserSource].
 * @property username Login name for LOCAL users; NULL for OIDC. UNIQUE within
 *   LOCAL rows via partial index `uq_plugwerk_user_username_local`.
 * @property passwordHash BCrypt hash for LOCAL users; NULL for OIDC.
 * @property enabled Whether the account is active. OIDC users are auto-disabled
 *   by `OidcProviderService.delete` when their provider goes away — see
 *   issue #351 Provider-Delete-Politik C.
 * @property passwordChangeRequired When `true`, the user must change their
 *   password on next login. Always `false` for OIDC users (they have no
 *   password to change).
 * @property createdAt Creation timestamp.
 * @property updatedAt Last modification timestamp.
 */
@Entity
@Table(name = "plugwerk_user")
class UserEntity(
    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(name = "id", updatable = false)
    var id: UUID? = null,

    @Column(name = "display_name", nullable = false, length = 255)
    var displayName: String,

    // Uniqueness is enforced at the DB level by the partial functional index
    // `uq_plugwerk_user_email_local` on `LOWER(email) WHERE source='LOCAL'`.
    // Setting `unique = true` here would cause Hibernate to generate a
    // redundant case-sensitive constraint that contradicts production semantics.
    @Column(name = "email", nullable = false, length = 255)
    var email: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 10)
    var source: UserSource,

    // Same uniqueness story as email — partial index `uq_plugwerk_user_username_local`
    // enforces it for LOCAL rows only.
    @Column(name = "username", length = 255)
    var username: String? = null,

    @Column(name = "password_hash", length = 255)
    var passwordHash: String? = null,

    @Column(name = "enabled", nullable = false)
    var enabled: Boolean = true,

    @Column(name = "password_change_required", nullable = false)
    var passwordChangeRequired: Boolean = false,

    @Column(name = "is_superadmin", nullable = false)
    var isSuperadmin: Boolean = false,

    @Column(name = "password_invalidated_before")
    var passwordInvalidatedBefore: OffsetDateTime? = null,

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: OffsetDateTime = OffsetDateTime.now(),

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    var updatedAt: OffsetDateTime = OffsetDateTime.now(),
) {
    fun isLocal(): Boolean = source == UserSource.LOCAL
    fun isOidc(): Boolean = source == UserSource.OIDC
}
