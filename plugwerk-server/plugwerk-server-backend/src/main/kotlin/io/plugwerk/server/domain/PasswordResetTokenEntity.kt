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
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UuidGenerator
import java.time.OffsetDateTime
import java.util.UUID

/**
 * One row per issued self-service password-reset token (#421).
 *
 * Storage and lifecycle mirror [EmailVerificationTokenEntity] (#420):
 * the raw token only ever lives in the reset-link email plus the inbound
 * POST body on `/auth/reset-password`; only `SHA-256(rawToken)` hex is
 * persisted here, so a database leak alone cannot impersonate a user.
 *
 * `consumed_at` flips when the user successfully exchanges the token for
 * a new password; the row sticks around for audit until
 * [io.plugwerk.server.service.auth.PasswordResetTokenService]'s scheduled
 * sweep removes it after the grace window.
 */
@Entity
@Table(
    name = "password_reset_token",
    uniqueConstraints = [
        UniqueConstraint(name = "uq_password_reset_token_hash", columnNames = ["token_hash"]),
    ],
    indexes = [
        Index(name = "idx_password_reset_token_user", columnList = "user_id"),
        Index(name = "idx_password_reset_token_expires", columnList = "expires_at"),
    ],
)
class PasswordResetTokenEntity(
    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(name = "id", updatable = false)
    var id: UUID? = null,

    // EAGER fetch: the reset-password controller reads `user` outside the
    // service's transaction (to apply the new password via UserService),
    // so LAZY would blow up with LazyInitializationException — same
    // reasoning as EmailVerificationTokenEntity.
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    var user: UserEntity,

    @Column(name = "token_hash", nullable = false, length = 64, updatable = false)
    var tokenHash: String,

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: OffsetDateTime = OffsetDateTime.now(),

    @Column(name = "expires_at", nullable = false)
    var expiresAt: OffsetDateTime,

    @Column(name = "consumed_at", nullable = true)
    var consumedAt: OffsetDateTime? = null,
)
