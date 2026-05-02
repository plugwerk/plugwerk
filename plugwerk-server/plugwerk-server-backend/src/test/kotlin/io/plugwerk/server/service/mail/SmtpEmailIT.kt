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

import com.icegreen.greenmail.junit5.GreenMailExtension
import com.icegreen.greenmail.util.ServerSetup
import io.plugwerk.server.service.settings.ApplicationSettingKey
import io.plugwerk.server.service.settings.ApplicationSettingsService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource

/**
 * End-to-end SMTP verification via GreenMail (#253).
 *
 * Boots the full Spring context (H2 + Liquibase migrations including the SMTP
 * seed in 0025), wires the runtime settings to point at an embedded GreenMail
 * server on a random port, and sends a real message through `MailService` ->
 * `MailSenderProvider` -> `JavaMailSenderImpl`. Asserts the message lands in
 * GreenMail's inbox with the expected From/To/subject/body.
 *
 * Why this lives here and not in the controller test slice: the bug we want
 * to catch is the boundary between our settings-driven `JavaMailSender` build
 * path and a real SMTP transport — a controller-only test with a mock
 * `MailService` would not exercise that.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(
    properties = [
        // H2 in-memory; matches the standard test profile pattern used elsewhere
        // in the project (UserServiceTest, etc.) so we get the Liquibase seed
        // without a Postgres container.
        "spring.datasource.url=jdbc:h2:mem:smtp-it;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
    ],
)
@Tag("integration")
class SmtpEmailIT {

    @Autowired
    private lateinit var settings: ApplicationSettingsService

    @Autowired
    private lateinit var mailService: MailService

    @Autowired
    private lateinit var provider: MailSenderProvider

    /**
     * Embedded SMTP on a dynamic port — declared as `@RegisterExtension` so the
     * port is known by the time the test methods need it. ServerSetup.SMTP is
     * plain SMTP (no TLS); pairs with `smtp.encryption=none` below.
     */
    @RegisterExtension
    @Suppress("JUnitMalformedDeclaration") // Kotlin val gets a final field; JUnit5 accepts it.
    val greenMail: GreenMailExtension = GreenMailExtension(ServerSetup.SMTP.dynamicPort())

    @BeforeEach
    fun configureSmtp() {
        // Point the application's runtime SMTP settings at the GreenMail
        // server. We bypass the HTTP admin API (auth setup is irrelevant for
        // this integration boundary) and write through the service directly.
        // The settings service's listener will invalidate the cached
        // JavaMailSender so the next mailService.sendMail builds a fresh
        // instance pointing at the correct port.
        provider.invalidate()
        settings.update(ApplicationSettingKey.SMTP_HOST, "localhost", "test-setup")
        settings.update(
            ApplicationSettingKey.SMTP_PORT,
            greenMail.smtp.port.toString(),
            "test-setup",
        )
        settings.update(ApplicationSettingKey.SMTP_ENCRYPTION, "none", "test-setup")
        settings.update(ApplicationSettingKey.SMTP_USERNAME, "", "test-setup")
        settings.update(
            ApplicationSettingKey.SMTP_FROM_ADDRESS,
            "noreply@plugwerk.test",
            "test-setup",
        )
        settings.update(ApplicationSettingKey.SMTP_FROM_NAME, "Plugwerk", "test-setup")
        // smtp.enabled last so the cache invalidation hook does not race with
        // partial config — the next sendMail sees a fully-formed setting set.
        settings.update(ApplicationSettingKey.SMTP_ENABLED, "true", "test-setup")
    }

    @Test
    fun `sendMail delivers to the SMTP server with the expected envelope`() {
        val result = mailService.sendMail(
            to = "alice@example.test",
            subject = "Welcome to Plugwerk",
            body = "This is a test message from the SMTP IT.",
        )

        assertThat(result).isEqualTo(MailService.SendResult.Sent)
        // GreenMail blocks until the SMTP server has accepted and stored the
        // message; no extra wait needed.
        val received = greenMail.receivedMessages
        assertThat(received).hasSize(1)
        val message = received[0]
        assertThat(message.subject).isEqualTo("Welcome to Plugwerk")
        assertThat(message.from.first().toString()).contains("noreply@plugwerk.test")
        assertThat(message.allRecipients.first().toString()).isEqualTo("alice@example.test")
        // Body is multi-part on some SMTP libraries; SimpleMailMessage uses
        // text/plain, so the content() is a String we can compare directly.
        val rawContent = message.content
        val bodyText = if (rawContent is String) rawContent else rawContent.toString()
        assertThat(bodyText).contains("This is a test message")
    }

    @Test
    fun `password setting written via update is encrypted at rest, masked on read`() {
        // Pre-condition: write a password through the service. The service
        // routes PASSWORD-typed values through TextEncryptor so the row in
        // application_setting holds ciphertext.
        settings.update(ApplicationSettingKey.SMTP_PASSWORD, "s3cr3t-top-of-mind", "test-setup")

        // The decrypted plaintext accessor returns the original value.
        assertThat(settings.smtpPasswordPlaintext()).isEqualTo("s3cr3t-top-of-mind")

        // listAll surfaces ciphertext (raw value), not plaintext — the
        // controller layer is what masks it to "***" for the wire.
        val snapshot = settings.listAll().first { it.key == ApplicationSettingKey.SMTP_PASSWORD }
        assertThat(snapshot.value).isNotEqualTo("s3cr3t-top-of-mind")
        assertThat(snapshot.value).isNotEmpty()
    }

    @Test
    fun `disabling smtp via setting update makes mailService no-op without sending`() {
        settings.update(ApplicationSettingKey.SMTP_ENABLED, "false", "test-setup")

        val result = mailService.sendMail(
            to = "ignored@example.test",
            subject = "Should not arrive",
            body = "Body",
        )

        assertThat(result).isEqualTo(MailService.SendResult.Disabled)
        // GreenMail should have received nothing for this test — the previous
        // test's deliveries are isolated by the per-test extension lifecycle.
        assertThat(greenMail.receivedMessages).isEmpty()
    }
}
