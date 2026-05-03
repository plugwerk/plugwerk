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
 *   4. **Enum default** — [MailTemplate.defaultSubject] / [defaultBodyTemplate],
 *      hard-coded fallback that always exists
 *
 * The locale parameter is optional; `null` means "use the application default
 * directly" and skips stages 1–2.
 *
 * ### Strict Mustache
 *
 * The renderer is configured with `strictSections=true` so unknown section
 * tags fail loudly. Variable lookups are wrapped to throw on missing keys —
 * a `{{verificationLink}}` reference in the template plus a render-time map
 * without that key produces an [IllegalArgumentException], not a silently
 * empty placeholder. Auth emails without their action link are bugs.
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
     * Strict-mode Mustache compiler. `strictSections(true)` rejects unknown
     * section tags; the missing-key behaviour is enforced at the render-time
     * variable map level, see [render].
     */
    private val mustache: Mustache.Compiler = Mustache.compiler()
        .strictSections(true)
        .escapeHTML(false) // plaintext bodies — HTML escaping would mangle special chars in subjects/bodies

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
                body = row.body,
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
     * Validates that every `{{var}}` referenced in [subject] or [body] is
     * declared in [MailTemplate.placeholders] — typos surface as 400, not as
     * a runtime exception during a live send.
     *
     * @throws IllegalArgumentException if a referenced variable is not in
     *   the template's documented placeholders, or if [subject]/[body] are
     *   blank, or if Mustache cannot parse the template at all.
     */
    @Transactional
    fun update(
        template: MailTemplate,
        locale: String,
        subject: String,
        body: String,
        updatedBy: String?,
    ): MailTemplateView {
        require(locale.isNotBlank()) { "locale must not be blank" }
        require(subject.isNotBlank()) { "subject must not be blank" }
        require(body.isNotBlank()) { "body must not be blank" }

        // Compile both halves so a malformed template (`{{#unclosed`) fails
        // here, not at send time. The errors-on-unknown-vars check is below.
        val subjectErrors = validateReferences(template, "subject", subject)
        val bodyErrors = validateReferences(template, "body", body)
        val errors = subjectErrors + bodyErrors
        if (errors.isNotEmpty()) {
            throw IllegalArgumentException(
                "Template '${template.key}' references undocumented variables: " +
                    "${errors.joinToString("; ")}. Allowed: ${template.placeholders}",
            )
        }

        val existing = repository.findByTemplateKeyAndLocale(template.key, locale).orElse(null)
        val entity = existing?.apply {
            this.subject = subject
            this.body = body
            this.updatedBy = updatedBy
        } ?: MailTemplateEntity(
            templateKey = template.key,
            locale = locale,
            subject = subject,
            body = body,
            updatedBy = updatedBy,
        )
        repository.save(entity)
        refreshCache()
        return findByKeyAndLocale(template, locale)
            ?: error("Template ${template.key}/$locale missing immediately after save — refreshCache bug?")
    }

    /**
     * Renders a template against [vars] using the locale-fallback chain.
     *
     * @param template registry entry to render.
     * @param vars variable map; every key referenced in the chosen variant
     *   must be present, otherwise [IllegalArgumentException] is thrown.
     * @param locale optional BCP-47 tag. `null` skips per-locale lookup and
     *   uses the application-default variant or the enum default.
     */
    fun render(template: MailTemplate, vars: Map<String, Any?>, locale: String? = null): RenderedMail {
        val stored = resolveStored(template, locale)
        val source = if (stored != null) "DATABASE($locale)" else "ENUM_DEFAULT"
        val subjectTpl = stored?.subject ?: template.defaultSubject
        val bodyTpl = stored?.body ?: template.defaultBodyTemplate

        val safeVars = StrictVarMap(template.key, vars)
        val rendered = try {
            RenderedMail(
                subject = mustache.compile(subjectTpl).execute(safeVars),
                body = mustache.compile(bodyTpl).execute(safeVars),
            )
        } catch (ex: MustacheException) {
            throw IllegalArgumentException(
                "Mustache failed to render template '${template.key}' (source=$source): ${ex.message}",
                ex,
            )
        }
        log.debug("Rendered template {} (source={})", template.key, source)
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
     */
    private fun validateReferences(template: MailTemplate, fieldName: String, source: String): List<String> {
        // Compile once to surface syntax errors at write time, not at send time.
        try {
            mustache.compile(source)
        } catch (ex: MustacheException) {
            throw IllegalArgumentException(
                "Template '${template.key}' $fieldName has malformed Mustache syntax: ${ex.message}",
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
        val body: String,
        val updatedAt: OffsetDateTime,
        val updatedBy: String?,
    ) {
        fun toView(template: MailTemplate, locale: String, source: TemplateSource): MailTemplateView = MailTemplateView(
            key = template,
            locale = locale,
            subject = subject,
            body = body,
            source = source,
            updatedAt = updatedAt,
            updatedBy = updatedBy,
        )
    }

    /**
     * Strict variable map wrapper — Mustache lookups for keys not present
     * in the underlying map throw, so a missing `{{var}}` is a render-time
     * error rather than a silent empty string.
     */
    private class StrictVarMap(private val templateKey: String, private val backing: Map<String, Any?>) :
        AbstractMap<String, Any?>() {
        override val entries: Set<Map.Entry<String, Any?>> get() = backing.entries

        override fun containsKey(key: String): Boolean = backing.containsKey(key)

        override fun get(key: String): Any? {
            if (!backing.containsKey(key)) {
                throw IllegalArgumentException(
                    "Template '$templateKey' references {{$key}} but it was not provided in the render vars",
                )
            }
            // jmustache can't render `null`; treat null as "intentionally absent"
            // and substitute an empty string. Callers wanting "show this is
            // missing" should pass a placeholder string explicitly.
            return backing[key] ?: ""
        }
    }
}

/** Outcome of [MailTemplateService.render] — the materialised subject + body. */
data class RenderedMail(val subject: String, val body: String)

/** Origin of the variant returned by `findAll` / `findByKey` / `findByKeyAndLocale`. */
enum class TemplateSource {
    /** A row exists in `mail_template` for this `(key, locale)`. */
    DATABASE,

    /**
     * No row exists; the value would come from [MailTemplate.defaultSubject]
     * / [MailTemplate.defaultBodyTemplate]. Currently surfaced only by the
     * render path; the find-* methods return `null` / empty list for missing
     * rows so the caller can decide whether to materialise the enum default.
     */
    DEFAULT,
}

/**
 * A point-in-time view of one template variant. Mirrors `SettingSnapshot`
 * in shape so the Templates admin page (#438) can build on the same patterns
 * the General settings page already uses.
 */
data class MailTemplateView(
    val key: MailTemplate,
    val locale: String,
    val subject: String,
    val body: String,
    val source: TemplateSource,
    val updatedAt: OffsetDateTime?,
    val updatedBy: String?,
)
