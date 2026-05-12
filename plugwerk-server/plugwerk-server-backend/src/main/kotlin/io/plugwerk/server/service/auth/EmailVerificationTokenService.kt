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

import io.plugwerk.server.domain.EmailVerificationTokenEntity
import io.plugwerk.server.domain.UserEntity
import io.plugwerk.server.repository.EmailVerificationTokenRepository
import io.plugwerk.server.service.scheduler.SchedulerJobAuditor
import io.plugwerk.server.service.scheduler.SchedulerJobDescriptor
import io.plugwerk.server.service.scheduler.SchedulerJobRegistry
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
 * Issues and consumes single-use email-verification tokens for the
 * self-registration flow (#420).
 *
 * **Token shape.** A 32-byte secret from [SecureRandom], base64url-encoded
 * without padding (43 characters). The plaintext is included once in the
 * verification email and then discarded; only `SHA-256(rawToken)` hex is
 * persisted in the `email_verification_token` table — same hash-at-rest
 * posture as `namespace_access_key` and `revoked_token`. A DB leak alone
 * cannot grant account access.
 *
 * **Lifecycle.**
 *  1. [issue] — invalidates any existing in-flight tokens for the user
 *     (so a resend supersedes the previous link), generates a new one,
 *     persists the hash with `expires_at = now + TOKEN_TTL`, returns the
 *     raw token to the caller for emailing.
 *  2. [consume] — hashes the inbound token, looks it up, validates that
 *     it is neither expired nor already consumed, marks `consumed_at =
 *     now`, returns the linked user. Throws [InvalidVerificationTokenException]
 *     on every failure mode so the controller can map to a uniform 400.
 *  3. [sweep] — daily @Scheduled job removes rows past their grace window
 *     (expired + consumed > 7 days) so the table doesn't grow unbounded.
 */
@Service
class EmailVerificationTokenService(
    private val repository: EmailVerificationTokenRepository,
    private val schedulerJobRegistry: SchedulerJobRegistry,
    private val schedulerJobAuditor: SchedulerJobAuditor,
) {

    @PostConstruct
    fun registerScheduledJob() {
        schedulerJobRegistry.register(
            SchedulerJobDescriptor(
                name = "email-verification-token-sweep",
                description = "Daily sweep of expired email-verification tokens. " +
                    "Consumed rows linger for a week to support post-incident " +
                    "troubleshooting; unconsumed expired rows are removed immediately.",
                cronExpression = "0 30 3 * * *",
                supportsDryRun = false,
                runNowExecutor = ::sweep,
            ),
        )
    }

    private val log = LoggerFactory.getLogger(EmailVerificationTokenService::class.java)
    private val secureRandom = SecureRandom()

    /**
     * Issues a fresh token for [user] and invalidates any earlier in-flight
     * tokens (resend semantics — only the most recent link should work).
     *
     * @return the raw token string (caller emails it; never log it) and
     *   the absolute expiry timestamp (caller renders into the email
     *   body's "valid until X" line).
     */
    @Transactional
    fun issue(user: UserEntity): IssuedToken {
        val userId = requireNotNull(user.id) { "user.id must be set before issuing a verification token" }

        // Invalidate any previous unconsumed tokens for this user. We mark
        // them consumed (rather than deleting) so the audit trail survives.
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        val previous = repository.findUnconsumedTokensForUser(userId)
        if (previous.isNotEmpty()) {
            previous.forEach { it.consumedAt = now }
            repository.saveAll(previous)
            log.debug("Invalidated {} previous verification token(s) for user {}", previous.size, userId)
        }

        val rawBytes = ByteArray(TOKEN_BYTES)
        secureRandom.nextBytes(rawBytes)
        val rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(rawBytes)
        val expiresAt = now.plus(TOKEN_TTL)
        val entity = EmailVerificationTokenEntity(
            user = user,
            tokenHash = sha256Hex(rawToken),
            expiresAt = expiresAt,
        )
        repository.save(entity)
        // Only log the hash prefix — the raw token must never appear in logs.
        log.info(
            "Issued email verification token for user {} (hash prefix={}, expires at {})",
            userId,
            entity.tokenHash.substring(0, 8),
            expiresAt,
        )
        return IssuedToken(rawToken = rawToken, expiresAt = expiresAt)
    }

    /**
     * Consumes [rawToken] and returns the linked user. Marks the row
     * `consumed_at = now` so the same link cannot verify a second account.
     *
     * @throws InvalidVerificationTokenException if the token is unknown,
     *   already consumed, or past its expiry.
     */
    @Transactional
    fun consume(rawToken: String): UserEntity {
        val hash = sha256Hex(rawToken)
        val entity = repository.findByTokenHash(hash).orElseThrow {
            InvalidVerificationTokenException("Verification token is invalid")
        }
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        if (entity.consumedAt != null) {
            throw InvalidVerificationTokenException("Verification token has already been used")
        }
        if (entity.expiresAt.isBefore(now)) {
            throw InvalidVerificationTokenException("Verification token has expired")
        }
        entity.consumedAt = now
        repository.save(entity)
        return entity.user
    }

    /**
     * Daily sweep: removes rows whose grace window has elapsed. Consumed
     * rows hang around for a week so a support engineer can correlate a
     * "I clicked the link but it said expired" complaint to the actual
     * timestamp; expired-unconsumed rows go straight away.
     */
    @Scheduled(cron = "0 30 3 * * *") // 03:30 server-local daily, off-peak
    @net.javacrumbs.shedlock.spring.annotation.SchedulerLock(
        name = "email-verification-token-sweep",
        lockAtMostFor = "PT15M",
        lockAtLeastFor = "PT1M",
    )
    @Transactional
    fun sweep() {
        schedulerJobAuditor.gateAndRun("email-verification-token-sweep") {
            val cutoff = OffsetDateTime.now(ZoneOffset.UTC).minus(SWEEP_GRACE)
            val removed = repository.deleteByExpiresAtBefore(cutoff)
            if (removed > 0) {
                log.info("Email verification token sweep removed {} expired row(s) older than {}", removed, cutoff)
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

        /** Verification links are valid for 24 hours per the issue spec. */
        val TOKEN_TTL: Duration = Duration.ofHours(24)

        /** Consumed tokens stick around for 7 days for audit before sweep. */
        val SWEEP_GRACE: Duration = Duration.ofDays(7)
    }
}

/** Raw token (only ever in the email body) + absolute expiry timestamp. */
data class IssuedToken(val rawToken: String, val expiresAt: OffsetDateTime)

/** Mapped to HTTP 400 by [io.plugwerk.server.controller.GlobalExceptionHandler]. */
class InvalidVerificationTokenException(message: String) : RuntimeException(message)
