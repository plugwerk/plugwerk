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

import io.plugwerk.server.domain.RefreshTokenEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.OffsetDateTime
import java.util.Optional
import java.util.UUID

interface RefreshTokenRepository : JpaRepository<RefreshTokenEntity, UUID> {

    /**
     * Constant-time lookup by HMAC of the plaintext refresh token. The unique index on
     * `token_lookup_hash` makes hit and miss statistically equivalent — the same pattern
     * applied to access keys in ADR-0024.
     *
     * Returns the entity regardless of [RefreshTokenEntity.revokedAt]; callers must check
     * revocation explicitly so reuse-detection can distinguish a replayed revoked token
     * from an unknown one.
     */
    fun findByTokenLookupHash(tokenLookupHash: String): Optional<RefreshTokenEntity>

    /**
     * Force-revokes every row in a family with the given [reason]. Used for
     * reuse-detection (`REUSE_DETECTED`), logout (`LOGOUT`), and admin revocation paths.
     *
     * Idempotent: already-revoked rows retain their original [revokedAt] (the `WHERE` clause
     * filters them out) so the original reason is preserved for forensics.
     */
    @Modifying(clearAutomatically = true)
    @Query(
        """
        UPDATE RefreshTokenEntity t
        SET t.revokedAt = :revokedAt,
            t.revocationReason = :reason
        WHERE t.familyId = :familyId AND t.revokedAt IS NULL
        """,
    )
    fun revokeFamily(
        @Param("familyId") familyId: UUID,
        @Param("reason") reason: String,
        @Param("revokedAt") revokedAt: OffsetDateTime,
    ): Int

    /**
     * Revokes every active refresh token for a user. Called from password-change and
     * admin-disable paths alongside `TokenRevocationService.revokeAllForUser`.
     */
    @Modifying(clearAutomatically = true)
    @Query(
        """
        UPDATE RefreshTokenEntity t
        SET t.revokedAt = :revokedAt,
            t.revocationReason = :reason
        WHERE t.userId = :userId AND t.revokedAt IS NULL
        """,
    )
    fun revokeAllForUser(
        @Param("userId") userId: UUID,
        @Param("reason") reason: String,
        @Param("revokedAt") revokedAt: OffsetDateTime,
    ): Int

    /**
     * Purges expired rows whose revocation (if any) is also in the past. Called hourly by
     * the scheduled cleanup job — mirrors `RevokedTokenRepository.deleteExpiredBefore`.
     */
    @Modifying
    @Query("DELETE FROM RefreshTokenEntity t WHERE t.expiresAt < :cutoff")
    fun deleteExpiredBefore(cutoff: OffsetDateTime): Int
}
