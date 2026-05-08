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
package io.plugwerk.server.controller

import io.plugwerk.api.AuthPasswordResetApi
import io.plugwerk.api.model.ForgotPasswordRequest
import io.plugwerk.api.model.ResetPasswordRequest
import io.plugwerk.server.PlugwerkProperties
import io.plugwerk.server.domain.UserEntity
import io.plugwerk.server.domain.UserSource
import io.plugwerk.server.repository.UserRepository
import io.plugwerk.server.security.PasswordResetRateLimitService
import io.plugwerk.server.security.RateLimitResult
import io.plugwerk.server.security.RefreshTokenCookieFactory
import io.plugwerk.server.service.RefreshTokenService
import io.plugwerk.server.service.UserService
import io.plugwerk.server.service.auth.PasswordResetTokenService
import io.plugwerk.server.service.mail.MailService
import io.plugwerk.server.service.mail.MailTemplate
import io.plugwerk.server.service.settings.ApplicationSettingsService
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.time.Duration
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/**
 * Public endpoints for the opt-in self-service password-reset flow (#421).
 *
 * Both endpoints share the "feature-flag disguise" pattern from the
 * registration controller: when `auth.password_reset_enabled` is `false`
 * they 404, so a locked-down deployment never advertises that the
 * surface exists. The `/config` flag `passwordResetEnabled` lets the
 * frontend hide the matching login-page link in the same off state —
 * the backend gate is independent and authoritative.
 *
 * **Anti-enumeration shape.** `forgot-password` always returns 204 on
 * the success path regardless of whether the supplied identifier
 * resolves to an INTERNAL user. EXTERNAL users likewise get no email
 * (they reset upstream); disabled users get no email (no point); a
 * mail-send failure is logged but does not change the status code,
 * because a 503 here would create a timing oracle between
 * "doesn't-exist" and "mail-server-broken".
 *
 * **Session revocation.** A successful `reset-password` revokes both
 * the access-token side (`passwordInvalidatedBefore` via
 * [UserService.applyPasswordReset]) and the refresh-token family
 * (`refreshTokenService.revokeAllForUser`). The refresh-cookie is
 * cleared on the response so a stolen cookie cannot be replayed.
 * Without the second revocation an attacker holding the refresh
 * cookie could keep harvesting fresh access tokens because
 * `/auth/refresh` does not consult `passwordInvalidatedBefore`.
 */
