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
import jakarta.mail.Session
import jakarta.mail.internet.MimeMessage
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.mail.MailSendException
import org.springframework.mail.javamail.JavaMailSender
import java.util.Properties

@ExtendWith(MockitoExtension::class)
class MailServiceTest {

    private lateinit var settings: ApplicationSettingsService
    private lateinit var provider: MailSenderProvider
    private lateinit var templates: MailTemplateService
    private lateinit var sender: JavaMailSender
    private lateinit var service: MailService

    @BeforeEach
    fun setUp() {
        settings = mock()
        provider = mock()
        templates = mock()
        sender = mock()
        // The service uses sender.createMimeMessage() to assemble its message,
        // then sender.send(message). Wire createMimeMessage() to return a real
        // MimeMessage backed by a default Session — both calls become observable
        // through the captor below. lenient() because the early-return tests
        // (Disabled / Misconfigured) never reach createMimeMessage and Mockito
        // strict mode would otherwise flag the unused stub.
        org.mockito.Mockito.lenient().`when`(sender.createMimeMessage()).thenAnswer {
            MimeMessage(Session.getDefaultInstance(Properties()))
        }
        service = MailService(settings, provider, templates)
    }

    private fun configureSmtpEnabled() {
        whenever(settings.smtpEnabled()).thenReturn(true)
        whenever(provider.current()).thenReturn(sender)
        whenever(settings.smtpFromAddress()).thenReturn("noreply@plugwerk.test")
        whenever(settings.smtpFromName()).thenReturn("Plugwerk")
    }

    @Test
    fun `sendMail returns Disabled when smtp_enabled is false (no JavaMailSender contacted)`() {
        whenever(settings.smtpEnabled()).thenReturn(false)

        val result = service.sendMail("alice@example.test", "Hi", "Body")

        assertThat(result).isEqualTo(MailService.SendResult.Disabled)
        verify(provider, never()).current()
        verify(sender, never()).send(any<MimeMessage>())
    }

    @Test
    fun `sendMail returns Disabled when provider has no usable sender (incomplete config)`() {
        whenever(settings.smtpEnabled()).thenReturn(true)
        whenever(provider.current()).thenReturn(null)

        val result = service.sendMail("alice@example.test", "Hi", "Body")

        assertThat(result).isEqualTo(MailService.SendResult.Disabled)
    }

    @Test
    fun `sendMail returns Misconfigured when from_address is blank`() {
        whenever(settings.smtpEnabled()).thenReturn(true)
        whenever(provider.current()).thenReturn(sender)
        whenever(settings.smtpFromAddress()).thenReturn("")

        val result = service.sendMail("alice@example.test", "Hi", "Body")

        assertThat(result).isInstanceOf(MailService.SendResult.Misconfigured::class.java)
        verify(sender, never()).send(any<MimeMessage>())
    }

    @Test
    fun `sendMail with plaintext body only sends a single-part text-plain message`() {
        configureSmtpEnabled()

        val result = service.sendMail("alice@example.test", "Welcome", "Hello Alice")

        assertThat(result).isEqualTo(MailService.SendResult.Sent)
        val captor = argumentCaptor<MimeMessage>()
        verify(sender).send(captor.capture())
        val sent = captor.firstValue
        assertThat(sent.subject).isEqualTo("Welcome")
        assertThat(sent.from.first().toString()).contains("noreply@plugwerk.test")
        assertThat(sent.allRecipients.first().toString()).isEqualTo("alice@example.test")
        // Plaintext-only branch: content-type is text/plain (not multipart)
        assertThat(sent.contentType).startsWith("text/plain")
        assertThat(sent.content).isEqualTo("Hello Alice")
    }

