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

import io.plugwerk.server.domain.PasswordResetTokenEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime
import java.util.Optional
import java.util.UUID

interface PasswordResetTokenRepository : JpaRepository<PasswordResetTokenEntity, UUID> {
    fun findByTokenHash(tokenHash: String): Optional<PasswordResetTokenEntity>

    /**
     * "Any in-flight token for user X" — used by the issue path to
     * supersede previous tokens before generating a fresh one. A user
     * who clicks "forgot password" twice should land on the most-recent
     * link only; the older one stops working immediately.
     *
     * Explicit JPQL (rather than the derived `findByUser_Id…` method name)
     * so the function signature stays valid Kotlin camelCase under ktlint.
     */
    @Query("SELECT t FROM PasswordResetTokenEntity t WHERE t.user.id = :userId AND t.consumedAt IS NULL")
    fun findUnconsumedTokensForUser(@Param("userId") userId: UUID): List<PasswordResetTokenEntity>

    /**
     * Sweep target: rows whose `expires_at` has passed. The
     * @Scheduled job in [io.plugwerk.server.service.auth.PasswordResetTokenService]
     * runs daily so the table doesn't grow unbounded.
     */
    @Transactional
    fun deleteByExpiresAtBefore(cutoff: OffsetDateTime): Long
}
