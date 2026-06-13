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

import io.plugwerk.server.domain.MailTemplateEntity
import io.plugwerk.server.repository.MailTemplateRepository
import io.plugwerk.server.service.settings.ApplicationSettingsService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.OffsetDateTime
import java.util.Optional
import kotlin.test.assertFailsWith

/**
 * Pure Mockito unit tests for [MailTemplateService] targeting currently-missed
 * JaCoCo branches.
 *
 * The existing [MailTemplateServiceTest] is an H2-backed `@SpringBootTest`; this
 * suite deliberately mocks [MailTemplateRepository] and [ApplicationSettingsService]
 * so it can drive the explicit branch arms (require throws, elvis/null paths,
 * empty-vs-nonempty collections, the dual catch blocks in `previewWith` /
 * `validateReferences`, and every stage of the locale-fallback chain) without
 * a Spring context.
 *
 * The service reads its in-memory snapshot from [MailTemplateRepository.findAll];
 * each test stubs that and calls `service.initialize()` to seed the desired
 * cache state, mirroring how `@PostConstruct` would populate it at runtime.
 */
@ExtendWith(MockitoExtension::class)
class MailTemplateServiceBranchCoverageTest {

    private val repository: MailTemplateRepository = mock()
    private val settings: ApplicationSettingsService = mock()
    private val service: MailTemplateService = MailTemplateService(repository, settings)

    private val template = MailTemplate.AUTH_PASSWORD_RESET

    private fun row(
        templateKey: String,
        locale: String,
        subject: String = "Reset",
        bodyPlain: String = "Hi {{username}}",
        bodyHtml: String? = null,
    ) = MailTemplateEntity(
        templateKey = templateKey,
        locale = locale,
        subject = subject,
        bodyPlain = bodyPlain,
        bodyHtml = bodyHtml,
        updatedAt = OffsetDateTime.parse("2025-01-01T00:00:00Z"),
        updatedBy = "seed",
    )

    /** Seeds the in-memory cache from the supplied rows via the @PostConstruct entry point. */
    private fun seedCache(vararg rows: MailTemplateEntity) {
        whenever(repository.findAll()).thenReturn(rows.toList())
        service.initialize()
    }

    // ---- findAll -----------------------------------------------------------

    @Test
    fun `findAll skips rows whose templateKey is not in the registry (byKey null arm)`() {
        seedCache(
            row(template.key, "en"),
            row("totally.unknown.key", "en"),
        )

        val all = service.findAll()

        // Only the known-key row survives mapNotNull; the unknown-key row is dropped.
        assertThat(all).hasSize(1)
        assertThat(all.first().key).isEqualTo(template)
        assertThat(all.first().source).isEqualTo(TemplateSource.DATABASE)
    }

    @Test
    fun `findAll returns empty list when cache is empty`() {
        seedCache()

        assertThat(service.findAll()).isEmpty()
    }

    // ---- findByKey ---------------------------------------------------------

    @Test
    fun `findByKey returns only variants whose key matches and drops the rest (filter both arms)`() {
        seedCache(
            row(template.key, "en"),
            row(template.key, "de"),
            row(MailTemplate.AUTH_REGISTRATION_VERIFICATION.key, "en"),
        )

        val variants = service.findByKey(template)

        assertThat(variants).hasSize(2)
        assertThat(variants.map { it.locale }).containsExactlyInAnyOrder("en", "de")
    }

    @Test
    fun `findByKey returns empty list when no row matches`() {
        seedCache(row(MailTemplate.AUTH_REGISTRATION_VERIFICATION.key, "en"))

        assertThat(service.findByKey(template)).isEmpty()
    }

    // ---- findByKeyAndLocale ------------------------------------------------

    @Test
    fun `findByKeyAndLocale returns the view when a row exists`() {
        seedCache(row(template.key, "en", subject = "Stored subject"))

        val view = service.findByKeyAndLocale(template, "en")

        assertThat(view).isNotNull
        assertThat(view!!.subject).isEqualTo("Stored subject")
    }

    @Test
    fun `findByKeyAndLocale returns null when no row exists (elvis return arm)`() {
        seedCache()

        assertThat(service.findByKeyAndLocale(template, "fr")).isNull()
    }

    // ---- update: require guards -------------------------------------------

