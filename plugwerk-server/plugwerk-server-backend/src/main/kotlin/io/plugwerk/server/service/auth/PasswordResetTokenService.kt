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
package io.plugwerk.server.service.auth

import io.plugwerk.server.domain.PasswordResetTokenEntity
import io.plugwerk.server.domain.UserEntity
import io.plugwerk.server.repository.PasswordResetTokenRepository
import io.plugwerk.server.service.scheduler.SchedulerJobAuditor
import io.plugwerk.server.service.scheduler.SchedulerJobDescriptor
import io.plugwerk.server.service.scheduler.SchedulerJobRegistry
import io.plugwerk.server.service.settings.ApplicationSettingsService
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Duration
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.Base64

/**
 * Issues and consumes single-use password-reset tokens for the opt-in
 * forgot-password flow (#421).
 *
 * Mirrors [EmailVerificationTokenService] (#420) almost line-for-line —
 * same SecureRandom + base64url + SHA-256-hash-at-rest posture, same
 * supersede-previous semantics on issue, same expired/consumed/unknown
 * branches in consume, same daily sweep. The only behavioural difference
 * is that the TTL is **operator-tunable** at runtime (not a code
 * constant): the value is read from `auth.password_reset_token_ttl_minutes`
 * each time `issue` is called, so admins can shrink the window from the
 * settings UI without a redeploy.
 */
@Service
class PasswordResetTokenService(
    private val repository: PasswordResetTokenRepository,
    private val settings: ApplicationSettingsService,
    private val schedulerJobRegistry: SchedulerJobRegistry,
    private val schedulerJobAuditor: SchedulerJobAuditor,
) {
    private val log = LoggerFactory.getLogger(PasswordResetTokenService::class.java)
    private val secureRandom = SecureRandom()

    @PostConstruct
    fun registerScheduledJob() {
        schedulerJobRegistry.register(
            SchedulerJobDescriptor(
                name = "password-reset-token-sweep",
                description = "Daily sweep of expired password-reset tokens. Consumed " +
                    "rows linger for a week so support can correlate user complaints; " +
                    "expired-unconsumed rows are removed immediately.",
                cronExpression = "0 35 3 * * *",
                supportsDryRun = false,
                runNowExecutor = ::sweep,
            ),
        )
    }

    /**
     * Issues a fresh token for [user] and invalidates any earlier in-flight
     * tokens (resend semantics — only the most recent reset link works).
     *
     * @return the raw token string (caller emails it; never log it) and
     *   the absolute expiry timestamp (caller renders into the email
     *   body's "valid until X" line).
     */
    @Transactional
    fun issue(user: UserEntity): IssuedResetToken {
        val userId = requireNotNull(user.id) { "user.id must be set before issuing a password-reset token" }

        // Invalidate any previous unconsumed tokens for this user. Mark
        // them consumed (rather than deleting) so the audit trail
        // survives — same posture as the verification-token service.
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        val previous = repository.findUnconsumedTokensForUser(userId)
        if (previous.isNotEmpty()) {
            previous.forEach { it.consumedAt = now }
            repository.saveAll(previous)
            log.debug("Invalidated {} previous password-reset token(s) for user {}", previous.size, userId)
        }

        val rawBytes = ByteArray(TOKEN_BYTES)
        secureRandom.nextBytes(rawBytes)
        val rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(rawBytes)
        val ttl = Duration.ofMinutes(settings.passwordResetTokenTtlMinutes().toLong())
        val expiresAt = now.plus(ttl)
        val entity = PasswordResetTokenEntity(
            user = user,
            tokenHash = sha256Hex(rawToken),
            expiresAt = expiresAt,
        )
        repository.save(entity)
        // Only log the hash prefix — the raw token must never appear in logs.
        log.info(
            "Issued password-reset token for user {} (hash prefix={}, expires at {})",
            userId,
            entity.tokenHash.substring(0, 8),
            expiresAt,
        )
        return IssuedResetToken(rawToken = rawToken, expiresAt = expiresAt)
    }

    /**
     * Consumes [rawToken] and returns the linked user. Marks the row
     * `consumed_at = now` so the same link cannot reset a second time.
     *
     * @throws InvalidPasswordResetTokenException if the token is unknown,
     *   already consumed, or past its expiry.
     */
    @Transactional
    fun consume(rawToken: String): UserEntity {
        val hash = sha256Hex(rawToken)
        val entity = repository.findByTokenHash(hash).orElseThrow {
            InvalidPasswordResetTokenException("Password-reset token is invalid")
        }
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        if (entity.consumedAt != null) {
            throw InvalidPasswordResetTokenException("Password-reset token has already been used")
        }
        if (entity.expiresAt.isBefore(now)) {
            throw InvalidPasswordResetTokenException("Password-reset token has expired")
        }
        entity.consumedAt = now
        repository.save(entity)
        return entity.user
    }

    /**
     * Daily sweep: removes rows whose grace window has elapsed. Consumed
     * rows hang around for a week so a support engineer can correlate a
     * "the link said expired" complaint to the actual timestamp;
     * expired-unconsumed rows go straight away.
     *
     * Runs five minutes after the email-verification sweep so the two
     * jobs don't fight for the same DB connection on a small instance.
     */
    @Scheduled(cron = "0 35 3 * * *")
    @net.javacrumbs.shedlock.spring.annotation.SchedulerLock(
        name = "password-reset-token-sweep",
        lockAtMostFor = "PT15M",
        lockAtLeastFor = "PT1M",
    )
    @Transactional
    fun sweep() {
        schedulerJobAuditor.gateAndRun("password-reset-token-sweep") {
            val cutoff = OffsetDateTime.now(ZoneOffset.UTC).minus(SWEEP_GRACE)
            val removed = repository.deleteByExpiresAtBefore(cutoff)
            if (removed > 0) {
                log.info("Password-reset token sweep removed {} expired row(s) older than {}", removed, cutoff)
            }
        }
    }

    private fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString(separator = "") { "%02x".format(it) }
    }

    companion object {
        /** 32 bytes ≈ 256 bits of entropy, encoded as 43 base64url chars. */
        const val TOKEN_BYTES = 32

        /** Consumed tokens stick around for 7 days for audit before sweep. */
        val SWEEP_GRACE: Duration = Duration.ofDays(7)
    }
}

/** Raw token (only ever in the email body) + absolute expiry timestamp. */
data class IssuedResetToken(val rawToken: String, val expiresAt: OffsetDateTime)

/** Mapped to HTTP 400 by [io.plugwerk.server.controller.GlobalExceptionHandler]. */
class InvalidPasswordResetTokenException(message: String) : RuntimeException(message)
