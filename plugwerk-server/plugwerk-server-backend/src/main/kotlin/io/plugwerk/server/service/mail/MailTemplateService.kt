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

import com.samskivert.mustache.Mustache
import com.samskivert.mustache.MustacheException
import io.plugwerk.server.domain.MailTemplateEntity
import io.plugwerk.server.repository.MailTemplateRepository
import io.plugwerk.server.service.settings.ApplicationSettingsService
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime
import java.util.concurrent.atomic.AtomicReference

/**
 * Single source of truth for email-template content (#436).
 *
 * Reads the `mail_template` table into an in-memory snapshot at startup and
 * after every successful write. The snapshot is held in an [AtomicReference]
 * so reads are lock-free and consistent with the most recent write — same
 * pattern as [ApplicationSettingsService].
 *
 * ### Render-time fallback chain
 *
 * [render] walks four stages so a missing row can never crash an auth flow:
 *
 *   1. **Requested locale exact** — `(key, requestedLocale)` row
 *   2. **Language base** — `de-CH` falls back to `de`
 *   3. **Application default** — `general.default_language` (today: `en`)
 *   4. **Enum default** — [MailTemplate.defaultSubject] / [defaultBodyPlainTemplate]
 *      / [defaultBodyHtmlTemplate], hard-coded fallback that always exists.
 *
 * The locale parameter is optional; `null` means "use the application default
 * directly" and skips stages 1–2.
 *
 * ### Strict Mustache + dual compilers
 *
 * Two compilers are used because subject/plaintext-body must NOT be HTML-
 * escaped (they're plaintext) but the HTML body should auto-escape `{{var}}`
 * substitutions to prevent stored-XSS-style injection through user-controlled
 * vars (e.g. a username with `<script>` in it):
 *
 *   - [mustachePlain]: `escapeHTML=false` — for subject + plaintext body
 *   - [mustacheHtml]:  `escapeHTML=true`  — for HTML body
 *
 * In the HTML body, admins who genuinely want raw HTML in a substitution
 * use Mustache's triple-mustache `{{{var}}}` to opt out per reference.
 *
 * Both compilers run with `strictSections=true`, and the variable map
 * wrapper throws on missing keys — a `{{verificationLink}}` reference plus
 * a render-time map without that key produces an [IllegalArgumentException],
 * not a silently empty placeholder. Auth emails without their action link
 * are bugs.
 */