    @Test
    fun `update throws when locale is blank`() {
        seedCache()
        assertFailsWith<IllegalArgumentException> {
            service.update(template, locale = "  ", subject = "S", bodyPlain = "Hi {{username}}", updatedBy = "t")
        }
        verify(repository, never()).save(any())
    }

    @Test
    fun `update throws when subject is blank`() {
        seedCache()
        assertFailsWith<IllegalArgumentException> {
            service.update(template, locale = "en", subject = "", bodyPlain = "Hi {{username}}", updatedBy = "t")
        }
        verify(repository, never()).save(any())
    }

    @Test
    fun `update throws when bodyPlain is blank`() {
        seedCache()
        assertFailsWith<IllegalArgumentException> {
            service.update(template, locale = "en", subject = "S", bodyPlain = "   ", updatedBy = "t")
        }
        verify(repository, never()).save(any())
    }

    @Test
    fun `update throws when bodyHtml is provided but blank (non-null but blank arm)`() {
        seedCache()
        assertThat(
            assertFailsWith<IllegalArgumentException> {
                service.update(
                    template,
                    locale = "en",
                    subject = "S",
                    bodyPlain = "Hi {{username}}",
                    bodyHtml = "   ",
                    updatedBy = "t",
                )
            }.message,
        ).contains("bodyHtml")
        verify(repository, never()).save(any())
    }

    // ---- update: undocumented-placeholder errors --------------------------

    @Test
    fun `update throws when subject references an undocumented placeholder (errors non-empty arm)`() {
        seedCache()
        assertThat(
            assertFailsWith<IllegalArgumentException> {
                service.update(
                    template,
                    locale = "en",
                    subject = "Reset {{bogus}}",
                    bodyPlain = "Hi {{username}}",
                    updatedBy = "t",
                )
            }.message,
        ).contains("bogus")
        verify(repository, never()).save(any())
    }

    @Test
    fun `update throws when bodyHtml references an undocumented placeholder (html validation arm)`() {
        seedCache()
        assertThat(
            assertFailsWith<IllegalArgumentException> {
                service.update(
                    template,
                    locale = "en",
                    subject = "Reset",
                    bodyPlain = "Hi {{username}}",
                    bodyHtml = "<p>{{notReal}}</p>",
                    updatedBy = "t",
                )
            }.message,
        ).contains("notReal")
    }

    // ---- update: insert vs update (existing?.apply ?: new) -----------------

    @Test
    fun `update inserts a new entity when no existing row is found (elvis-new arm)`() {
        seedCache()
        whenever(repository.findByTemplateKeyAndLocale(template.key, "en")).thenReturn(Optional.empty())
        whenever(repository.save(any<MailTemplateEntity>())).thenAnswer { it.arguments[0] as MailTemplateEntity }
        // After the save, refreshCache() reloads findAll(); from this point findAll()
        // must yield the saved variant so the post-save findByKeyAndLocale guard
        // resolves a row. This stub replaces the empty one set by seedCache().
        whenever(repository.findAll()).thenReturn(listOf(row(template.key, "en", subject = "New")))

        val view = service.update(
            template,
            locale = "en",
            subject = "New",
            bodyPlain = "Hi {{username}}",
            updatedBy = "t",
        )

        assertThat(view.subject).isEqualTo("New")
        verify(repository).save(any<MailTemplateEntity>())
    }

    @Test
    fun `update mutates the existing entity in place when a row is found (existing-apply arm)`() {
        val existing = row(template.key, "en", subject = "Old", bodyPlain = "Hi {{username}}")
        seedCache(existing)
        whenever(repository.findByTemplateKeyAndLocale(template.key, "en")).thenReturn(Optional.of(existing))
        whenever(repository.save(any<MailTemplateEntity>())).thenAnswer { it.arguments[0] as MailTemplateEntity }
        // After save, refreshCache() reloads the mutated row (same instance is mutated
        // in place by existing?.apply { ... }). This stub replaces seedCache()'s.
        whenever(repository.findAll()).thenReturn(listOf(existing))

        val view = service.update(
            template,
            locale = "en",
            subject = "Updated",
            bodyPlain = "Hi {{username}}",
            bodyHtml = "<p>Hi {{username}}</p>",
            updatedBy = "editor",
        )

        // The existing instance was mutated in place rather than a fresh entity created.
        assertThat(existing.subject).isEqualTo("Updated")
        assertThat(existing.bodyHtml).isEqualTo("<p>Hi {{username}}</p>")
        assertThat(existing.updatedBy).isEqualTo("editor")
        assertThat(view.subject).isEqualTo("Updated")
    }

