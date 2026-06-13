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

import io.plugwerk.server.service.settings.ApplicationSettingKey
import io.plugwerk.server.service.settings.ApplicationSettingsService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.mail.javamail.JavaMailSenderImpl

/**
 * Pure Mockito unit tests for [MailSenderProvider] targeting currently-missed
 * JaCoCo branches in `build()`, `current()`, the invalidation listener, and the
 * test-only helpers.
 *
 * [ApplicationSettingsService] is mocked so each test drives one config shape:
 * disabled, blank host, every `smtp.encryption` arm (starttls / tls / none /
 * unknown), and authenticated-vs-anonymous relay (the two `username.isNotBlank()`
 * branches).
 */
@ExtendWith(MockitoExtension::class)
class MailSenderProviderBranchCoverageTest {

    private val settings: ApplicationSettingsService = mock()
    private val provider = MailSenderProvider(settings)

    /**
     * Wires the happy-path SMTP settings; individual tests override the bits they vary.
     *
     * Stubs are lenient because anonymous-relay tests (username blank) never consult
     * [ApplicationSettingsService.smtpPasswordPlaintext], and cached-path tests build
     * once — Mockito strict mode would otherwise flag the unconsumed stubs. Same
     * posture as the lenient createMimeMessage stub in MailServiceTest.
     */
    private fun configureValidSmtp(
        enabled: Boolean = true,
        host: String = "smtp.example.test",
        port: Int = 587,
        username: String = "",
        password: String = "",
        encryption: String = "starttls",
    ) {
        val lenient = org.mockito.Mockito.lenient()
        lenient.`when`(settings.smtpEnabled()).thenReturn(enabled)
        lenient.`when`(settings.smtpHost()).thenReturn(host)
        lenient.`when`(settings.smtpPort()).thenReturn(port)
        lenient.`when`(settings.smtpUsername()).thenReturn(username)
        lenient.`when`(settings.smtpPasswordPlaintext()).thenReturn(password)
        lenient.`when`(settings.smtpEncryption()).thenReturn(encryption)
    }

    // ---- build(): early-return branches ------------------------------------

    @Test
    fun `current returns null when SMTP is disabled (enabled-false arm)`() {
        whenever(settings.smtpEnabled()).thenReturn(false)

        assertThat(provider.current()).isNull()
        assertThat(provider.isCached()).isFalse()
    }

    @Test
    fun `current returns null when host is blank (blank-host arm)`() {
        whenever(settings.smtpEnabled()).thenReturn(true)
        whenever(settings.smtpHost()).thenReturn("   ")

        assertThat(provider.current()).isNull()
    }

    @Test
    fun `current returns null when encryption mode is unknown (when else arm)`() {
        configureValidSmtp(encryption = "quantum-tls")

        assertThat(provider.current()).isNull()
        assertThat(provider.isCached()).isFalse()
    }

    // ---- build(): encryption arms ------------------------------------------

    @Test
    fun `forceBuild configures STARTTLS required for the starttls arm`() {
        configureValidSmtp(encryption = "starttls")

        val sender = provider.forceBuild() as JavaMailSenderImpl

        assertThat(sender.host).isEqualTo("smtp.example.test")
        assertThat(sender.port).isEqualTo(587)
        val props = sender.javaMailProperties
        assertThat(props.getProperty("mail.smtp.starttls.enable")).isEqualTo("true")
        assertThat(props.getProperty("mail.smtp.starttls.required")).isEqualTo("true")
        assertThat(props.getProperty("mail.transport.protocol")).isEqualTo("smtp")
    }

    @Test
    fun `forceBuild enables implicit SSL for the tls arm`() {
        configureValidSmtp(encryption = "tls", port = 465)

        val sender = provider.forceBuild() as JavaMailSenderImpl

        val props = sender.javaMailProperties
        assertThat(props.getProperty("mail.smtp.ssl.enable")).isEqualTo("true")
        assertThat(props.getProperty("mail.smtp.starttls.enable")).isNull()
    }

    @Test
    fun `forceBuild builds an unencrypted sender for the none arm`() {
        configureValidSmtp(encryption = "none")

        val sender = provider.forceBuild() as JavaMailSenderImpl

        val props = sender.javaMailProperties
        // none arm sets no TLS/SSL flags at all.
        assertThat(props.getProperty("mail.smtp.starttls.enable")).isNull()
        assertThat(props.getProperty("mail.smtp.ssl.enable")).isNull()
    }