    @Test
    fun `sendMail with bodyHtml sends a multipart-alternative MIME message containing both parts`() {
        configureSmtpEnabled()

        val result = service.sendMail(
            to = "alice@example.test",
            subject = "Welcome",
            bodyPlain = "Hello Alice (plain)",
            bodyHtml = "<p>Hello <b>Alice</b> (html)</p>",
        )

        assertThat(result).isEqualTo(MailService.SendResult.Sent)
        val captor = argumentCaptor<MimeMessage>()
        verify(sender).send(captor.capture())
        val sent = captor.firstValue
        // saveChanges() materialises the MIME headers from the in-memory
        // Multipart — without this, getContentType() reports the placeholder
        // "text/plain" set during the empty-message bootstrap. JavaMailSenderImpl
        // does this internally before transport; the mock does not, so we
        // do it here to match production semantics.
        sent.saveChanges()
        // Spring's MimeMessageHelper(multipart=true) defaults to MIXED_RELATED,
        // so the outer content-type is `multipart/mixed`. The plain+html
        // `multipart/alternative` part is nested inside — assert against the
        // raw MIME stream rather than the top-level header.
        assertThat(sent.contentType).startsWith("multipart/")
        val baos = java.io.ByteArrayOutputStream()
        sent.writeTo(baos)
        val mimeText = baos.toString()
        assertThat(mimeText).contains("multipart/alternative")
        assertThat(mimeText).contains("Hello Alice (plain)")
        assertThat(mimeText).contains("<p>Hello <b>Alice</b> (html)</p>")
        assertThat(mimeText).contains("text/plain")
        assertThat(mimeText).contains("text/html")
    }

    @Test
    fun `sendMail uses bare from_address when from_name is blank`() {
        whenever(settings.smtpEnabled()).thenReturn(true)
        whenever(provider.current()).thenReturn(sender)
        whenever(settings.smtpFromAddress()).thenReturn("noreply@plugwerk.test")
        whenever(settings.smtpFromName()).thenReturn("")

        service.sendMail("alice@example.test", "Hi", "Body")

        val captor = argumentCaptor<MimeMessage>()
        verify(sender).send(captor.capture())
        // No display name → bare address only, no `Name <addr>` form.
        assertThat(captor.firstValue.from.first().toString()).isEqualTo("noreply@plugwerk.test")
    }

    @Test
    fun `sendMail returns Failed when the JavaMailSender throws a MailException`() {
        configureSmtpEnabled()
        doThrow(MailSendException("auth failed: invalid credentials"))
            .whenever(sender).send(any<MimeMessage>())

        val result = service.sendMail("alice@example.test", "Hi", "Body")

        assertThat(result).isInstanceOf(MailService.SendResult.Failed::class.java)
        assertThat((result as MailService.SendResult.Failed).reason)
            .contains("auth failed")
    }

    @Test
    fun `sendMailFromTemplate forwards rendered subject + bodies to sendMail`() {
        configureSmtpEnabled()
        whenever(
            templates.render(
                eq(MailTemplate.AUTH_PASSWORD_RESET),
                any(),
                anyOrNull<String>(),
            ),
        ).thenReturn(
            RenderedMail(
                subject = "Rendered subject",
                bodyPlain = "Plain body",
                bodyHtml = "<p>HTML body</p>",
            ),
        )

        val result = service.sendMailFromTemplate(
            MailTemplate.AUTH_PASSWORD_RESET,
            to = "alice@example.test",
            vars = mapOf("username" to "alice"),
        )

        assertThat(result).isEqualTo(MailService.SendResult.Sent)
        val captor = argumentCaptor<MimeMessage>()
        verify(sender).send(captor.capture())
        val sent = captor.firstValue
        sent.saveChanges()
        assertThat(sent.subject).isEqualTo("Rendered subject")
        // Both bodies present → multipart/alternative.
        assertThat(sent.contentType).startsWith("multipart/")
    }

    @Test
    fun `sendMailFromTemplate returns Misconfigured when render throws missing-var`() {
        // Deliberately do NOT call configureSmtpEnabled — render fails before
        // the SMTP path is touched, so the SMTP enabled-check should also not
        // matter. Mirrors how a typo in the var map would surface in
        // production.
        whenever(
            templates.render(
                eq(MailTemplate.AUTH_PASSWORD_RESET),
                any(),
                anyOrNull<String>(),
            ),
        ).thenThrow(IllegalArgumentException("Template references {{resetLink}} but it was not provided"))

        val result = service.sendMailFromTemplate(
            MailTemplate.AUTH_PASSWORD_RESET,
            to = "alice@example.test",
            vars = mapOf("username" to "alice"),
        )

        assertThat(result).isInstanceOf(MailService.SendResult.Misconfigured::class.java)
        assertThat((result as MailService.SendResult.Misconfigured).reason)
            .contains("auth.password_reset")
            .contains("resetLink")
        verify(sender, never()).send(any<MimeMessage>())
    }
}

// Tiny eq-helper alias to keep the test bodies above readable.
private inline fun <reified T : Any> eq(value: T): T = org.mockito.kotlin.eq(value)
