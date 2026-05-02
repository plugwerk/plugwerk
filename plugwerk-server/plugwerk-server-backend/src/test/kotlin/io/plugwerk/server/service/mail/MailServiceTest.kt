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
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.mail.MailSendException
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSender

@ExtendWith(MockitoExtension::class)
class MailServiceTest {

    private lateinit var settings: ApplicationSettingsService
    private lateinit var provider: MailSenderProvider
    private lateinit var sender: JavaMailSender
    private lateinit var service: MailService

    @BeforeEach
    fun setUp() {
        settings = mock()
        provider = mock()
        sender = mock()
        service = MailService(settings, provider)
    }

    @Test
    fun `sendMail returns Disabled when smtp_enabled is false (no JavaMailSender contacted)`() {
        whenever(settings.smtpEnabled()).thenReturn(false)

        val result = service.sendMail("alice@example.test", "Hi", "Body")

        assertThat(result).isEqualTo(MailService.SendResult.Disabled)
        verify(provider, never()).current()
        verify(sender, never()).send(any<SimpleMailMessage>())
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
        // From-address is the only required field at send time once SMTP is
        // enabled — the absence of any other smtp.* field surfaces as
        // Disabled/Failed depending on which layer caught it.
        verify(sender, never()).send(any<SimpleMailMessage>())
    }

    @Test
    fun `sendMail returns Sent and hands a correctly built SimpleMailMessage to the JavaMailSender`() {
        whenever(settings.smtpEnabled()).thenReturn(true)
        whenever(provider.current()).thenReturn(sender)
        whenever(settings.smtpFromAddress()).thenReturn("noreply@plugwerk.test")
        whenever(settings.smtpFromName()).thenReturn("Plugwerk")

        val result = service.sendMail("alice@example.test", "Welcome", "Hello Alice")

        assertThat(result).isEqualTo(MailService.SendResult.Sent)
        val captor = argumentCaptor<SimpleMailMessage>()
        verify(sender).send(captor.capture())
        val sent = captor.firstValue
        assertThat(sent.from).isEqualTo("Plugwerk <noreply@plugwerk.test>")
        assertThat(sent.to).containsExactly("alice@example.test")
        assertThat(sent.subject).isEqualTo("Welcome")
        assertThat(sent.text).isEqualTo("Hello Alice")
    }

    @Test
    fun `sendMail uses bare from_address when from_name is blank`() {
        // Edge case: operator only configured the address. The display-name
        // RFC-5322 form `Name <addr>` would render as `<addr>` with the bare
        // angle brackets — ugly. Fall back to just the address.
        whenever(settings.smtpEnabled()).thenReturn(true)
        whenever(provider.current()).thenReturn(sender)
        whenever(settings.smtpFromAddress()).thenReturn("noreply@plugwerk.test")
        whenever(settings.smtpFromName()).thenReturn("")

        service.sendMail("alice@example.test", "Hi", "Body")

        val captor = argumentCaptor<SimpleMailMessage>()
        verify(sender).send(captor.capture())
        assertThat(captor.firstValue.from).isEqualTo("noreply@plugwerk.test")
    }

    @Test
    fun `sendMail returns Failed when the JavaMailSender throws a MailException`() {
        whenever(settings.smtpEnabled()).thenReturn(true)
        whenever(provider.current()).thenReturn(sender)
        whenever(settings.smtpFromAddress()).thenReturn("noreply@plugwerk.test")
        whenever(settings.smtpFromName()).thenReturn("Plugwerk")
        doThrow(MailSendException("auth failed: invalid credentials"))
            .whenever(sender).send(any<SimpleMailMessage>())

        val result = service.sendMail("alice@example.test", "Hi", "Body")

        assertThat(result).isInstanceOf(MailService.SendResult.Failed::class.java)
        assertThat((result as MailService.SendResult.Failed).reason)
            .contains("auth failed")
    }
}