    @Test
    fun `update throws IllegalStateException when refreshCache loses the row right after save (error guard)`() {
        seedCache()
        whenever(repository.findByTemplateKeyAndLocale(template.key, "en")).thenReturn(Optional.empty())
        whenever(repository.save(any<MailTemplateEntity>())).thenAnswer { it.arguments[0] as MailTemplateEntity }
        // findAll stays empty even after save → findByKeyAndLocale returns null → error(...) fires.
        whenever(repository.findAll()).thenReturn(emptyList())

        assertFailsWith<IllegalStateException> {
            service.update(template, locale = "en", subject = "S", bodyPlain = "Hi {{username}}", updatedBy = "t")
        }
    }

    // ---- delete ------------------------------------------------------------

    @Test
    fun `delete throws when locale is blank`() {
        assertFailsWith<IllegalArgumentException> { service.delete(template, "") }
        verify(repository, never()).deleteByTemplateKeyAndLocale(any(), any())
    }

    @Test
    fun `delete returns true and refreshes cache when a row was deleted (deleted greater than zero arm)`() {
        whenever(repository.deleteByTemplateKeyAndLocale(template.key, "en")).thenReturn(1L)
        whenever(repository.findAll()).thenReturn(emptyList())

        assertThat(service.delete(template, "en")).isTrue()
        verify(repository).findAll() // refreshCache fired
    }

    @Test
    fun `delete returns false and skips refresh when no row was deleted (zero arm)`() {
        whenever(repository.deleteByTemplateKeyAndLocale(template.key, "en")).thenReturn(0L)

        assertThat(service.delete(template, "en")).isFalse()
        verify(repository, never()).findAll() // refreshCache NOT fired
    }

    // ---- findEffective -----------------------------------------------------

    @Test
    fun `findEffective returns the stored override when a row exists`() {
        seedCache(row(template.key, "en", subject = "Override subject"))

        val view = service.findEffective(template, "en")

        assertThat(view.source).isEqualTo(TemplateSource.DATABASE)
        assertThat(view.subject).isEqualTo("Override subject")
    }

    @Test
    fun `findEffective synthesises the enum-default view when no row exists (elvis default arm)`() {
        // findByKeyAndLocale reads the cache directly (no resolveStored / settings call),
        // so defaultLanguage() is intentionally not stubbed here.
        seedCache()

        val view = service.findEffective(template, "fr")

        assertThat(view.source).isEqualTo(TemplateSource.DEFAULT)
        assertThat(view.subject).isEqualTo(template.defaultSubject)
        assertThat(view.bodyPlain).isEqualTo(template.defaultBodyPlainTemplate)
        assertThat(view.bodyHtml).isEqualTo(template.defaultBodyHtmlTemplate)
        assertThat(view.updatedAt).isNull()
        assertThat(view.updatedBy).isNull()
    }

    // ---- previewWith -------------------------------------------------------

    @Test
    fun `previewWith throws when subject is blank`() {
        assertFailsWith<IllegalArgumentException> {
            service.previewWith(template, subject = "", bodyPlain = "Hi {{username}}", bodyHtml = null)
        }
    }

    @Test
    fun `previewWith throws when bodyPlain is blank`() {
        assertFailsWith<IllegalArgumentException> {
            service.previewWith(template, subject = "S", bodyPlain = " ", bodyHtml = null)
        }
    }

    @Test
    fun `previewWith throws when bodyHtml is provided but blank`() {
        assertThat(
            assertFailsWith<IllegalArgumentException> {
                service.previewWith(template, subject = "S", bodyPlain = "Hi {{username}}", bodyHtml = "  ")
            }.message,
        ).contains("bodyHtml")
    }

    @Test
    fun `previewWith throws when a referenced placeholder is undocumented (errors non-empty arm)`() {
        assertThat(
            assertFailsWith<IllegalArgumentException> {
                service.previewWith(
                    template,
                    subject = "Reset",
                    bodyPlain = "Hi {{username}} {{ghost}}",
                    bodyHtml = null,
                )
            }.message,
        ).contains("ghost")
    }

