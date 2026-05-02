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
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.JavaMailSenderImpl
import org.springframework.stereotype.Component
import java.util.Properties
import java.util.concurrent.atomic.AtomicReference

/**
 * Builds and caches a [JavaMailSender] instance from the runtime SMTP settings
 * stored in `application_setting` (#253). Spring Boot's auto-config wires
 * `JavaMailSender` once at startup from `application.yaml`; we deliberately
 * bypass that path because the operator must be able to swap the SMTP server
 * at runtime without restarting the JVM.
 *
 * The sender is held in an [AtomicReference] so reads are lock-free and
 * always see either the previous or the new instance — no half-built state.
 * The cache is invalidated by an `ApplicationSettingsService` update listener
 * on every `smtp.*` write; the next [current] call rebuilds.
 *
 * Returns `null` when SMTP is disabled or the configuration is incomplete
 * (host blank). Callers — currently only [MailService] — are expected to
 * treat that as "no-op + warn", never as an exception.
 */
@Component
class MailSenderProvider(private val settings: ApplicationSettingsService) {

    private val log = LoggerFactory.getLogger(MailSenderProvider::class.java)

    private val cached = AtomicReference<JavaMailSender?>(null)

    @PostConstruct
    fun registerInvalidationHook() {
        settings.addUpdateListener { key ->
            if (key.key.startsWith("smtp.")) {
                log.debug("Invalidating cached JavaMailSender after change to {}", key.key)
                invalidate()
            }
        }
    }

    /**
     * Returns the current [JavaMailSender] instance, building it lazily from
     * the live SMTP settings. Returns `null` when SMTP is disabled, the host
     * is unset, or the encryption mode is unknown.
     */
    fun current(): JavaMailSender? {
        cached.get()?.let { return it }
        val built = build() ?: return null
        // Compare-and-set so two concurrent first calls do not race; whichever
        // wins, the next call returns the cached instance.
        return if (cached.compareAndSet(null, built)) built else cached.get()
    }

    /** Drops the cached sender. Called by the settings update listener. */
    fun invalidate() {
        cached.set(null)
    }

    /**
     * Builds a fresh [JavaMailSenderImpl] from the live settings, or returns
     * `null` if the configuration is incomplete (e.g. enabled=false, blank
     * host, unknown encryption mode). All "incomplete" cases log at INFO/WARN
     * so an operator misconfiguration is visible without spamming the log on
     * every send.
     */
    private fun build(): JavaMailSenderImpl? {
        if (!settings.smtpEnabled()) {
            log.debug("SMTP disabled (smtp.enabled=false) — no JavaMailSender built")
            return null
        }
        val host = settings.smtpHost()
        if (host.isBlank()) {
            log.warn("smtp.enabled=true but smtp.host is blank — cannot build JavaMailSender")
            return null
        }
        val sender = JavaMailSenderImpl()
        sender.host = host
        sender.port = settings.smtpPort()
        val username = settings.smtpUsername()
        if (username.isNotBlank()) {
            sender.username = username
            sender.password = settings.smtpPasswordPlaintext()
        }

        val props: Properties = sender.javaMailProperties
        props.setProperty("mail.transport.protocol", "smtp")
        when (settings.smtpEncryption()) {
            "starttls" -> {
                props.setProperty("mail.smtp.starttls.enable", "true")
                // STARTTLS-required (not just opportunistic) so the connection
                // upgrades or fails — silently falling back to plaintext on a
                // submission port is exactly the kind of misconfig users do
                // not want.
                props.setProperty("mail.smtp.starttls.required", "true")
            }

            "tls" -> {
                // Implicit TLS (port-465 style) — the socket is TLS from byte 0.
                props.setProperty("mail.smtp.ssl.enable", "true")
            }

            "none" -> {
                // Explicit opt-out for plain SMTP. Useful for in-org dev/test
                // SMTP relays without TLS termination. Logged at INFO so the
                // choice is visible.
                log.info("SMTP encryption=none — connection will not be encrypted")
            }

            else -> {
                log.warn(
                    "Unknown smtp.encryption '{}' — refusing to build JavaMailSender",
                    settings.smtpEncryption(),
                )
                return null
            }
        }
        if (username.isNotBlank()) {
            props.setProperty("mail.smtp.auth", "true")
        }
        log.info(
            "Built JavaMailSender for {}:{} encryption={} auth={}",
            host,
            sender.port,
            settings.smtpEncryption(),
            username.isNotBlank(),
        )
        return sender
    }

    /** For tests: peek at whether the cache is populated without forcing a build. */
    internal fun isCached(): Boolean = cached.get() != null

    /** For tests + the test endpoint: force a build using the current settings. */
    internal fun forceBuild(): JavaMailSender? = build()?.also { cached.set(it) }

    /**
     * Visible for tests so that the [ApplicationSettingKey] type is the boundary,
     * not the dotted string — keeps callers compiling against the enum.
     */
    @Suppress("unused")
    internal fun smtpKeys(): List<ApplicationSettingKey> = listOf(
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