    // ---- build(): authenticated vs anonymous relay -------------------------

    @Test
    fun `forceBuild sets credentials and auth flag when username is present (username non-blank arm)`() {
        configureValidSmtp(username = "mailer", password = "s3cret", encryption = "starttls")

        val sender = provider.forceBuild() as JavaMailSenderImpl

        assertThat(sender.username).isEqualTo("mailer")
        assertThat(sender.password).isEqualTo("s3cret")
        assertThat(sender.javaMailProperties.getProperty("mail.smtp.auth")).isEqualTo("true")
    }

    @Test
    fun `forceBuild leaves credentials unset for an anonymous relay (username blank arm)`() {
        configureValidSmtp(username = "", encryption = "none")

        val sender = provider.forceBuild() as JavaMailSenderImpl

        assertThat(sender.username).isNull()
        assertThat(sender.javaMailProperties.getProperty("mail.smtp.auth")).isNull()
        // smtpPasswordPlaintext must not be consulted when there is no username.
        verify(settings, never()).smtpPasswordPlaintext()
    }

    // ---- current(): caching + compareAndSet --------------------------------

    @Test
    fun `current caches the built sender and returns the same instance on the second call`() {
        configureValidSmtp(encryption = "starttls")

        val first = provider.current()
        val second = provider.current()

        assertThat(first).isNotNull
        assertThat(provider.isCached()).isTrue()
        assertThat(second).isSameAs(first)
        // The second call returns the cached instance — settings are read only on the first build.
        verify(settings, times(1)).smtpHost()
    }

    @Test
    fun `current returns null and does not cache when build fails (build-null arm)`() {
        whenever(settings.smtpEnabled()).thenReturn(false)

        assertThat(provider.current()).isNull()
        assertThat(provider.isCached()).isFalse()
    }

    // ---- invalidate + forceBuild -------------------------------------------

    @Test
    fun `invalidate drops a previously cached sender`() {
        configureValidSmtp(encryption = "starttls")
        provider.current()
        assertThat(provider.isCached()).isTrue()

        provider.invalidate()

        assertThat(provider.isCached()).isFalse()
    }

    @Test
    fun `forceBuild populates the cache directly`() {
        configureValidSmtp(encryption = "starttls")

        val built = provider.forceBuild()

        assertThat(built).isNotNull
        assertThat(provider.isCached()).isTrue()
    }

    @Test
    fun `forceBuild returns null and leaves the cache empty when config is incomplete`() {
        whenever(settings.smtpEnabled()).thenReturn(true)
        whenever(settings.smtpHost()).thenReturn("")

        assertThat(provider.forceBuild()).isNull()
        assertThat(provider.isCached()).isFalse()
    }

    // ---- registerInvalidationHook listener branches ------------------------

    @Test
    fun `the update listener invalidates the cache for an smtp-prefixed key but not for others`() {
        configureValidSmtp(encryption = "starttls")
        // Capture the listener registered by @PostConstruct.
        provider.registerInvalidationHook()
        val captor = argumentCaptor<(ApplicationSettingKey) -> Unit>()
        verify(settings).addUpdateListener(captor.capture())
        val listener = captor.firstValue

        // Seed the cache so we can observe invalidation.
        provider.current()
        assertThat(provider.isCached()).isTrue()

        // Non-smtp key: prefix check is false → cache untouched.
        listener(ApplicationSettingKey.GENERAL_SITE_NAME)
        assertThat(provider.isCached()).isTrue()

        // smtp.* key: prefix check is true → cache invalidated.
        listener(ApplicationSettingKey.SMTP_ENABLED)
        assertThat(provider.isCached()).isFalse()
    }

    // ---- smtpKeys() --------------------------------------------------------

    @Test
    fun `smtpKeys lists exactly the eight smtp setting keys`() {
        val keys = provider.smtpKeys()

        assertThat(keys).containsExactly(
            ApplicationSettingKey.SMTP_ENABLED,
            ApplicationSettingKey.SMTP_HOST,
            ApplicationSettingKey.SMTP_PORT,
            ApplicationSettingKey.SMTP_USERNAME,
            ApplicationSettingKey.SMTP_PASSWORD,
            ApplicationSettingKey.SMTP_ENCRYPTION,
            ApplicationSettingKey.SMTP_FROM_ADDRESS,
            ApplicationSettingKey.SMTP_FROM_NAME,
        )
    }
}