@RestController
@RequestMapping("/api/v1")
class AuthPasswordResetController(
    private val settings: ApplicationSettingsService,
    private val userService: UserService,
    private val userRepository: UserRepository,
    private val tokenService: PasswordResetTokenService,
    private val mailService: MailService,
    private val rateLimitService: PasswordResetRateLimitService,
    private val refreshTokenService: RefreshTokenService,
    private val refreshTokenCookieFactory: RefreshTokenCookieFactory,
    private val plugwerkProperties: PlugwerkProperties,
) : AuthPasswordResetApi {

    private val log = LoggerFactory.getLogger(AuthPasswordResetController::class.java)

    override fun forgotPassword(forgotPasswordRequest: ForgotPasswordRequest): ResponseEntity<Unit> {
        if (!settings.passwordResetEnabled()) {
            // 404 (not 403) so the feature is invisible when off — same
            // disguise as the registration endpoint.
            throw ResponseStatusException(HttpStatus.NOT_FOUND)
        }

        // SMTP unconfigured → 503 even though that leaks "feature is on";
        // the operator hint outweighs the marginal enumeration risk and
        // the issue spec explicitly asks for a 503 here.
        if (!settings.smtpEnabled()) {
            throw ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Self-service password reset requires SMTP infrastructure that is not configured on this server.",
            )
        }

        val input = forgotPasswordRequest.usernameOrEmail.trim()
        val user = userRepository.findByUsernameOrEmailAndSource(input, UserSource.INTERNAL).orElse(null)

        // Three branches that all return 204 silently:
        //   1. No matching INTERNAL user — could be a typo or an attacker probing.
        //   2. The user is disabled — admin or registration verification took
        //      them out; resetting their password would not actually let them
        //      back in until they're re-enabled, and surfacing that distinction
        //      would leak account state.
        //   3. The user IS resolvable but EXTERNAL — that case is filtered out
        //      by the source predicate above; this fallthrough catches future
        //      schema additions defensively.
        if (user == null || !user.enabled || user.source != UserSource.INTERNAL) {
            log.info(
                "forgot-password: silenced for input '{}' (matched={}, eligible={})",
                input,
                user != null,
                user?.enabled == true && user?.source == UserSource.INTERNAL,
            )
            return ResponseEntity.noContent().build()
        }

        return runCatching { issueAndSend(user) }.fold(
            onSuccess = { ResponseEntity.noContent().build() },
            onFailure = { err ->
                // Mail-send or token-issue failure: log loud, but return
                // the same 204 the silenced branches do — otherwise a slow
                // 503 vs. a fast 204 leaks "this user exists".
                log.error(
                    "forgot-password: mail send failed for user {} — returning 204 to preserve anti-enumeration",
                    user.id,
                    err,
                )
                ResponseEntity.noContent().build()
            },
        )
    }

    override fun resetPassword(resetPasswordRequest: ResetPasswordRequest): ResponseEntity<Unit> {
        if (!settings.passwordResetEnabled()) {
            // Stale link after operator turns the feature off → 404, same
            // disguise as forgot-password. Without this, an attacker
            // probing /reset-password could discover that the feature
            // existed at one point.
            throw ResponseStatusException(HttpStatus.NOT_FOUND)
        }

        // Token-keyed bucket fires BEFORE consume so brute-force attempts
        // against a leaked link can't iterate faster than the limit even
        // if the token's TTL is generous. The IP bucket already fired in
        // PasswordResetRateLimitFilter.
        when (val gate = rateLimitService.tryConsumeToken(resetPasswordRequest.token)) {
            is RateLimitResult.Allowed -> Unit

            is RateLimitResult.Rejected -> throw ResponseStatusException(
                HttpStatus.TOO_MANY_REQUESTS,
                "Too many reset attempts for this token. Retry after ${gate.retryAfterSeconds} s.",
            )
        }

        val user = tokenService.consume(resetPasswordRequest.token)
        val userId = requireNotNull(user.id) { "User without id reached reset-password" }

        // Side-effects in order:
        //   1. Persist the new password (BCrypt) and bump
        //      passwordInvalidatedBefore to revoke every access token in
        //      flight. UserService.applyPasswordReset handles both atoms.
        //   2. Burn the refresh-token family so /auth/refresh can't mint
        //      new access tokens from a stolen cookie. AuthController.refresh
        //      does NOT honour passwordInvalidatedBefore (verified for #421);
        //      this second revocation is mandatory, not an add-on.
        //   3. Clear the refresh cookie on the response so the browser
        //      doesn't keep re-presenting it.
        userService.applyPasswordReset(userId, resetPasswordRequest.newPassword)
        refreshTokenService.revokeAllForUser(userId, RefreshTokenService.LOGOUT)
        log.info("Password reset completed for user {} — all sessions revoked", userId)

        return ResponseEntity.noContent()
            .header(HttpHeaders.SET_COOKIE, refreshTokenCookieFactory.clear().toString())
            .build()
    }

    private fun issueAndSend(user: UserEntity) {
        val issued = tokenService.issue(user)
        val resetLink = buildResetLink(issued.rawToken)
        val expiresAtHuman = formatExpiry(issued.expiresAt)
        val send = mailService.sendMailFromTemplate(
            template = MailTemplate.AUTH_PASSWORD_RESET,
            to = user.email,
            vars = mapOf(
                "username" to user.displayName,
                "resetLink" to resetLink,
                "expiresAtHuman" to expiresAtHuman,
                "siteName" to settings.siteName(),
            ),
        )
        if (send !is MailService.SendResult.Sent) {
            // Caller's runCatching turns this into a logged 204. We throw
            // rather than return a "soft failure" object so the runCatching
            // path is the only place that decides on the user-facing status.
            error("MailService returned $send")
        }
        log.info("Issued password-reset email for user {}", user.id)
    }

    private fun buildResetLink(rawToken: String): String {
        // Browser-facing link: in dev the SPA lives on a different port
        // than the API (Vite on :5173 vs. backend on :8080), so we use
        // the dedicated web-base-url which falls back to base-url for
        // bundled-SPA production deployments.
        val base = plugwerkProperties.server.effectiveWebBaseUrl().trimEnd('/')
        return "$base/reset-password?token=$rawToken"
    }

    /**
     * "in 30 minutes" / "in 1 hours" for short windows, otherwise the
     * absolute UTC timestamp. Matches the verification-email tone from
     * `AuthRegistrationController.formatExpiry`.
     */
    private fun formatExpiry(expiresAt: OffsetDateTime): String {
        val secondsLeft = Duration.between(OffsetDateTime.now(), expiresAt).seconds
        return when {
            secondsLeft <= 0 -> "now (link already expired)"
            secondsLeft < 3600 -> "in ${(secondsLeft / 60).coerceAtLeast(1)} minutes"
            secondsLeft < 86_400 -> "in ${(secondsLeft / 3600).coerceAtLeast(1)} hours"
            else -> "on ${expiresAt.format(ABSOLUTE_FMT)}"
        }
    }

    private companion object {
        private val ABSOLUTE_FMT: DateTimeFormatter =
            DateTimeFormatter.ofPattern("d MMM yyyy 'at' HH:mm 'UTC'")
    }
}
