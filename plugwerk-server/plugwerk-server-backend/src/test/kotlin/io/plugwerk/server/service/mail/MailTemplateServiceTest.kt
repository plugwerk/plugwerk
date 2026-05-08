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
            mapOf(
                "username" to "alice",
                "resetLink" to "https://x",
                "expiresAtHuman" to "in 30 minutes",
                "siteName" to "marketplace.example.test",
            ),
        )

        assertThat(rendered.subject).isEqualTo("Reset your Plugwerk password")
        assertThat(rendered.bodyPlain).contains("Hi alice,")
        assertThat(rendered.bodyPlain).contains("https://x")
        assertThat(rendered.bodyPlain).contains("in 30 minutes")
        // Enum default for AUTH_PASSWORD_RESET ships an HTML body too —
        // render must materialise it. Post-#449 the body is the
        // editorial-minimal Carbon-Blue layout, asserted by class hooks
        // rather than tag shape because the literal markup is large and
        // would make these assertions brittle.
        assertThat(rendered.bodyHtml).isNotNull()
        assertThat(rendered.bodyHtml).contains(">Reset password<")
        assertThat(rendered.bodyHtml).contains("https://x")
        assertThat(rendered.bodyHtml).contains("class=\"pw-card\"")
    }

    @Test
    fun `render uses exact-locale row when present (stage 1 of fallback chain)`() {
        service.update(
            MailTemplate.AUTH_PASSWORD_RESET,
            locale = "de",
            subject = "Passwort zurücksetzen",
            bodyPlain = "Hallo {{username}}, klicke {{resetLink}} ({{expiresAtHuman}})",
            bodyHtml = "<p>Hallo {{username}}, <a href=\"{{resetLink}}\">klicke hier</a></p>",
            updatedBy = "test",
        )

        val rendered = service.render(
            MailTemplate.AUTH_PASSWORD_RESET,
            mapOf("username" to "alice", "resetLink" to "https://x", "expiresAtHuman" to "in 30 Minuten"),
            locale = "de",
        )

        assertThat(rendered.subject).isEqualTo("Passwort zurücksetzen")
        assertThat(rendered.bodyPlain).isEqualTo("Hallo alice, klicke https://x (in 30 Minuten)")
        assertThat(rendered.bodyHtml).isEqualTo("<p>Hallo alice, <a href=\"https://x\">klicke hier</a></p>")
    }

    @Test
    fun `render falls back to language-base (stage 2) — de-CH falls through to de`() {
        service.update(
            MailTemplate.AUTH_PASSWORD_RESET,
            locale = "de",
            subject = "Passwort",
            bodyPlain = "Hi {{username}}",
            updatedBy = "test",
        )

        val rendered = service.render(
            MailTemplate.AUTH_PASSWORD_RESET,
            mapOf("username" to "fritz"),
            locale = "de-CH",
        )

        assertThat(rendered.subject).isEqualTo("Passwort")
        assertThat(rendered.bodyPlain).isEqualTo("Hi fritz")
        // Per-row fallback: the `de` row exists and explicitly has no HTML
        // body, so render returns null instead of mixing in the enum's
        // English HTML default. Operator-set plaintext-only stays plaintext-only.
        assertThat(rendered.bodyHtml).isNull()
    }

    @Test
    fun `render falls back to application default language (stage 3) when requested locale not present`() {
        service.update(
            MailTemplate.AUTH_PASSWORD_RESET,
            locale = "en",
            subject = "Reset",
            bodyPlain = "Hi {{username}}",
            updatedBy = "test",
        )

        val rendered = service.render(
            MailTemplate.AUTH_PASSWORD_RESET,
            mapOf("username" to "alice"),
            locale = "de",
        )

        assertThat(rendered.subject).isEqualTo("Reset")
        assertThat(rendered.bodyPlain).isEqualTo("Hi alice")
        assertThat(settings.defaultLanguage()).isEqualTo("en")
    }

    @Test
    fun `render strict mode throws on missing variable rather than silently emitting empty string`() {
        assertThatThrownBy {
            service.render(
                MailTemplate.AUTH_PASSWORD_RESET,
                // Deliberately omits resetLink. Strict mode raises on the
                // first missing var jmustache encounters; siteName is also
                // declared but provided to keep the assertion pointed at
                // the actual missing-content case.
                mapOf(
                    "username" to "alice",
                    "expiresAtHuman" to "in 30 minutes",
                    "siteName" to "marketplace.example.test",
                ),
            )
        }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("resetLink")
    }

    @Test
    fun `render auto-escapes HTML in vars to prevent stored-XSS through user-controlled placeholders`() {
        service.update(
            MailTemplate.AUTH_PASSWORD_RESET,
            locale = "en",
            subject = "Reset",
            bodyPlain = "Hi {{username}}",
            bodyHtml = "<p>Hi {{username}}, click <a href=\"{{resetLink}}\">here</a></p>",
            updatedBy = "test",
        )

        val rendered = service.render(
            MailTemplate.AUTH_PASSWORD_RESET,
            mapOf(
                "username" to "<script>alert('xss')</script>",
                "resetLink" to "https://app/reset?token=abc&user=alice",
                "expiresAtHuman" to "in 30 minutes",
            ),
            locale = "en",
        )

        // Plaintext body keeps `<` `>` `&` intact — no HTML rendering happens.
        assertThat(rendered.bodyPlain).contains("<script>alert('xss')</script>")
        // HTML body escapes the dangerous characters — &lt;script&gt; renders
        // as visible text in the browser, not an executable element.
        assertThat(rendered.bodyHtml).contains("&lt;script&gt;alert")
        assertThat(rendered.bodyHtml).doesNotContain("<script>")
        // The `&` in the URL is escaped to `&amp;`. jmustache's escaper is
        // aggressive and additionally hex-escapes `=` (-> `&#x3D;`) and `'`
        // (-> `&#39;`); browsers decode all of those back to the original
        // characters when rendering the attribute value, so the link still
        // works at click time. The assertion below pins the safety property
        // (no raw `&` outside the entity form, no executable script).
        assertThat(rendered.bodyHtml).contains("&amp;")
        assertThat(rendered.bodyHtml).doesNotContain("?token=abc&user")
    }

    @Test
    fun `update rejects undocumented placeholder references in any of subject, plaintext, html`() {
        assertThatThrownBy {
            service.update(
                MailTemplate.AUTH_PASSWORD_RESET,
                locale = "en",
                subject = "Reset",
                bodyPlain = "Hi {{username}}, click {{badPlaceholder}} now",
                updatedBy = "test",
            )
        }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("badPlaceholder")

        // Same check for the HTML body — plain is fine but html has a typo.
        assertThatThrownBy {
            service.update(
                MailTemplate.AUTH_PASSWORD_RESET,
                locale = "en",
                subject = "Reset",
                bodyPlain = "Hi {{username}}",
                bodyHtml = "<p>Hi {{username}}, <a href=\"{{notARealVar}}\">click</a></p>",
                updatedBy = "test",
            )
        }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("notARealVar")
    }

    @Test
    fun `update accepts a subset of declared placeholders (not every var must be referenced)`() {
        val view = service.update(
            MailTemplate.AUTH_PASSWORD_RESET,
            locale = "en",
            subject = "Reset",
            bodyPlain = "Hello {{username}}",
            updatedBy = "test",
        )

        assertThat(view.bodyPlain).isEqualTo("Hello {{username}}")
        assertThat(view.bodyHtml).isNull()

        val rendered = service.render(
            MailTemplate.AUTH_PASSWORD_RESET,
            mapOf("username" to "alice"),
            locale = "en",
        )
        assertThat(rendered.bodyPlain).isEqualTo("Hello alice")
        assertThat(rendered.bodyHtml).isNull()
    }

    @Test
    fun `update is idempotent — second update overwrites the first row, no duplicate`() {
        service.update(
            MailTemplate.AUTH_PASSWORD_RESET,
            locale = "en",
            subject = "v1",
            bodyPlain = "Body {{username}}",
            updatedBy = "test",
        )
        service.update(
            MailTemplate.AUTH_PASSWORD_RESET,
            locale = "en",
            subject = "v2",
            bodyPlain = "Body {{username}}",
            bodyHtml = "<p>HTML body {{username}}</p>",
            updatedBy = "test",
        )

        val variants = service.findByKey(MailTemplate.AUTH_PASSWORD_RESET)
        assertThat(variants).hasSize(1)
        assertThat(variants.first().subject).isEqualTo("v2")
        assertThat(variants.first().bodyHtml).isEqualTo("<p>HTML body {{username}}</p>")
    }

    @Test
    fun `update with explicit blank bodyHtml is rejected — null is the no-html marker, blank is invalid`() {
        assertThatThrownBy {
            service.update(
                MailTemplate.AUTH_PASSWORD_RESET,
                locale = "en",
                subject = "Reset",
                bodyPlain = "Hi {{username}}",
                bodyHtml = "   ",
                updatedBy = "test",
            )
        }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("bodyHtml")
    }

    @Test
    fun `findByKeyAndLocale returns null when no row exists — caller falls through to render's enum default`() {
        val view = service.findByKeyAndLocale(MailTemplate.AUTH_PASSWORD_RESET, "fr")

        assertThat(view).isNull()
    }

    @Test
    fun `previewWith returns IllegalArgumentException for an undocumented placeholder`() {
        assertThatThrownBy {
            service.previewWith(
                template = MailTemplate.AUTH_PASSWORD_RESET,
                subject = "Reset",
                bodyPlain = "Hi {{username}} click {{notARealVar}}",
                bodyHtml = null,
            )
        }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("notARealVar")
    }

    @Test
    fun `previewWith renders against registry sample vars by default`() {
        val result = service.previewWith(
            template = MailTemplate.AUTH_PASSWORD_RESET,
            subject = "Reset for {{username}}",
            bodyPlain = "Hi {{username}}, click {{resetLink}}",
            bodyHtml = null,
        )
        // Registry default for AUTH_PASSWORD_RESET seeds username = "Alice".
        assertThat(result.rendered.subject).contains("Alice")
        assertThat(result.rendered.bodyPlain).contains("Alice")
        assertThat(result.sampleVars["username"]).isEqualTo("Alice")
    }

    @Test
    fun `previewWith merges caller overrides on top of registry sample vars`() {
        val result = service.previewWith(
            template = MailTemplate.AUTH_PASSWORD_RESET,
            subject = "Reset for {{username}}",
            bodyPlain = "Hi {{username}}",
            bodyHtml = null,
            sampleVarsOverride = mapOf("username" to "Bob"),
        )
        assertThat(result.rendered.subject).isEqualTo("Reset for Bob")
        assertThat(result.sampleVars["username"]).isEqualTo("Bob")
        // Other registry defaults still present (resetLink, expiresAtHuman).
        assertThat(result.sampleVars["resetLink"]).isNotEmpty()
    }

    @Test
    fun `findByKey lists every locale variant of a template`() {
        service.update(MailTemplate.AUTH_PASSWORD_RESET, "en", "EN", "Hi {{username}}", null, "test")
        service.update(MailTemplate.AUTH_PASSWORD_RESET, "de", "DE", "Hallo {{username}}", null, "test")

        val variants = service.findByKey(MailTemplate.AUTH_PASSWORD_RESET)

        assertThat(variants).hasSize(2)
        assertThat(variants.map { it.locale }).containsExactlyInAnyOrder("en", "de")
    }

    // -----------------------------------------------------------------------
    // Issue #449 — editorial-minimal Carbon-Blue layout guarantees.
    //
    // These tests pin the brand promises the EmailLayoutBuilder makes when
    // its output is rendered through MailTemplateService against the enum
    // defaults. They are deliberately targeted at small, stable
    // identifiers (`pw-card`, `pw-accent`, `>Verify email<`) rather than
    // full-markup snapshots — copy and spacing details may shift in
    // future polish passes without breaking the brand contract.
    // -----------------------------------------------------------------------

    @Test
    fun `registration verification mail renders the editorial layout chrome`() {
        val rendered = service.render(
            MailTemplate.AUTH_REGISTRATION_VERIFICATION,
            mapOf(
                "username" to "alice",
                "verificationLink" to "https://app.plugwerk.test/verify?token=t",
                "expiresAtHuman" to "in 24 hours",
                "siteName" to "marketplace.example.test",
            ),
        )

        val html = rendered.bodyHtml ?: error("HTML body must be present for AUTH_REGISTRATION_VERIFICATION")
        // Wordmark, accent gestures, card chrome.
        assertThat(html).contains(">Plugwerk<")
        assertThat(html).contains("class=\"pw-card\"")
        assertThat(html).contains("class=\"pw-accent\"")
        // Single CTA ("Verify email") and the user's link both present.
        // The link's `=` is hex-escaped to `&#x3D;` by jmustache (same
        // posture documented in the auto-escapes test); we assert only
        // on the path components so the test stays valid regardless of
        // jmustache's escape-table internals.
        assertThat(html).contains(">Verify email<")
        assertThat(html).contains("https://app.plugwerk.test/verify?token")
        // Footer carries the rendered siteName.
        assertThat(html).contains("Sent by Plugwerk")
        assertThat(html).contains("marketplace.example.test")
        // Anti-template guarantee: no logo image, no gradient.
        assertThat(html).doesNotContain("<img")
        assertThat(html).doesNotContain("linear-gradient")
    }

    @Test
    fun `password reset mail renders the editorial layout chrome`() {
        val rendered = service.render(
            MailTemplate.AUTH_PASSWORD_RESET,
            mapOf(
                "username" to "alice",
                "resetLink" to "https://app.plugwerk.test/reset?token=t",
                "expiresAtHuman" to "in 30 minutes",
                "siteName" to "marketplace.example.test",
            ),
        )

        val html = rendered.bodyHtml ?: error("HTML body must be present for AUTH_PASSWORD_RESET")
        assertThat(html).contains(">Plugwerk<")
        assertThat(html).contains("class=\"pw-card\"")
        assertThat(html).contains(">Reset password<")
        // See the comment on the registration-verification test: jmustache
        // hex-escapes `=` so we assert only on the path prefix.
        assertThat(html).contains("https://app.plugwerk.test/reset?token")
        assertThat(html).contains("marketplace.example.test")
    }

    @Test
    fun `enum default html embeds the dark-mode media query so Apple Mail and iOS render correctly`() {
        // The dark-mode block lives in a static <style> inside the layout —
        // it survives Mustache rendering verbatim because there are no
        // {{vars}} inside the @media rule. Asserting the marker string here
        // catches a future refactor that accidentally inlines everything
        // (Outlook desktop ignores @media, but Apple Mail and iOS need it).
        val rendered = service.render(
            MailTemplate.AUTH_REGISTRATION_VERIFICATION,
            mapOf(
                "username" to "alice",
                "verificationLink" to "https://x",
                "expiresAtHuman" to "in 24 hours",
                "siteName" to "marketplace.example.test",
            ),
        )

        assertThat(rendered.bodyHtml).contains("@media (prefers-color-scheme: dark)")
        assertThat(rendered.bodyHtml).contains("background-color: #262626 !important")
    }

    @Test
    fun `siteName is a required placeholder — strict mode catches a caller that forgets to set it`() {
        assertThatThrownBy {
            service.render(
                MailTemplate.AUTH_PASSWORD_RESET,
                mapOf(
                    "username" to "alice",
                    "resetLink" to "https://x",
                    "expiresAtHuman" to "in 30 minutes",
                    // siteName intentionally omitted — every caller must
                    // wire it through (AuthRegistrationController +
                    // AuthPasswordResetController already do).
                ),
            )
        }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("siteName")
    }
}