@Service
class MailTemplateService(
    private val repository: MailTemplateRepository,
    private val settings: ApplicationSettingsService,
) {

    private val log = LoggerFactory.getLogger(MailTemplateService::class.java)

    /** Live snapshot of `(template_key, locale) -> stored row`. Updated atomically on writes. */
    private val cache = AtomicReference<Map<CacheKey, StoredTemplate>>(emptyMap())

    /**
     * Strict-mode compiler for subject + plaintext body.
     *
     * - `strictSections=true` rejects unknown section tags
     * - missing variable references throw [MustacheException] by default
     *   (`No method or field with name 'X'`) — strict-on-missing is the
     *   built-in behaviour
     * - `nullValue("")` makes intentional null values render as empty
     *   strings (so callers can pass `mapOf("optional" to null)` without
     *   blowing up)
     * - `escapeHTML=false` because the consumer is a `text/plain` MIME
     *   part, not a browser
     */
    private val mustachePlain: Mustache.Compiler = Mustache.compiler()
        .strictSections(true)
        .nullValue("")
        .escapeHTML(false)

    /**
     * Strict-mode compiler for HTML body. Same strict-on-missing semantics
     * as [mustachePlain]; differs in that `escapeHTML=true` so admin-edited
     * templates that interpolate user-controlled vars (e.g.
     * `<p>Hello {{username}}</p>`) are not a stored-XSS surface. Templates
     * that genuinely need raw HTML in a substitution opt out per reference
     * with the triple-mustache form `{{{var}}}`.
     */
    private val mustacheHtml: Mustache.Compiler = Mustache.compiler()
        .strictSections(true)
        .nullValue("")
        .escapeHTML(true)

    @PostConstruct
    fun initialize() {
        refreshCache()
        log.info("MailTemplateService initialized with {} stored template variant(s)", cache.get().size)
    }

    private fun refreshCache() {
        val rows = repository.findAll()
        val snapshot = rows.associate { row ->
            CacheKey(row.templateKey, row.locale) to StoredTemplate(
                subject = row.subject,
                bodyPlain = row.bodyPlain,
                bodyHtml = row.bodyHtml,
                updatedAt = row.updatedAt,
                updatedBy = row.updatedBy,
            )
        }
        cache.set(snapshot)
    }

    /** Returns every stored template variant across every locale. */
    fun findAll(): List<MailTemplateView> {
        val current = cache.get()
        return current.entries.mapNotNull { (cacheKey, stored) ->
            val template = MailTemplate.byKey(cacheKey.templateKey) ?: return@mapNotNull null
            stored.toView(template, cacheKey.locale, source = TemplateSource.DATABASE)
        }
    }

    /** Returns every locale variant of a single template. Empty list when no row exists. */
    fun findByKey(template: MailTemplate): List<MailTemplateView> {
        val current = cache.get()
        return current.entries
            .filter { it.key.templateKey == template.key }
            .map { (cacheKey, stored) ->
                stored.toView(template, cacheKey.locale, source = TemplateSource.DATABASE)
            }
    }

    /** Returns the variant for a specific locale, or `null` if no row exists. */
    fun findByKeyAndLocale(template: MailTemplate, locale: String): MailTemplateView? {
        val stored = cache.get()[CacheKey(template.key, locale)] ?: return null
        return stored.toView(template, locale, source = TemplateSource.DATABASE)
    }

    /**
     * Inserts or updates a per-locale template variant.
     *
     * Validates that every `{{var}}` referenced in [subject], [bodyPlain],
     * or [bodyHtml] is declared in [MailTemplate.placeholders] — typos
     * surface as 400, not as a runtime exception during a live send.
     *
     * @param bodyHtml optional HTML alternative. `null` (the default) keeps
     *   the variant plaintext-only; a non-null value enables `multipart/alternative`
     *   delivery for sends that resolve to this variant.
     *
     * @throws IllegalArgumentException if a referenced variable is not in
     *   the template's documented placeholders, if [subject]/[bodyPlain] are
     *   blank, or if Mustache cannot parse a template at all.
     */
    @Transactional
    fun update(
        template: MailTemplate,
        locale: String,
        subject: String,
        bodyPlain: String,
        bodyHtml: String? = null,
        updatedBy: String?,
    ): MailTemplateView {
        require(locale.isNotBlank()) { "locale must not be blank" }
        require(subject.isNotBlank()) { "subject must not be blank" }
        require(bodyPlain.isNotBlank()) { "bodyPlain must not be blank" }

        val errors = buildList {
            addAll(validateReferences(template, "subject", subject, mustachePlain))
            addAll(validateReferences(template, "bodyPlain", bodyPlain, mustachePlain))
            if (bodyHtml != null) {
                require(bodyHtml.isNotBlank()) {
                    "bodyHtml must not be blank when provided (use null for plaintext-only)"
                }
                addAll(validateReferences(template, "bodyHtml", bodyHtml, mustacheHtml))
            }
        }
        if (errors.isNotEmpty()) {
            throw IllegalArgumentException(
                "Template '${template.key}' references undocumented variables: " +
                    "${errors.joinToString("; ")}. Allowed: ${template.placeholders}",
            )
        }

        val existing = repository.findByTemplateKeyAndLocale(template.key, locale).orElse(null)
        val entity = existing?.apply {
            this.subject = subject
            this.bodyPlain = bodyPlain
            this.bodyHtml = bodyHtml
            this.updatedBy = updatedBy
        } ?: MailTemplateEntity(
            templateKey = template.key,
            locale = locale,
            subject = subject,
            bodyPlain = bodyPlain,
            bodyHtml = bodyHtml,
            updatedBy = updatedBy,
        )
        repository.save(entity)
        refreshCache()
        return findByKeyAndLocale(template, locale)
            ?: error("Template ${template.key}/$locale missing immediately after save — refreshCache bug?")
    }

    /**
     * Removes the per-locale override row for [template] / [locale]. Render
     * subsequently falls back to the enum default (stage 4 of the fallback
     * chain).
     *
     * Idempotent — succeeds even when no row exists.
     *
     * @return `true` if a row was actually deleted, `false` if no override
     *   was present.
     */
    @Transactional
    fun delete(template: MailTemplate, locale: String): Boolean {
        require(locale.isNotBlank()) { "locale must not be blank" }
        val deleted = repository.deleteByTemplateKeyAndLocale(template.key, locale)
        if (deleted > 0) {
            refreshCache()
        }
        return deleted > 0
    }

    /**
     * Returns the *effective* view for one template at a single locale: the
     * stored override when present, otherwise a synthesised view backed by
     * the enum defaults. Used by the admin UI so the list view always shows
     * one row per registered template, with `source` discriminating override
     * vs default.
     */
    fun findEffective(template: MailTemplate, locale: String): MailTemplateView = findByKeyAndLocale(template, locale)
        ?: MailTemplateView(
            key = template,
            locale = locale,
            subject = template.defaultSubject,
            bodyPlain = template.defaultBodyPlainTemplate,
            bodyHtml = template.defaultBodyHtmlTemplate,
            source = TemplateSource.DEFAULT,
            updatedAt = null,
            updatedBy = null,
        )

    /**
     * Renders a draft directly without consulting the cache or DB (#438).
     *
     * Powers the admin "Preview" feature — the editor's in-flight subject /
     * bodyPlain / bodyHtml strings come in unsaved, get the same placeholder
     * validation as [update] (so a typo surfaces here too, not first when
     * the admin clicks Save), and render with the same dual-compiler setup
     * as production.
     *
     * Sample vars merge with [MailTemplate.previewSampleVars] as the base
     * — the caller can override any subset (admin-edited preview values),
     * missing keys fall back to the registry default. That keeps the
     * "fresh open" preview meaningful without forcing the admin to fill in
     * every variable before seeing anything.
     *
     * @throws IllegalArgumentException same surface as [update] — undocumented
     *   placeholder, malformed Mustache syntax, blank required field.
     */
    fun previewWith(
        template: MailTemplate,
        subject: String,
        bodyPlain: String,
        bodyHtml: String?,
        sampleVarsOverride: Map<String, String> = emptyMap(),
    ): PreviewResult {
        require(subject.isNotBlank()) { "subject must not be blank" }
        require(bodyPlain.isNotBlank()) { "bodyPlain must not be blank" }
        if (bodyHtml != null) {
            require(bodyHtml.isNotBlank()) {
                "bodyHtml must not be blank when provided (use null for plaintext-only)"
            }
        }

        val errors = buildList {
            addAll(validateReferences(template, "subject", subject, mustachePlain))
            addAll(validateReferences(template, "bodyPlain", bodyPlain, mustachePlain))
            if (bodyHtml != null) {
                addAll(validateReferences(template, "bodyHtml", bodyHtml, mustacheHtml))
            }
        }
        if (errors.isNotEmpty()) {
            throw IllegalArgumentException(
                "Template '${template.key}' references undocumented variables: " +
                    "${errors.joinToString("; ")}. Allowed: ${template.placeholders}",
            )
        }

        val effectiveVars: Map<String, String> = template.previewSampleVars + sampleVarsOverride

        // jmustache surfaces some malformed-template states as plain
        // RuntimeExceptions (NullPointerException, IndexOutOfBoundsException
        // when the parser walks off the end of an unterminated tag). Catch
        // every Throwable and re-wrap as IllegalArgumentException so the
        // controller's 400 handler renders the actual cause to the operator
        // — without this, the preview returns a generic 500 "An unexpected
        // error occurred" and the editor surface is useless for debugging.
        val rendered = try {
            RenderedMail(
                subject = mustachePlain.compile(subject).execute(effectiveVars),
                bodyPlain = mustachePlain.compile(bodyPlain).execute(effectiveVars),
                bodyHtml = bodyHtml?.let { mustacheHtml.compile(it).execute(effectiveVars) },
            )
        } catch (ex: MustacheException) {
            throw IllegalArgumentException(
                "Template '${template.key}' has malformed Mustache syntax: ${ex.message}",
                ex,
            )
        } catch (ex: RuntimeException) {
            throw IllegalArgumentException(
                "Template '${template.key}' could not be rendered: " +
                    "${ex.javaClass.simpleName}: ${ex.message ?: "no further detail"}",
                ex,
            )
        }
        return PreviewResult(rendered = rendered, sampleVars = effectiveVars)
    }

    /**
     * Renders a template against [vars] using the locale-fallback chain.
     *
     * Returns both the plaintext and (optionally) the HTML body. The HTML
     * body is `null` iff the resolved template variant has no `body_html`
     * row column AND the enum default's [MailTemplate.defaultBodyHtmlTemplate]
     * is also `null` — the mail layer then sends a single-part `text/plain`
     * message instead of `multipart/alternative`.
     *
     * @param template registry entry to render.
     * @param vars variable map; every key referenced in the chosen variant
     *   must be present, otherwise [IllegalArgumentException] is thrown.
     * @param locale optional BCP-47 tag. `null` skips per-locale lookup and
     *   uses the application-default variant or the enum default.
     */
    fun render(template: MailTemplate, vars: Map<String, Any?>, locale: String? = null): RenderedMail {
        // Per-row fallback: a DB row is treated as the complete definition.
        // If a stored variant exists for this (key, locale) chain but its
        // bodyHtml is null, the message is plaintext-only — we do NOT mix
        // the operator's plaintext with the enum's HTML default. Operators
        // who deliberately leave HTML blank get plaintext-only, as expected.
        val stored = resolveStored(template, locale)
        val source = if (stored != null) "DATABASE($locale)" else "ENUM_DEFAULT"
        val subjectTpl: String
        val plainTpl: String
        val htmlTpl: String?
        if (stored != null) {
            subjectTpl = stored.subject
            plainTpl = stored.bodyPlain
            htmlTpl = stored.bodyHtml
        } else {
            subjectTpl = template.defaultSubject
            plainTpl = template.defaultBodyPlainTemplate
            htmlTpl = template.defaultBodyHtmlTemplate
        }

        val rendered = try {
            RenderedMail(
                subject = mustachePlain.compile(subjectTpl).execute(vars),
                bodyPlain = mustachePlain.compile(plainTpl).execute(vars),
                bodyHtml = htmlTpl?.let { mustacheHtml.compile(it).execute(vars) },
            )
        } catch (ex: MustacheException) {
            throw IllegalArgumentException(
                "Mustache failed to render template '${template.key}' (source=$source): ${ex.message}",
                ex,
            )
        }
        log.debug(
            "Rendered template {} (source={}, html={})",
            template.key,
            source,
            rendered.bodyHtml != null,
        )
        return rendered
    }

    /**
     * Walks the locale-fallback chain (stages 1–3 of the render contract).
     * Returns `null` when no row matches — the caller falls back to the
     * enum-default values (stage 4).
     */
    private fun resolveStored(template: MailTemplate, locale: String?): StoredTemplate? {
        val current = cache.get()
        if (locale != null) {
            // Stage 1: exact match
            current[CacheKey(template.key, locale)]?.let { return it }
            // Stage 2: language-base — `de-CH` → `de`
            val base = locale.substringBefore('-')
            if (base != locale) {
                current[CacheKey(template.key, base)]?.let { return it }
            }
        }
        // Stage 3: application default language
        val appDefault = settings.defaultLanguage()
        return current[CacheKey(template.key, appDefault)]
    }

    /**
     * Walks every `{{ref}}` Mustache tag in [source] and returns the names
     * of any references that are NOT in the template's documented placeholders.
     *
     * Uses a small string scanner rather than hooking jmustache's
     * VariableFetcher API — the fetcher protocol only fires per render call
     * via a custom Collector, which would mean instantiating an alternate
     * compiler just for validation. Mustache's tag syntax is regular enough
     * that a straight scan is the smaller, more obvious tool.
     *
     * @param compiler the strict compiler that will eventually render this
     *   field — passed in so the syntax-error pre-check uses the same
     *   parser settings (escapeHTML on/off doesn't affect parsing, but
     *   keeping the call symmetric removes a gotcha).
     */
    private fun validateReferences(
        template: MailTemplate,
        fieldName: String,
        source: String,
        compiler: Mustache.Compiler,
    ): List<String> {
        // Compile once to surface syntax errors at write time, not at send time.
        // Catch every Throwable: jmustache occasionally throws plain
        // RuntimeExceptions (e.g. on unterminated tags) instead of
        // MustacheException, and we want the operator-facing 400 either way.
        try {
            compiler.compile(source)
        } catch (ex: MustacheException) {
            throw IllegalArgumentException(
                "Template '${template.key}' $fieldName has malformed Mustache syntax: ${ex.message}",
                ex,
            )
        } catch (ex: RuntimeException) {
            throw IllegalArgumentException(
                "Template '${template.key}' $fieldName could not be parsed: " +
                    "${ex.javaClass.simpleName}: ${ex.message ?: "no further detail"}",
                ex,
            )
        }

        val unknown = sortedSetOf<String>()
        var i = 0
        while (i < source.length) {
            val open = source.indexOf("{{", i)
            if (open < 0) break
            val close = source.indexOf("}}", open + 2)
            if (close < 0) break
            val inner = source.substring(open + 2, close).trim()
            // Skip comments `{{!…}}`, partials `{{>…}}`, and section closers
            // `{{/…}}` — none of them are variable references.
            val first = inner.firstOrNull()
            if (first == '!' || first == '>' || first == '/') {
                i = close + 2
                continue
            }
            // Strip optional section openers `#` / `^` and unescape marker `&`;
            // triple-mustache `{{{name}}}` is naturally handled because the inner
            // string then starts with `{name` after our `{{` skip — drop the leading `{`.
            val payload = inner
                .removePrefix("#").removePrefix("^").removePrefix("&").removePrefix("{")
                .trim()
            // A reference name is the leading run of identifier characters.
            // Dots are allowed for nested context (e.g. `{{user.email}}`) but
            // we only validate the top-level segment against placeholders.
            val name = payload.takeWhile { it.isLetterOrDigit() || it == '_' }
            if (name.isNotEmpty() && name !in template.placeholders) {
                unknown.add(name)
            }
            i = close + 2
            // Triple-mustache `{{{name}}}` ends with `}}}` — skip the trailing `}`.
            if (i < source.length && source[i] == '}') i++
        }
        return unknown.map { "$fieldName references {{$it}}" }
    }

    private data class CacheKey(val templateKey: String, val locale: String)

    private data class StoredTemplate(
        val subject: String,
        val bodyPlain: String,
        val bodyHtml: String?,
        val updatedAt: OffsetDateTime,
        val updatedBy: String?,
    ) {
        fun toView(template: MailTemplate, locale: String, source: TemplateSource): MailTemplateView = MailTemplateView(
            key = template,
            locale = locale,
            subject = subject,
            bodyPlain = bodyPlain,
            bodyHtml = bodyHtml,
            source = source,
            updatedAt = updatedAt,
            updatedBy = updatedBy,
        )
    }
}