    @Test
    fun `previewWith renders plain-only when bodyHtml is null (htmlTpl null arm)`() {
        val result = service.previewWith(
            template,
            subject = "Reset for {{username}}",
            bodyPlain = "Hi {{username}}",
            bodyHtml = null,
        )

        assertThat(result.rendered.bodyHtml).isNull()
        assertThat(result.rendered.subject).contains("Alice") // registry sample var
        assertThat(result.sampleVars["username"]).isEqualTo("Alice")
    }

    @Test
    fun `previewWith renders the html body when bodyHtml is provided (htmlTpl non-null arm)`() {
        val result = service.previewWith(
            template,
            subject = "Reset",
            bodyPlain = "Hi {{username}}",
            bodyHtml = "<p>Hi {{username}}</p>",
        )

        assertThat(result.rendered.bodyHtml).isEqualTo("<p>Hi Alice</p>")
    }

    @Test
    fun `previewWith merges caller overrides on top of registry sample vars`() {
        val result = service.previewWith(
            template,
            subject = "Reset for {{username}}",
            bodyPlain = "Hi {{username}}",
            bodyHtml = null,
            sampleVarsOverride = mapOf("username" to "Bob"),
        )

        assertThat(result.rendered.subject).isEqualTo("Reset for Bob")
        assertThat(result.sampleVars["username"]).isEqualTo("Bob")
    }

    @Test
    fun `previewWith wraps malformed Mustache as IllegalArgumentException (MustacheException catch)`() {
        // Unterminated section — jmustache surfaces this as a MustacheException
        // both at validateReferences compile-time and at execute-time.
        assertThat(
            assertFailsWith<IllegalArgumentException> {
                service.previewWith(
                    template,
                    subject = "Reset",
                    bodyPlain = "Hi {{#username}} no close",
                    bodyHtml = null,
                )
            }.message,
        ).contains(template.key)
    }

    // ---- render ------------------------------------------------------------

    @Test
    fun `render uses the stored variant when a matching row exists (stored non-null branch)`() {
        seedCache(row(template.key, "en", subject = "Stored {{username}}", bodyPlain = "Plain {{username}}"))

        val rendered = service.render(template, mapOf("username" to "alice"), locale = "en")

        assertThat(rendered.subject).isEqualTo("Stored alice")
        assertThat(rendered.bodyPlain).isEqualTo("Plain alice")
        // Stored row has null bodyHtml → htmlTpl null arm → no HTML body.
        assertThat(rendered.bodyHtml).isNull()
    }

    @Test
    fun `render uses the stored html body when the variant has one (htmlTpl non-null branch)`() {
        seedCache(
            row(
                template.key,
                "en",
                subject = "S",
                bodyPlain = "Hi {{username}}",
                bodyHtml = "<p>Hi {{username}}</p>",
            ),
        )

        val rendered = service.render(template, mapOf("username" to "alice"), locale = "en")

        assertThat(rendered.bodyHtml).isEqualTo("<p>Hi alice</p>")
    }

    @Test
    fun `render falls through to enum defaults when no row exists (stored null branch)`() {
        seedCache()
        whenever(settings.defaultLanguage()).thenReturn("en")

        val rendered = service.render(
            template,
            mapOf(
                "username" to "alice",
                "resetLink" to "https://x",
                "expiresAtHuman" to "in 30 minutes",
                "siteName" to "site.test",
            ),
            locale = null,
        )

        assertThat(rendered.subject).isEqualTo(template.defaultSubject)
        // Enum default ships an HTML body → htmlTpl non-null arm exercised on the default path.
        assertThat(rendered.bodyHtml).isNotNull()
    }

    @Test
    fun `render wraps a missing-variable failure as IllegalArgumentException (MustacheException catch)`() {
        seedCache()
        whenever(settings.defaultLanguage()).thenReturn("en")

        assertThat(
            assertFailsWith<IllegalArgumentException> {
                // Omit resetLink/siteName → strict-mode jmustache raises mid-render.
                service.render(template, mapOf("username" to "alice"), locale = null)
            }.message,
        ).contains(template.key)
    }

    // ---- resolveStored fallback chain (exercised via render) ---------------

    @Test
    fun `render resolveStored stage 1 matches the exact locale row`() {
        seedCache(row(template.key, "de", subject = "Exakt {{username}}", bodyPlain = "Hallo {{username}}"))
        // settings.defaultLanguage must not be consulted because stage 1 hits first.

        val rendered = service.render(template, mapOf("username" to "fritz"), locale = "de")

        assertThat(rendered.subject).isEqualTo("Exakt fritz")
        verify(settings, never()).defaultLanguage()
    }

