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
import org.springframework.mail.MailParseException
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.stereotype.Service
import java.nio.charset.StandardCharsets

/**
 * Single entry point for sending transactional email (#253, #436).
 *
 * Two send paths share one outcome contract ([SendResult]):
 *
 *   - [sendMail] — caller already has a rendered subject + body. Required:
 *     plaintext body. Optional: HTML body. Plaintext-only sends become a
 *     single-part `text/plain` MIME message; sends with both bodies become
 *     a `multipart/mixed` (Spring's default flexible container when
 *     `multipart=true`) wrapping a `multipart/alternative` with both parts,
 *     so HTML-capable clients render the HTML and plaintext-only clients
 *     (spam filters, accessibility tools, terminal mail readers) still get
 *     a real body to render.
 *
 *   - [sendMailFromTemplate] — caller provides a [MailTemplate] registry
 *     entry plus the variable map and an optional locale. The
 *     [MailTemplateService] runs the locale-fallback chain, renders both
 *     bodies, and forwards to [sendMail].
 *
 * **No-op semantics when SMTP is not configured.** When `smtp.enabled=false`
 * or the configuration is incomplete (no host), both methods return
 * [SendResult.Disabled] without contacting any server. Callers that have a
 * legitimate reason to surface this to the user (e.g. the test-mail endpoint)
 * inspect the result; everything else can ignore it and rely on the warning
 * log line.
 */
@Service
class MailService(
    private val settings: ApplicationSettingsService,
    private val provider: MailSenderProvider,
    private val templates: MailTemplateService,
) {
    private val log = LoggerFactory.getLogger(MailService::class.java)

    /**
     * Sends an email synchronously. Returns the outcome rather than throwing
     * so call sites that do not care (most of them) can stay terse.
     *
     * @param to the recipient email address.
     * @param subject the message subject.
     * @param bodyPlain the plaintext body — always required, even when
     *   [bodyHtml] is set. Spam filters and accessibility-focused clients
     *   benefit from a real plaintext alternative.
     * @param bodyHtml optional HTML body. When set, the message is sent as
     *   `multipart/mixed` wrapping a `multipart/alternative` (Spring's
     *   `MULTIPART_MODE_MIXED_RELATED` default) with both parts; when null,
     *   the message is single-part `text/plain` (cheaper, smaller, no MIME
     *   boundary).
     */
    fun sendMail(to: String, subject: String, bodyPlain: String, bodyHtml: String? = null): SendResult {
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

        val mime = sender.createMimeMessage()
        try {
            // multipart=true selects MULTIPART_MODE_MIXED_RELATED so the
            // outer container can carry future attachments / inline images;
            // cheap when plaintext-only (no MIME boundary added when only
            // setText(plain) is called).
            val helper = MimeMessageHelper(
                mime,
                /* multipart = */
                bodyHtml != null,
                StandardCharsets.UTF_8.name(),
            )
            if (fromName.isBlank()) {
                helper.setFrom(fromAddress)
            } else {
                helper.setFrom(fromAddress, fromName)
            }
            helper.setTo(to)
            helper.setSubject(subject)
            if (bodyHtml != null) {
                // Two-arg setText assembles `multipart/alternative` with the
                // plaintext + HTML parts in the order RFC 2046 recommends
                // (plaintext first, HTML second so HTML-capable clients
                // pick the last alternative).
                helper.setText(bodyPlain, bodyHtml)
            } else {
                helper.setText(bodyPlain, false)
            }
        } catch (ex: Exception) {
            log.warn("Failed to assemble MIME message for {}: {}", to, ex.message)
            return SendResult.Failed("Failed to assemble message: ${ex.message}")
        }

        return try {
            sender.send(mime)
            log.info(
                "Sent mail to {} subject='{}' html={}",
                to,
                subject,
                bodyHtml != null,
            )
            SendResult.Sent
        } catch (ex: MailParseException) {
            log.warn("Failed to parse mail to {}: {}", to, ex.message)
            SendResult.Failed(ex.message ?: "Mail parse failed")
        } catch (ex: MailException) {
            // Distinguish at the call site between operator misconfig (auth /
            // relay refused) and the SMTP server being briefly unreachable;
            // here we only know it failed. Caller decides 5xx vs 4xx mapping.
            log.warn("Failed to send mail to {}: {}", to, ex.message)
            SendResult.Failed(ex.message ?: "Mail send failed")
        }
    }

    /**
     * Renders a registered template and sends it (#436 / #437).
     *
     * Resolves the template through [MailTemplateService.render] (locale
     * fallback chain, strict missing-var detection, dual-compiler HTML
     * escaping), then forwards to [sendMail]. The HTML body part is
     * automatically wired when the resolved variant has one.
     *
     * @param template registry entry to send.
     * @param to recipient email address.
     * @param vars Mustache variable map; every `{{key}}` referenced in the
     *   resolved variant must be present.
     * @param locale optional BCP-47 tag. `null` uses the application default.
     */
    fun sendMailFromTemplate(
        template: MailTemplate,
        to: String,
        vars: Map<String, Any?>,
        locale: String? = null,
    ): SendResult {
        val rendered = try {
            templates.render(template, vars, locale)
        } catch (ex: IllegalArgumentException) {
            // Most likely a missing required placeholder. Surface as
            // Misconfigured so the test endpoint and other callers can
            // distinguish "your template is broken" from "the SMTP server
            // is broken" — the former is a 400, the latter a 502.
            log.warn(
                "Cannot render template {} for {}: {}",
                template.key,
                to,
                ex.message,
            )
            return SendResult.Misconfigured(
                "Template '${template.key}' could not be rendered: ${ex.message}",
            )
        }
        return sendMail(to, rendered.subject, rendered.bodyPlain, rendered.bodyHtml)
    }

    /** Outcome of a [sendMail] / [sendMailFromTemplate] call. */
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
