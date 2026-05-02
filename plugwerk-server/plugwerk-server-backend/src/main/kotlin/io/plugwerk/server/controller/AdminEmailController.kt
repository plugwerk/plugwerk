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

import io.plugwerk.api.AdminEmailApi
import io.plugwerk.api.model.SendTestEmailRequest
import io.plugwerk.api.model.SendTestEmailResponse
import io.plugwerk.server.security.NamespaceAuthorizationService
import io.plugwerk.server.security.currentAuthentication
import io.plugwerk.server.service.mail.MailService
import io.plugwerk.server.service.mail.MailService.SendResult
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Admin endpoint for verifying SMTP configuration end-to-end (#253).
 *
 * The only operation today is `POST /admin/email/test` — sends a one-shot
 * test message to a target address using the live SMTP settings, returning
 * a kuratierte success/error response so the operator can confirm their
 * configuration without watching the server log.
 */
@RestController
@RequestMapping("/api/v1")
class AdminEmailController(
    private val mailService: MailService,
    private val namespaceAuthorizationService: NamespaceAuthorizationService,
) : AdminEmailApi {

    private val log = LoggerFactory.getLogger(AdminEmailController::class.java)

    @PreAuthorize("@namespaceAuthorizationService.isCurrentUserSuperadmin()")
    override fun sendTestEmail(sendTestEmailRequest: SendTestEmailRequest): ResponseEntity<SendTestEmailResponse> {
        // requireSuperadmin runs first as a defence-in-depth — @PreAuthorize
        // already gates entry, but a future refactor that drops the
        // annotation should still hit a 403 before sending anyone email.
        val principal = requireSuperadmin()
        val target = sendTestEmailRequest.target
        log.info("Superadmin {} requested test email to {}", principal, target)

        val subject = "Plugwerk SMTP test"
        val body = """
            This is a test email from Plugwerk.

            If you received this, your SMTP configuration is working.

            Sent by superadmin: $principal
        """.trimIndent()

        return when (val result = mailService.sendMail(target, subject, body)) {
            SendResult.Sent ->
                ResponseEntity.ok(SendTestEmailResponse(message = "Test email sent to $target"))

            SendResult.Disabled ->
                throw SmtpNotConfiguredException(
                    "SMTP is disabled or the configuration is incomplete. Set smtp.enabled=true and " +
                        "configure smtp.host before sending mail.",
                )

            is SendResult.Misconfigured ->
                throw SmtpNotConfiguredException(result.reason)

            is SendResult.Failed ->
                throw SmtpDeliveryException(result.reason)
        }
    }

    private fun requireSuperadmin(): String {
        val auth = currentAuthentication()
        namespaceAuthorizationService.requireSuperadmin(auth)
        return auth.name
    }
}

/** Thrown when SMTP is disabled or its configuration is incomplete (#253). Mapped to 400. */
class SmtpNotConfiguredException(message: String) : RuntimeException(message)

/** Thrown when the SMTP server rejected the message (#253). Mapped to 502. */
class SmtpDeliveryException(message: String) : RuntimeException(message)