/**
 * Outcome of [MailTemplateService.render].
 *
 * @property bodyHtml `null` when the resolved template variant has no HTML
 *   alternative — the mail layer then sends a single-part `text/plain`
 *   message instead of `multipart/alternative`.
 */
data class RenderedMail(val subject: String, val bodyPlain: String, val bodyHtml: String?)

/**
 * Outcome of [MailTemplateService.previewWith] (#438): the rendered draft
 * plus the actual variable map used. The map is surfaced so the admin UI
 * can show "preview was rendered with these values" and let the operator
 * tweak them for the next refresh.
 */
data class PreviewResult(val rendered: RenderedMail, val sampleVars: Map<String, String>)

/** Origin of the variant returned by `findAll` / `findByKey` / `findByKeyAndLocale`. */
enum class TemplateSource {
    /** A row exists in `mail_template` for this `(key, locale)`. */
    DATABASE,

    /**
     * No row exists; the value would come from [MailTemplate.defaultSubject]
     * / [MailTemplate.defaultBodyPlainTemplate] / [MailTemplate.defaultBodyHtmlTemplate].
     * Currently surfaced only by the render path; the find-* methods return
     * `null` / empty list for missing rows so the caller can decide whether
     * to materialise the enum default.
     */
    DEFAULT,
}

/**
 * A point-in-time view of one template variant. Mirrors `SettingSnapshot`
 * in shape so the Templates admin page (#438) can build on the same patterns
 * the General settings page already uses.
 *
 * @property bodyHtml `null` when the variant is plaintext-only.
 */
data class MailTemplateView(
    val key: MailTemplate,
    val locale: String,
    val subject: String,
    val bodyPlain: String,
    val bodyHtml: String?,
    val source: TemplateSource,
    val updatedAt: OffsetDateTime?,
    val updatedBy: String?,
)
