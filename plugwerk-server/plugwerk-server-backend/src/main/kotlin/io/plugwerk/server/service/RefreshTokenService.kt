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

import io.plugwerk.server.PlugwerkProperties
import io.plugwerk.server.domain.RefreshTokenEntity
import io.plugwerk.server.repository.RefreshTokenRepository
import io.plugwerk.server.security.AccessKeyHmac
import io.plugwerk.server.service.scheduler.SchedulerJobAuditor
import io.plugwerk.server.service.scheduler.SchedulerJobDescriptor
import io.plugwerk.server.service.scheduler.SchedulerJobRegistry
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Lazy
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.SecureRandom
import java.time.Duration
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.Base64
import java.util.UUID

/**
 * Mint, rotate, and revoke refresh tokens backing the httpOnly session cookie
 * (ADR-0027, issue #294). The plaintext token never touches disk — only its
 * HMAC-SHA256 does, via [AccessKeyHmac], following the ADR-0024 pattern.
 *
 * Rotation semantics:
 *   1. On `/api/v1/auth/refresh`, [rotate] is called with the presented plaintext token.
 *   2. If the token maps to an **active** row, the row is revoked with reason `ROTATED`
 *      and a successor is issued; the new plaintext plus the successor's `familyId` and
 *      expiry are returned.
 *   3. If the token maps to a **revoked** row, the whole family is force-revoked with
 *      reason `REUSE_DETECTED` — the classic refresh-token reuse attack response.
 *   4. If the token maps to no row (or an expired one), [RotationResult.Unknown] is
 *      returned — a generic auth failure, indistinguishable from reuse to the caller
 *      so the client's retry logic treats them identically.
 */
