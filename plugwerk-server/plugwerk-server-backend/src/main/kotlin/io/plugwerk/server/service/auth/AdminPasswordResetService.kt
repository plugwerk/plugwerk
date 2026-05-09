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

import io.plugwerk.server.PlugwerkProperties
import io.plugwerk.server.repository.UserRepository
import io.plugwerk.server.service.EntityNotFoundException
import io.plugwerk.server.service.RefreshTokenService
import io.plugwerk.server.service.TokenRevocationService
import io.plugwerk.server.service.mail.MailService
import io.plugwerk.server.service.mail.MailTemplate
import io.plugwerk.server.service.settings.ApplicationSettingsService
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime
import java.util.UUID

/**
 * Drives the superadmin-triggered password reset for a target user (#450).
 *
 * Side effects on every successful invocation:
 *  1. A fresh single-use token is issued via [PasswordResetTokenService.issue]
 *     (supersedes any previous unconsumed token for the same user).
 *  2. `passwordChangeRequired = true` on the target row so that — even in
 *     the SMTP-disabled fallback where the user might still know their old
 *     password — the next login is funneled through `/change-password` by
 *     `PasswordChangeRequiredFilter`.
 *  3. **Both** revocation atoms run: `passwordInvalidatedBefore` (access-token
 *     side, via [TokenRevocationService]) **and** the refresh-token family
 *     (via [RefreshTokenService.revokeAllForUser]). The dual revocation is
 *     mandatory — `AuthController.refresh` does not consult
 *     `passwordInvalidatedBefore`, verified for #421 in PR #448, so a stolen
 *     refresh cookie would otherwise keep minting fresh access tokens after
 *     the reset.
 *  4. The admin-initiated mail template (`AUTH_ADMIN_PASSWORD_RESET`) is
 *     dispatched via [MailService].
 *
 * Refusal cases:
 *  - target user is EXTERNAL — credentials live with the upstream OIDC
 *    provider; raises [IllegalArgumentException] which `GlobalExceptionHandler`
 *    maps to HTTP 400.
 *  - target user is the caller — superadmins must use `/auth/change-password`
 *    for self-service password changes. Same 400 mapping.
 *
 * SMTP-disabled / mail-send-fail behaviour: server-side state changes
 * (token, session revocation, flag) **always succeed**. Only the mail step
 * is best-effort: on `Disabled` / `Misconfigured` / `Failed` the call returns
 * 200 with `emailSent=false` and the absolute reset URL inlined into the
 * response so the operator can deliver the link out-of-band (Slack, phone,
 * in-person). The link is intentionally **not** logged on the wire — the
 * admin sees it once on the API response and that is the only ambient copy
 * outside the mail provider's relay.
 *
 * Audit: the action is recorded structurally via SLF4J + MDC
 * (`actor`, `target`, `kind=ADMIN_PASSWORD_RESET`, `smtpDelivered`). A
 * persistent audit-log table is intentionally out of scope for this issue —
 * see follow-up issue for the dedicated infrastructure.
 *
 * Bypasses the `auth.password_reset_enabled` operator gate that protects
 * the public forgot-password flow — the admin-driven path is an
 * independent trust chain (the caller is already authenticated as a
 * superadmin via `@PreAuthorize` on `AdminUserController`).
 */