    @Test
    fun `render resolveStored stage 2 falls from de-CH to the de language base (base not equal locale arm)`() {
        seedCache(row(template.key, "de", subject = "Basis {{username}}", bodyPlain = "Hallo {{username}}"))

        val rendered = service.render(template, mapOf("username" to "fritz"), locale = "de-CH")

        assertThat(rendered.subject).isEqualTo("Basis fritz")
        // Stage 2 resolved without ever consulting the application default.
        verify(settings, never()).defaultLanguage()
    }

    @Test
    fun `render resolveStored stage 3 uses the application default language when locale has no row`() {
        seedCache(row(template.key, "en", subject = "Default {{username}}", bodyPlain = "Hi {{username}}"))
        whenever(settings.defaultLanguage()).thenReturn("en")

        val rendered = service.render(template, mapOf("username" to "alice"), locale = "fr")

        assertThat(rendered.subject).isEqualTo("Default alice")
        verify(settings).defaultLanguage()
    }

    @Test
    fun `render resolveStored with a bare locale skips stage 2 (base equals locale arm)`() {
        // locale = "en" has no '-', so substringBefore('-') == locale and stage 2 is skipped.
        seedCache() // no rows at all
        whenever(settings.defaultLanguage()).thenReturn("en")

        val rendered = service.render(
            template,
            mapOf(
                "username" to "alice",
                "resetLink" to "https://x",
                "expiresAtHuman" to "soon",
                "siteName" to "s.test",
            ),
            locale = "en",
        )

        // No stored row in any stage → enum default subject.
        assertThat(rendered.subject).isEqualTo(template.defaultSubject)
        verify(settings).defaultLanguage()
    }

    @Test
    fun `render resolveStored stage 3 with null locale skips per-locale lookup (locale null arm)`() {
        seedCache(row(template.key, "en", subject = "AppDefault {{username}}", bodyPlain = "Hi {{username}}"))
        whenever(settings.defaultLanguage()).thenReturn("en")

        val rendered = service.render(template, mapOf("username" to "alice"), locale = null)

        assertThat(rendered.subject).isEqualTo("AppDefault alice")
    }

    // ---- validateReferences edge branches (exercised via previewWith) ------

    @Test
    fun `validateReferences ignores comments, partials, and section closers (skip-first-char arm)`() {
        // {{!comment}} {{>partial}} {{/username}} are all non-references and must be skipped.
        // {{#username}}...{{/username}} is a valid documented section; the closer is skipped.
        val result = service.previewWith(
            template,
            subject = "Reset",
            bodyPlain = "{{! a comment }}{{#username}}Hi {{username}}{{/username}}",
            bodyHtml = null,
        )

        assertThat(result.rendered.bodyPlain).isEqualTo("Hi Alice")
    }

    @Test
    fun `validateReferences accepts triple-mustache references and trims the trailing brace`() {
        // {{{resetLink}}} is a documented placeholder rendered raw; the scanner must
        // strip the leading and trailing brace and still see it as 'resetLink'.
        val result = service.previewWith(
            template,
            subject = "Reset",
            bodyPlain = "Link: {{{resetLink}}}",
            bodyHtml = null,
        )

        assertThat(result.rendered.bodyPlain).contains("demo-token-123")
    }

    @Test
    fun `validateReferences ignores an unescape marker prefix (ampersand strip arm)`() {
        // {{&resetLink}} is the HTML-unescape form; payload strip removes the '&'.
        val result = service.previewWith(
            template,
            subject = "Reset",
            bodyPlain = "Link: {{&resetLink}}",
            bodyHtml = null,
        )

        assertThat(result.rendered.bodyPlain).contains("demo-token-123")
    }

    @Test
    fun `validateReferences tolerates literal text with no mustache tags at all (open less than zero break)`() {
        val result = service.previewWith(
            template,
            subject = "Static subject",
            bodyPlain = "Plain text body with no placeholders",
            bodyHtml = null,
        )

        assertThat(result.rendered.subject).isEqualTo("Static subject")
        assertThat(result.rendered.bodyPlain).isEqualTo("Plain text body with no placeholders")
    }
}