@Service
class RefreshTokenService(
    private val refreshTokenRepository: RefreshTokenRepository,
    private val accessKeyHmac: AccessKeyHmac,
    private val props: PlugwerkProperties,
    private val schedulerJobRegistry: SchedulerJobRegistry,
    private val schedulerJobAuditor: SchedulerJobAuditor,
) {

    private val log = LoggerFactory.getLogger(RefreshTokenService::class.java)
    private val secureRandom = SecureRandom()

    /**
     * Lazy self-reference so the run-now lambda invokes the Spring-proxy
     * method rather than the raw `this`. Without it the proxy-bound
     * `@Transactional` + `@SchedulerLock` advice does not fire for
     * admin-triggered runs (issue surfaced in #516 testing).
     */
    @Autowired
    @Lazy
    private lateinit var self: RefreshTokenService

    @PostConstruct
    fun registerScheduledJob() {
        schedulerJobRegistry.register(
            SchedulerJobDescriptor(
                name = "refresh-token-cleanup",
                description = "Purges expired refresh-token rows hourly so reuse-detection " +
                    "keeps working without unbounded table growth.",
                cronExpression = "0 0 * * * *",
                supportsDryRun = false,
                runNowExecutor = { self.cleanupExpired() },
            ),
        )
    }

    /**
     * Issues a new refresh token bound to [userId] (= `plugwerk_user.id`).
     * Used on initial login and as the successor-issuance step inside [rotate]
     * (see [issueInFamily]).
     *
     * @param userId Plugwerk user identifier — direct PK lookup. Caller is
     *   responsible for verifying the user exists; an unknown id results in
     *   a FK-constraint failure on insert.
     * @param upstreamIdToken Raw OIDC `id_token` value to remember on this row,
     *   so [io.plugwerk.server.controller.AuthController.logout] can perform
     *   RP-Initiated Logout against the IdP (#352). `null` for local logins and
     *   for OIDC providers that do not return an ID token.
     * @return [IssuedToken] containing the plaintext token (for the `Set-Cookie` header),
     *   the computed expiry, and the [familyId]/row-id pair. The plaintext is *never*
     *   stored — only `HMAC-SHA256(jwtSecret, plaintext)`.
     */
    @Transactional
    fun issue(userId: UUID, upstreamIdToken: String? = null): IssuedToken =
        issueInFamily(userId, UUID.randomUUID(), upstreamIdToken)

    /**
     * Rotates a presented refresh token. See class-level KDoc for the reuse-detection
     * semantics. Always runs in a single DB transaction so that the revoke-old +
     * issue-new pair is atomic.
     */
    @Transactional
    fun rotate(presentedPlaintext: String): RotationResult {
        val hash = accessKeyHmac.compute(presentedPlaintext)
        val row = refreshTokenRepository.findByTokenLookupHash(hash).orElse(null)
            ?: return RotationResult.Unknown

        val now = OffsetDateTime.now(ZoneOffset.UTC)
        if (row.revokedAt != null) {
            // Reuse attack: the presented token is already revoked. Burn the whole family.
            val revoked = refreshTokenRepository.revokeFamily(row.familyId, REUSE_DETECTED, now)
            log.warn(
                "Refresh-token reuse detected for user_id={} family_id={} — revoked {} row(s)",
                row.userId,
                row.familyId,
                revoked,
            )
            return RotationResult.Reused
        }
        if (row.expiresAt.isBefore(now)) {
            return RotationResult.Unknown
        }

        // Happy path: revoke the presented row, issue its successor in the same family.
        // Carry the upstream ID-token forward so a long-lived OIDC session — many
        // refresh-rotations after the original login — can still hand the IdP a
        // hint at logout time (#352).
        val successor = issueInFamily(row.userId, row.familyId, row.upstreamIdToken)
        row.revokedAt = now
        row.revocationReason = ROTATED
        row.rotatedToId = successor.rowId
        // `row` is a managed entity; Hibernate flushes on transaction commit.
        return RotationResult.Success(
            userId = row.userId,
            issuedToken = successor,
        )
    }

    /**
     * Revokes every active refresh token for the given user. Called from the password-
     * change path (together with `TokenRevocationService.revokeAllForUser`) and admin
     * disable actions.
     */
    @Transactional
    fun revokeAllForUser(userId: UUID, reason: String = LOGOUT) {
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        val revoked = refreshTokenRepository.revokeAllForUser(userId, reason, now)
        if (revoked > 0) {
            log.info("Revoked {} refresh token(s) for user_id={} reason={}", revoked, userId, reason)
        }
    }

    /**
     * Revokes a single refresh token's family (used by logout, where only the cookie is
     * presented and there may still be other devices in the same family — a logout
     * explicitly terminates the session wherever the session cookie was delivered).
     *
     * @return `true` when a matching active row existed (so logout can return 204),
     *   `false` when no active row matched (already logged out elsewhere).
     */
    @Transactional
    fun revokePresentedFamily(presentedPlaintext: String, reason: String = LOGOUT): Boolean {
        val hash = accessKeyHmac.compute(presentedPlaintext)
        val row = refreshTokenRepository.findByTokenLookupHash(hash).orElse(null) ?: return false
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        val revoked = refreshTokenRepository.revokeFamily(row.familyId, reason, now)
        return revoked > 0
    }

    /**
     * Purges fully expired rows every hour. Revoked-but-not-expired rows are retained
     * so reuse-detection can still identify replays of recently-rotated tokens; once
     * they pass [RefreshTokenEntity.expiresAt], reuse detection is moot.
     */
    @Scheduled(cron = "0 0 * * * *")
    @net.javacrumbs.shedlock.spring.annotation.SchedulerLock(
        name = "refresh-token-cleanup",
        lockAtMostFor = "PT15M",
        lockAtLeastFor = "PT30S",
    )
    @Transactional
    fun cleanupExpired() {
        schedulerJobAuditor.gateAndRun("refresh-token-cleanup") {
            val cutoff = OffsetDateTime.now(ZoneOffset.UTC)
            val deleted = refreshTokenRepository.deleteExpiredBefore(cutoff)
            if (deleted > 0) {
                log.info("Cleaned up {} expired refresh token(s)", deleted)
            }
        }
    }

    /**
     * Looks up the upstream OIDC `id_token` recorded for the row backing the given
     * presented refresh-cookie plaintext. Returns `null` for local-login rows, for
     * OIDC providers that never returned an ID token, for unknown plaintexts, and
     * for already-revoked rows (a logout after rotation may legitimately race with
     * the next /auth/refresh — accepting both states keeps the call site simple).
     *
     * Used by [io.plugwerk.server.controller.AuthController.logout] to construct the
     * RP-Initiated Logout `id_token_hint` parameter (#352). Read-only; never mutates
     * the row's revocation state — the caller's `revokePresentedFamily` does that.
     */
    @Transactional(readOnly = true)
    fun findUpstreamIdToken(presentedPlaintext: String): String? {
        val hash = accessKeyHmac.compute(presentedPlaintext)
        return refreshTokenRepository.findByTokenLookupHash(hash).orElse(null)?.upstreamIdToken
    }

    private fun issueInFamily(userId: UUID, familyId: UUID, upstreamIdToken: String? = null): IssuedToken {
        val plaintext = generatePlaintext()
        val hash = accessKeyHmac.compute(plaintext)
        val issuedAt = OffsetDateTime.now(ZoneOffset.UTC)
        val expiresAt = issuedAt.plusHours(props.auth.refreshTokenValidityHours)
        val entity = refreshTokenRepository.save(
            RefreshTokenEntity(
                familyId = familyId,
                userId = userId,
                tokenLookupHash = hash,
                issuedAt = issuedAt,
                expiresAt = expiresAt,
                upstreamIdToken = upstreamIdToken,
            ),
        )
        return IssuedToken(
            plaintext = plaintext,
            expiresAt = expiresAt,
            maxAge = Duration.between(issuedAt, expiresAt),
            familyId = familyId,
            rowId = requireNotNull(entity.id) { "RefreshTokenEntity saved without id" },
        )
    }

    private fun generatePlaintext(): String {
        val bytes = ByteArray(REFRESH_TOKEN_BYTES)
        secureRandom.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    companion object {
        /** 32 bytes → 43-char base64url. Matches industry-standard opaque-token entropy. */
        private const val REFRESH_TOKEN_BYTES = 32

        const val ROTATED = "ROTATED"
        const val LOGOUT = "LOGOUT"
        const val REUSE_DETECTED = "REUSE_DETECTED"
        const val ADMIN_REVOKE = "ADMIN_REVOKE"
    }

    /** Plaintext + metadata needed to set the `Set-Cookie` header and return the response. */
    data class IssuedToken(
        val plaintext: String,
        val expiresAt: OffsetDateTime,
        val maxAge: Duration,
        val familyId: UUID,
        val rowId: UUID,
    )

    /** Outcome of a [rotate] call. */
    sealed interface RotationResult {
        /** Rotation succeeded; caller sets the new cookie and returns a new access token. */
        data class Success(val userId: UUID, val issuedToken: IssuedToken) : RotationResult

        /** Presented token was a known-revoked row — family was force-revoked. */
        data object Reused : RotationResult

        /** Presented token did not match any row (unknown or hard-expired). */
        data object Unknown : RotationResult
    }
}