@Service
class AdminPasswordResetService(
    private val userRepository: UserRepository,
    private val tokenService: PasswordResetTokenService,
    private val tokenRevocationService: TokenRevocationService,
    private val refreshTokenService: RefreshTokenService,
    private val mailService: MailService,
    private val settings: ApplicationSettingsService,
    private val plugwerkProperties: PlugwerkProperties,
) {
    private val log = LoggerFactory.getLogger(AdminPasswordResetService::class.java)

    /**
     * Executes the admin-driven reset for [targetUserId] on behalf of
     * [actorUserId] (typically the superadmin currently authenticated).
     *
     * @throws SelfResetNotAllowedException when [targetUserId] equals
     *   [actorUserId] — superadmins must use `/auth/change-password`.
     * @throws ExternalUserResetNotAllowedException when the target row is
     *   `source = EXTERNAL` — credentials are managed upstream.
     * @throws EntityNotFoundException when no user matches [targetUserId].
     */
    @Transactional
    fun trigger(targetUserId: UUID, actorUserId: UUID): Result {
        if (targetUserId == actorUserId) {
            throw SelfResetNotAllowedException(
                "Use Profile → Change password to update your own password.",
            )
        }

        val user = userRepository.findById(targetUserId).orElseThrow {
            EntityNotFoundException("User", targetUserId.toString())
        }

        if (user.isExternal()) {
            throw ExternalUserResetNotAllowedException(
                "Cannot reset password on OIDC users — credentials live with the upstream provider.",
            )
        }

        // Server-side state changes — always succeed regardless of SMTP status.
        //
        // Ordering matters because `RefreshTokenRepository.revokeAllForUser`
        // is annotated `@Modifying(clearAutomatically = true)`: Hibernate
        // clears the persistence context AFTER the bulk UPDATE without
        // flushing any pending entity changes first (`flushAutomatically=false`
        // is the Spring Data default, and Hibernate's auto-flush only fires
        // for queries whose target entity-type overlaps with pending changes
        // — a bulk update on `RefreshTokenEntity` does not overlap with
        // pending inserts on `PasswordResetTokenEntity` or pending updates
        // on `UserEntity`).
        //
        // Anything that mutates the persistence context BEFORE this bulk
        // query — issuing the new password-reset token, flipping
        // `passwordChangeRequired`, bumping `passwordInvalidatedBefore` — is
        // silently discarded on the PC clear. The most user-visible
        // symptom: an unflushed `PasswordResetTokenEntity` insert never
        // reaches the DB, so the emailed reset link looks up against an
        // empty hash and the user gets "Password-reset token is invalid"
        // on submit.
        //
        // The fix is to run the bulk @Modifying query FIRST, then re-fetch
        // and apply all entity-state changes — those land naturally on the
        // commit-time flush.

        // 1. Bulk refresh-token revocation FIRST (clears PC; nothing to lose).
        refreshTokenService.revokeAllForUser(targetUserId, RefreshTokenService.LOGOUT)

        // 2. Re-fetch the user — `user` was detached by the PC clear above.
        val freshUser = userRepository.findById(targetUserId).orElseThrow {
            EntityNotFoundException("User", targetUserId.toString())
        }

        // 3. Issue the single-use reset token. Insert lives in the PC and is
        //    flushed at commit (no further bulk queries follow that could
        //    discard it).
        val issued = tokenService.issue(freshUser)

        // 4. Force the next login through /change-password as defence-in-depth
        //    for the SMTP-disabled path: even if the user still knows their
        //    old password and tries to log in normally, PasswordChangeRequiredFilter
        //    funnels them to a password change.
        freshUser.passwordChangeRequired = true
        userRepository.save(freshUser)

        // 5. Access-token revocation. `tokenRevocationService.revokeAllForUser`
        //    re-fetches the user (returns the same managed instance from PC
        //    in this transaction), sets `passwordInvalidatedBefore`, saves.
        //    Both field updates accumulate on the same managed entity and
        //    Hibernate emits a single UPDATE on commit.
        tokenRevocationService.revokeAllForUser(targetUserId)

        // 6. Dispatch mail (best-effort).
        val resetUrl = buildResetLink(issued.rawToken)
        val expiresAtHuman = ExpiryFormatter.formatHuman(issued.expiresAt)
        val sendOutcome = runCatching {
            mailService.sendMailFromTemplate(
                template = MailTemplate.AUTH_ADMIN_PASSWORD_RESET,
                to = freshUser.email,
                vars = mapOf(
                    "username" to freshUser.displayName,
                    "resetLink" to resetUrl,
                    "expiresAtHuman" to expiresAtHuman,
                    "siteName" to settings.siteName(),
                ),
            )
        }.getOrElse { ex ->
            log.error(
                "ADMIN_PASSWORD_RESET: mail send threw for user {} — degrading to out-of-band response",
                targetUserId,
                ex,
            )
            MailService.SendResult.Failed(ex.message ?: ex::class.simpleName ?: "mail send threw")
        }

        val emailSent = sendOutcome is MailService.SendResult.Sent
        if (!emailSent) {
            log.warn(
                "ADMIN_PASSWORD_RESET: mail not delivered for user {} (outcome={}); reset URL returned to caller for out-of-band delivery",
                targetUserId,
                sendOutcome,
            )
        }

        // Structured audit log (#450 follow-up issue tracks persistent storage).
        // MDC keys are unconditional so a log-aggregator pipeline can index on
        // `kind=ADMIN_PASSWORD_RESET` without parsing the message string.
        MDC.putCloseable("actor", actorUserId.toString()).use {
            MDC.putCloseable("target", targetUserId.toString()).use {
                MDC.putCloseable("kind", "ADMIN_PASSWORD_RESET").use {
                    MDC.putCloseable("smtpDelivered", emailSent.toString()).use {
                        log.info(
                            "ADMIN_PASSWORD_RESET: actor={} target={} smtpDelivered={} expiresAt={}",
                            actorUserId,
                            targetUserId,
                            emailSent,
                            issued.expiresAt,
                        )
                    }
                }
            }
        }

        return Result(
            tokenIssued = true,
            emailSent = emailSent,
            expiresAt = issued.expiresAt,
            resetUrl = if (emailSent) null else resetUrl,
        )
    }

    private fun buildResetLink(rawToken: String): String {
        val base = plugwerkProperties.server.effectiveWebBaseUrl().trimEnd('/')
        return "$base/reset-password?token=$rawToken"
    }

    /** Outcome surfaced to [io.plugwerk.server.controller.AdminUserController]. */
    data class Result(
        val tokenIssued: Boolean,
        val emailSent: Boolean,
        val expiresAt: OffsetDateTime,
        val resetUrl: String?,
    )
}

/** Mapped to HTTP 400 by `GlobalExceptionHandler`. */
class SelfResetNotAllowedException(message: String) : RuntimeException(message)

/** Mapped to HTTP 400 by `GlobalExceptionHandler`. */
class ExternalUserResetNotAllowedException(message: String) : RuntimeException(message)
