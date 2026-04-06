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
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
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
        .expireAfterWrite(props.auth.tokenValidityHours, TimeUnit.HOURS)
        .build<String, Boolean>()

    /**
     * Revokes a single token by its [jti] claim. The token's [expiresAt] is stored so the
     * cleanup job can purge the entry once it would have expired anyway.
     */
    @Transactional
    fun revokeToken(jti: String, username: String, expiresAt: Instant) {
        if (revokedTokenRepository.existsByJti(jti)) return
        revokedTokenRepository.save(
            RevokedTokenEntity(
                jti = jti,
                username = username,
                expiresAt = expiresAt.atOffset(ZoneOffset.UTC),
            ),
        )
        revokedJtiCache.put(jti, true)
        log.debug("Revoked token jti={} for user={}", jti, username)
    }

    /**
     * Checks whether a token identified by [jti] has been revoked, either explicitly
     * or because the user's password was changed after the token was issued.
     *
     * @param jti The JWT ID claim.
     * @param username The token subject.
     * @param issuedAt The `iat` claim of the token.
     * @return `true` if the token must be rejected.
     */
    fun isRevoked(jti: String, username: String, issuedAt: Instant): Boolean {
        // Check explicit revocation (cache → DB fallback)
        val explicitlyRevoked = revokedJtiCache.get(jti) { revokedTokenRepository.existsByJti(it) }
        if (explicitlyRevoked == true) return true

        // Check bulk invalidation via password change
        val user = userRepository.findByUsername(username).orElse(null) ?: return false
        val invalidatedBefore = user.passwordInvalidatedBefore ?: return false
        return issuedAt.isBefore(invalidatedBefore.toInstant())
    }

    /**
     * Invalidates all tokens for the given [username] by setting `passwordInvalidatedBefore`
     * to the current time. Existing tokens issued before this timestamp will be rejected.
     */
    @Transactional
    fun revokeAllForUser(username: String) {
        val user = userRepository.findByUsername(username)
            .orElseThrow { EntityNotFoundException("User", username) }
        user.passwordInvalidatedBefore = OffsetDateTime.now(ZoneOffset.UTC)
        userRepository.save(user)
        log.info("Invalidated all tokens for user={}", username)
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
