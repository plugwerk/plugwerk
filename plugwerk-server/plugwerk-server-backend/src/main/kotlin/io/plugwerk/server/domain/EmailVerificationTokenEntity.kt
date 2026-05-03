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
 * One row per issued self-registration verification token (#420).
 *
 * The raw token is never stored — `tokenHash` holds SHA-256(rawToken)
 * hex (64 characters), mirroring `namespace_access_key` / `revoked_token`
 * so a database leak alone cannot grant account access. The raw token
 * lives only in the verification email + the inbound `?token=…` query
 * parameter on the verify-email endpoint.
 *
 * `consumed_at` flips when the user clicks the link; the row sticks
 * around for audit until [io.plugwerk.server.service.auth.EmailVerificationTokenService]'s
 * scheduled sweep removes expired+consumed rows after a grace period.
 */
@Entity
@Table(
    name = "email_verification_token",
    uniqueConstraints = [
        UniqueConstraint(name = "uq_email_verification_token_hash", columnNames = ["token_hash"]),
    ],
    indexes = [
        Index(name = "idx_email_verification_token_user", columnList = "user_id"),
        Index(name = "idx_email_verification_token_expires", columnList = "expires_at"),
    ],
)
class EmailVerificationTokenEntity(
    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(name = "id", updatable = false)
    var id: UUID? = null,

    // EAGER fetch: every consumer of this entity needs the linked user
    // (the verify-email controller flips user.enabled, the service emails
    // user.email). LAZY would force the caller to stay inside the service
    // transaction, which the controller does not — and produced a
    // LazyInitializationException in the verify-email path.
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
