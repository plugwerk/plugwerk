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

import io.plugwerk.api.model.RegisterRequest
import io.plugwerk.api.model.RegisterResponse
import io.plugwerk.api.model.VerifyEmailResponse
import io.plugwerk.server.PlugwerkProperties
import io.plugwerk.server.security.RateLimitResult
import io.plugwerk.server.security.RegisterRateLimitService
import io.plugwerk.server.service.ConflictException
import io.plugwerk.server.service.UserService
import io.plugwerk.server.service.auth.EmailVerificationTokenService
import io.plugwerk.server.service.mail.MailService
import io.plugwerk.server.service.mail.MailTemplate
import io.plugwerk.server.service.settings.ApplicationSettingsService
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.time.Duration
import java.time.format.DateTimeFormatter

/**
 * Public endpoints for self-service account creation (#420).
 *
 * Both endpoints share a "feature-flag disguise": when
 * `auth.self_registration_enabled` is `false` they throw 404, so a
 * locked-down deployment never advertises that the registration surface
 * exists at all. The matching frontend `LoginPage` link is gated on the
 * same setting via [ConfigController].
 *
 * **Anti-enumeration shape.** The success response is uniform across
 * "new user materialised", "username already taken", and "email already
 * taken". The legitimate owner of an in-use address sees nothing because
 * no email is sent in the collision case. Only true validation errors
 * (weak password, malformed email) return 400.
 */
@RestController
@RequestMapping("/api/v1")
class AuthRegistrationController(
    private val settings: ApplicationSettingsService,
    private val userService: UserService,
    private val tokenService: EmailVerificationTokenService,
    private val mailService: MailService,
    private val rateLimitService: RegisterRateLimitService,
    private val plugwerkProperties: PlugwerkProperties,
) {

    private val log = LoggerFactory.getLogger(AuthRegistrationController::class.java)

    @PostMapping("/auth/register")
    fun register(@Valid @RequestBody registerRequest: RegisterRequest): ResponseEntity<RegisterResponse> {
        if (!settings.selfRegistrationEnabled()) {
            // 404 (not 403) because the feature is meant to be invisible
            // when off — same shape as a route that simply doesn't exist.
            throw ResponseStatusException(HttpStatus.NOT_FOUND)
        }

        // Email-keyed bucket runs after we have the parsed body. The
        // IP-keyed bucket already fired in RegisterRateLimitFilter before
        // the request reached this method.
        when (val result = rateLimitService.tryConsumeEmail(registerRequest.email)) {
            is RateLimitResult.Allowed -> Unit

            is RateLimitResult.Rejected -> throw ResponseStatusException(
                HttpStatus.TOO_MANY_REQUESTS,
                "Too many registration attempts for this email. Retry after ${result.retryAfterSeconds} s.",
            )
        }

        val verificationRequired = settings.selfRegistrationEmailVerificationRequired()

        // No-mail fallback: if the operator wants verification but never
        // configured SMTP, the flow is unusable — surface a clear 503.
        if (verificationRequired && !settings.smtpEnabled()) {
            throw ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Self-registration with email verification requires SMTP infrastructure that is not configured on this server.",
            )
        }

        val user = try {
            userService.createSelfRegistered(
                username = registerRequest.username,
                email = registerRequest.email,
                password = registerRequest.password,
                displayName = registerRequest.displayName,
                enabled = !verificationRequired,
            )
        } catch (ex: ConflictException) {
            // Anti-enumeration: silently swallow username/email collisions.
            // The legitimate owner of an in-use address sees nothing
            // (no email), and an attacker probing existing accounts
            // sees the same response shape as a successful registration.
            log.info("Self-registration collision (silenced): {}", ex.message)
            return uniformSuccessResponse(verificationRequired)
        }

        if (verificationRequired) {
            val issued = tokenService.issue(user)
            val verificationLink = buildVerificationLink(issued.rawToken)
            val expiresAtHuman = formatExpiry(issued.expiresAt)
            val send = mailService.sendMailFromTemplate(
                template = MailTemplate.AUTH_REGISTRATION_VERIFICATION,
                to = user.email,
                vars = mapOf(
                    "username" to user.displayName,
                    "verificationLink" to verificationLink,
                    "expiresAtHuman" to expiresAtHuman,
                ),
            )
            if (send !is MailService.SendResult.Sent) {
                // The verification email failed to leave the building.
                // Without it the user can never log in — fail loud so
                // they don't end up in limbo, and an operator can fix
                // SMTP. The user row stays disabled; a follow-up resend
                // (out of v1 scope) can reissue the link.
                log.error(
                    "Verification email failed for new user {} ({}): {}",
                    user.id,
                    user.email,
                    send,
                )
                throw ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Account created but the verification email could not be sent. Please contact your administrator.",
                )
            }
            log.info("Issued self-registration verification email for user {}", user.id)
        } else {
            log.info("Self-registration created enabled user {} (no verification required)", user.id)
        }

        return uniformSuccessResponse(verificationRequired)
    }

    @GetMapping("/auth/verify-email")
    fun verifyEmail(@RequestParam("token") token: String): ResponseEntity<VerifyEmailResponse> {
        if (!settings.selfRegistrationEnabled()) {
            // Same disguise as /register — operators who turned the flow
            // off don't want stale verification links to leak that the
            // endpoint exists.
            throw ResponseStatusException(HttpStatus.NOT_FOUND)
        }
        val user = tokenService.consume(token)
        if (!user.enabled) {
            // Flip the enabled flag now that ownership is proven. Use the
            // existing service mutator so `updated_at` etc. behave the
            // same way they do for admin-driven enable/disable.
            val userId = requireNotNull(user.id) { "User without id reached verify-email" }
            userService.setEnabled(userId, true)
            log.info("Self-registered user {} verified email and is now enabled", userId)
        }
        return ResponseEntity.ok(VerifyEmailResponse(status = VerifyEmailResponse.Status.VERIFIED))
    }

    private fun uniformSuccessResponse(verificationRequired: Boolean): ResponseEntity<RegisterResponse> {
        val status = if (verificationRequired) {
            RegisterResponse.Status.VERIFICATION_PENDING
        } else {
            RegisterResponse.Status.ACTIVE
        }
        return ResponseEntity.ok(RegisterResponse(status = status))
    }

    private fun buildVerificationLink(rawToken: String): String {
        val base = plugwerkProperties.server.baseUrl.trimEnd('/')
        return "$base/verify-email?token=$rawToken"
    }

    /**
     * "in 24 hours" for short windows, otherwise the absolute UTC
     * timestamp. Operator-localisation lands with #436's i18n iteration.
     */
    private fun formatExpiry(expiresAt: java.time.OffsetDateTime): String {
        val secondsLeft = Duration.between(java.time.OffsetDateTime.now(), expiresAt).seconds
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
