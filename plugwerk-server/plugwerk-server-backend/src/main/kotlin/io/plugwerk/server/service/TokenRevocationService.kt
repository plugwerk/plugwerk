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
package io.plugwerk.server.service

import com.github.benmanes.caffeine.cache.Caffeine
import io.plugwerk.server.PlugwerkProperties
import io.plugwerk.server.domain.RevokedTokenEntity
import io.plugwerk.server.repository.RevokedTokenRepository
import io.plugwerk.server.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Manages JWT token revocation with database persistence and an in-memory Caffeine cache.
 *
 * Tokens can be revoked individually (logout) or in bulk (password change). The cache
 * avoids a database round-trip on every authenticated request; cache misses fall through
 * to the database.
 *
 * A scheduled cleanup job purges expired revocation entries hourly.
 */
@Service
class TokenRevocationService(
    private val revokedTokenRepository: RevokedTokenRepository,
    private val userRepository: UserRepository,
    props: PlugwerkProperties,
) {

    private val log = LoggerFactory.getLogger(TokenRevocationService::class.java)

    private val revokedJtiCache = Caffeine.newBuilder()
        .maximumSize(10_000)
        .expireAfterWrite(props.auth.accessTokenValidityMinutes, TimeUnit.MINUTES)
        .build<String, Boolean>()

    /**
     * Revokes a single token by its [jti] claim. The token's [expiresAt] is stored so the
     * cleanup job can purge the entry once it would have expired anyway.
     *
     * The raw [jti] is hashed (SHA-256 hex) before being persisted or cached so a
     * database leak exposes only opaque digests, not still-valid JWT IDs (SBS-013 / #268).
     *
     * @param userId The owning [io.plugwerk.server.domain.UserEntity.id]. Stored
     *   on the row as a proper FK (#422) so deleting the user cascades to wipe
     *   their revocation entries.
     */
    @Transactional
    fun revokeToken(jti: String, userId: UUID, expiresAt: Instant) {
        val jtiHash = hashJti(jti)
        if (revokedTokenRepository.existsByJti(jtiHash)) return
        revokedTokenRepository.save(
            RevokedTokenEntity(
                jti = jtiHash,
                userId = userId,
                expiresAt = expiresAt.atOffset(ZoneOffset.UTC),
            ),
        )
        revokedJtiCache.put(jtiHash, true)
        log.debug("Revoked token jtiHash={} for user_id={}", jtiHash, userId)
    }

    /**
     * Checks whether a token identified by [jti] has been revoked, either explicitly
     * or because the user's password was changed after the token was issued.
     *
     * Runs inside a read-only transaction (#486) so the existsByJti + findById
     * pair on a cache miss share one connection. Without this annotation each
     * repository call ran in its own auto-commit unit, doubling JDBC connection
     * acquires for every authenticated request hitting a cold jti.
     *
     * @param jti The JWT ID claim.
     * @param subject The JWT `sub` claim — `plugwerk_user.id` UUID-string after #351.
     * @param issuedAt The `iat` claim of the token.
     * @return `true` if the token must be rejected.
     */
    @Transactional(readOnly = true)
    fun isRevoked(jti: String, subject: String, issuedAt: Instant): Boolean {
        // Check explicit revocation (cache → DB fallback). Both the cache key
        // and the repository lookup use the SHA-256 hash so the raw jti never
        // leaves this method (SBS-013 / #268).
        val jtiHash = hashJti(jti)
        val explicitlyRevoked = revokedJtiCache.get(jtiHash) { revokedTokenRepository.existsByJti(it) }
        if (explicitlyRevoked == true) return true

        // Check bulk invalidation via password change. After #351 the JWT-sub
        // is the plugwerk_user.id UUID; a non-UUID value can only come from a
        // forged or pre-migration token, both of which we want to treat as
        // not-bulk-invalidated (the explicit-revocation path above will still
        // reject forged tokens via JWS verification before this check runs).
        val userId = runCatching { UUID.fromString(subject) }.getOrNull() ?: return false
        val user = userRepository.findById(userId).orElse(null) ?: return false
        val invalidatedBefore = user.passwordInvalidatedBefore ?: return false
        return issuedAt.isBefore(invalidatedBefore.toInstant())
    }

    /**
     * Hex-encoded SHA-256 of the JWT `jti` claim. Always 64 characters.
     * Used as the at-rest representation in the `revoked_token` table and as
     * the Caffeine cache key — see SBS-013 / #268 for rationale.
     */
    private fun hashJti(jti: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(jti.toByteArray(StandardCharsets.UTF_8))
        return buildString(digest.size * 2) {
            digest.forEach { append("%02x".format(it)) }
        }
    }

    /**
     * Invalidates all tokens for the given user by setting `passwordInvalidatedBefore`
     * to the current time. Existing tokens issued before this timestamp will be rejected.
     */
    @Transactional
    fun revokeAllForUser(userId: UUID) {
        val user = userRepository.findById(userId)
            .orElseThrow { EntityNotFoundException("User", userId.toString()) }
        user.passwordInvalidatedBefore = OffsetDateTime.now(ZoneOffset.UTC)
        userRepository.save(user)
        log.info("Invalidated all tokens for user_id={}", userId)
    }

    /**
     * Purges revocation entries whose tokens have already expired. Runs hourly.
     */
    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    fun cleanupExpired() {
        val cutoff = OffsetDateTime.now(ZoneOffset.UTC)
        val deleted = revokedTokenRepository.deleteExpiredBefore(cutoff)
        if (deleted > 0) {
            log.info("Cleaned up {} expired token revocation(s)", deleted)
        }
    }
}
