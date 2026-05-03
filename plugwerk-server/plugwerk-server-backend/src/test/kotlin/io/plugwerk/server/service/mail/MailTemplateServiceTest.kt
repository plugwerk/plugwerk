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

import io.plugwerk.server.repository.MailTemplateRepository
import io.plugwerk.server.service.settings.ApplicationSettingsService
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource

/**
 * H2-backed integration test for [MailTemplateService] (#436).
 *
 * @SpringBootTest gives us the real Liquibase migration (so the seeded
 * `(auth.*, en)` rows are present), the wired-up TextEncryptor, and the
 * settings cache that `render` reads its default-language fallback from.
 *
 * The test class deletes every row in the cache between tests so each case
 * controls its own seed state — without that, the migration's `en` rows
 * leak across tests and silently mask "no row exists" assertions.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(
    properties = [
        "spring.datasource.url=jdbc:h2:mem:mail-template-svc;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
    ],
)
class MailTemplateServiceTest {

    @Autowired private lateinit var service: MailTemplateService

    @Autowired private lateinit var repository: MailTemplateRepository

    @Autowired private lateinit var settings: ApplicationSettingsService

    @BeforeEach
    fun reset() {
        repository.deleteAll()
        // Force the service cache to reflect the empty state — without this,
        // the @PostConstruct snapshot from a previous test leaks in.
        service.initialize()
    }

    @Test
    fun `render falls through enum default when no row exists for any locale`() {
        val rendered = service.render(
            MailTemplate.AUTH_PASSWORD_RESET,
            mapOf("username" to "alice", "resetLink" to "https://x", "expiresAtHuman" to "in 30 minutes"),
        )

        assertThat(rendered.subject).isEqualTo("Reset your Plugwerk password")
        assertThat(rendered.body).contains("Hello alice,")
        assertThat(rendered.body).contains("https://x")
        assertThat(rendered.body).contains("in 30 minutes")
    }

    @Test
    fun `render uses exact-locale row when present (stage 1 of fallback chain)`() {
        service.update(
            MailTemplate.AUTH_PASSWORD_RESET,
            locale = "de",
            subject = "Passwort zurücksetzen",
            body = "Hallo {{username}}, klicke {{resetLink}} ({{expiresAtHuman}})",
            updatedBy = "test",
        )

        val rendered = service.render(
            MailTemplate.AUTH_PASSWORD_RESET,
            mapOf("username" to "alice", "resetLink" to "https://x", "expiresAtHuman" to "in 30 Minuten"),
            locale = "de",
        )

        assertThat(rendered.subject).isEqualTo("Passwort zurücksetzen")
        assertThat(rendered.body).isEqualTo("Hallo alice, klicke https://x (in 30 Minuten)")
    }

    @Test
    fun `render falls back to language-base (stage 2) — de-CH falls through to de`() {
        service.update(
            MailTemplate.AUTH_PASSWORD_RESET,
            locale = "de",
            subject = "Passwort",
            body = "Hi {{username}}",
            updatedBy = "test",
        )

        val rendered = service.render(
            MailTemplate.AUTH_PASSWORD_RESET,
            mapOf("username" to "fritz", "resetLink" to "x", "expiresAtHuman" to "y"),
            locale = "de-CH",
        )

        assertThat(rendered.subject).isEqualTo("Passwort")
        assertThat(rendered.body).isEqualTo("Hi fritz")
    }

    @Test
    fun `render falls back to application default language (stage 3) when requested locale not present`() {
        // Seed an `en` row only. Request `de` → stage 1 misses (no de),
        // stage 2 misses (de has no language-base), stage 3 hits the en row
        // because general.default_language=en in this test profile.
        service.update(
            MailTemplate.AUTH_PASSWORD_RESET,
            locale = "en",
            subject = "Reset",
            body = "Hi {{username}}",
            updatedBy = "test",
        )

        val rendered = service.render(
            MailTemplate.AUTH_PASSWORD_RESET,
            mapOf("username" to "alice", "resetLink" to "x", "expiresAtHuman" to "y"),
            locale = "de",
        )

        assertThat(rendered.subject).isEqualTo("Reset")
        assertThat(rendered.body).isEqualTo("Hi alice")
        // Sanity check — stage 3 only resolves because settings.defaultLanguage() is `en`.
        assertThat(settings.defaultLanguage()).isEqualTo("en")
    }

    @Test
    fun `render strict mode throws on missing variable rather than silently emitting empty string`() {
        // Vars include `username` but not `resetLink` — the template's
        // default body references {{resetLink}}, so render must throw.
        assertThatThrownBy {
            service.render(
                MailTemplate.AUTH_PASSWORD_RESET,
                mapOf("username" to "alice", "expiresAtHuman" to "in 30 minutes"),
                // No locale → falls through to enum default, which references {{resetLink}}.
            )
        }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("resetLink")
    }

    @Test
    fun `update rejects undocumented placeholder references`() {
        assertThatThrownBy {
            service.update(
                MailTemplate.AUTH_PASSWORD_RESET,
                locale = "en",
                subject = "Reset",
                body = "Hi {{username}}, click {{badPlaceholder}} now",
                updatedBy = "test",
            )
        }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("badPlaceholder")
            .hasMessageContaining(MailTemplate.AUTH_PASSWORD_RESET.placeholders.toString())
    }

    @Test
    fun `update accepts a subset of declared placeholders (not every var must be referenced)`() {
        // The template declares username + resetLink + expiresAtHuman, but
        // a custom variant might only need username — that is allowed. The
        // validator catches typos (extra refs), not missing refs.
        val view = service.update(
            MailTemplate.AUTH_PASSWORD_RESET,
            locale = "en",
            subject = "Reset",
            body = "Hello {{username}}",
            updatedBy = "test",
        )

        assertThat(view.body).isEqualTo("Hello {{username}}")
        // And render against this minimal variant — only username needs to be in vars.
        val rendered = service.render(
            MailTemplate.AUTH_PASSWORD_RESET,
            mapOf("username" to "alice"),
            locale = "en",
        )
        assertThat(rendered.body).isEqualTo("Hello alice")
    }

    @Test
    fun `update is idempotent — second update overwrites the first row, no duplicate`() {
        service.update(
            MailTemplate.AUTH_PASSWORD_RESET,
            locale = "en",
            subject = "v1",
            body = "Body {{username}}",
            updatedBy = "test",
        )
        service.update(
            MailTemplate.AUTH_PASSWORD_RESET,
            locale = "en",
            subject = "v2",
            body = "Body {{username}}",
            updatedBy = "test",
        )

        val variants = service.findByKey(MailTemplate.AUTH_PASSWORD_RESET)
        assertThat(variants).hasSize(1)
        assertThat(variants.first().subject).isEqualTo("v2")
    }

    @Test
    fun `findByKeyAndLocale returns null when no row exists — caller falls through to render's enum default`() {
        val view = service.findByKeyAndLocale(MailTemplate.AUTH_PASSWORD_RESET, "fr")

        assertThat(view).isNull()
    }

    @Test
    fun `findByKey lists every locale variant of a template`() {
        service.update(MailTemplate.AUTH_PASSWORD_RESET, "en", "EN", "Hi {{username}}", "test")
        service.update(MailTemplate.AUTH_PASSWORD_RESET, "de", "DE", "Hallo {{username}}", "test")

        val variants = service.findByKey(MailTemplate.AUTH_PASSWORD_RESET)

        assertThat(variants).hasSize(2)
        assertThat(variants.map { it.locale }).containsExactlyInAnyOrder("en", "de")
    }
}
