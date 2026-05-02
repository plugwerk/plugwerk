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
package io.plugwerk.server.service.mail

import io.plugwerk.server.service.settings.ApplicationSettingsService
import org.slf4j.LoggerFactory
import org.springframework.mail.MailException
import org.springframework.mail.SimpleMailMessage
import org.springframework.stereotype.Service

/**
 * Single entry point for sending transactional email (#253).
 *
 * Today the interface is intentionally narrow: synchronous one-shot
 * `sendMail(to, subject, body)`. Async/queued delivery, retries, and template
 * rendering are deferred to follow-up issues — adding stub methods here would
 * widen the API surface without consumers.
 *
 * **No-op semantics when SMTP is not configured.** When `smtp.enabled=false`
 * or the configuration is incomplete (no host), [sendMail] returns
 * [SendResult.Disabled] without contacting any server. Callers that have a
 * legitimate reason to surface this to the user (e.g. the test-mail endpoint)
 * inspect the result; everything else can ignore it and rely on the warning
 * log line.
 */
@Service
class MailService(private val settings: ApplicationSettingsService, private val provider: MailSenderProvider) {
    private val log = LoggerFactory.getLogger(MailService::class.java)

    /**
     * Sends a plain-text email synchronously. Returns the outcome rather than
     * throwing so call sites that do not care (most of them) can stay terse.
     *
     * @param to the recipient email address.
     * @param subject the message subject.
     * @param body the message body (plain text).
     */
    fun sendMail(to: String, subject: String, body: String): SendResult {
        if (!settings.smtpEnabled()) {
            log.warn("Skipping send to {} — SMTP is disabled (smtp.enabled=false)", to)
            return SendResult.Disabled
        }
        val sender = provider.current() ?: run {
            log.warn("Skipping send to {} — SMTP is enabled but configuration is incomplete", to)
            return SendResult.Disabled
        }
        val fromAddress = settings.smtpFromAddress()
        if (fromAddress.isBlank()) {
            log.warn("Skipping send to {} — smtp.from_address is not configured", to)
            return SendResult.Misconfigured("smtp.from_address is not configured")
        }
        val fromName = settings.smtpFromName()
        val from = if (fromName.isBlank()) fromAddress else "$fromName <$fromAddress>"
        val message = SimpleMailMessage().apply {
            this.from = from
            this.setTo(to)
            this.subject = subject
            this.text = body
        }
        return try {
            sender.send(message)
            log.info("Sent mail to {} subject='{}'", to, subject)
            SendResult.Sent
        } catch (ex: MailException) {
            // Distinguish at the call site between operator misconfig (auth /
            // relay refused) and the SMTP server being briefly unreachable;
            // here we only know it failed. Caller decides 5xx vs 4xx mapping.
            log.warn("Failed to send mail to {}: {}", to, ex.message)
            SendResult.Failed(ex.message ?: "Mail send failed")
        }
    }

    /** Outcome of a [sendMail] call. */
    sealed interface SendResult {
        /** Mail successfully handed to the SMTP server. Delivery is then asynchronous and not tracked here. */
        data object Sent : SendResult

        /** SMTP master switch is off, or the configuration is too incomplete to attempt a send. */
        data object Disabled : SendResult

        /** SMTP is enabled but a required field is missing — distinct from [Disabled] so callers can surface it. */
        data class Misconfigured(val reason: String) : SendResult

        /** The SMTP server rejected the send (auth failed, recipient refused, network error, etc.). */
        data class Failed(val reason: String) : SendResult
    }
}
