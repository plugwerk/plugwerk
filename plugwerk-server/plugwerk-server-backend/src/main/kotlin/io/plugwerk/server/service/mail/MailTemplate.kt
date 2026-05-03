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

/**
 * Central registry of every email template the application can send (#436).
 *
 * Each entry declares its dotted [key], the hard-coded [defaultSubject] /
 * [defaultBodyTemplate] used as the last-resort fallback when no DB row
 * exists, and the closed set of [placeholders] valid in this template.
 *
 * Adding a new template:
 *   1. Add an entry here with sensible defaults + the placeholder set.
 *   2. Add an INSERT for `(key, locale='en')` to the next mail-template
 *      Liquibase migration so a fresh install ships with a usable subject
 *      and body.
 *   3. Send via `MailService.sendMailFromTemplate(MailTemplate.X, …)`
 *      (issue #437 — adds that overload on top of `sendMail`).
 *
 * ## Engine choice — Mustache (jmustache)
 *
 * Templates are admin-editable at runtime through the Templates page in
 * the Email admin area (#438). That makes the template-engine choice a
 * security decision, not just an ergonomics one:
 *
 *   - **Mustache** is logic-less by design. The expression syntax is
 *     `{{name}}`, `{{#section}}…{{/section}}`, and that's roughly it.
 *     An admin who edits a template cannot accidentally open a remote-
 *     code-execution surface — there is no expression language to abuse.
 *
 *   - **Thymeleaf**, **FreeMarker**, **Velocity** all have full
 *     expression languages with method-call escape hatches. Safe to use
 *     when templates are checked into source control; risky when they
 *     are operator-edited at runtime.
 *
 * jmustache (`com.samskivert:jmustache`) is the lightweight, zero-
 * Spring-dependency choice. It runs in strict mode in this project —
 * a `{{var}}` reference whose key is missing from the render-time map
 * raises an exception rather than silently substituting an empty
 * string. An auth email going out without `{{verificationLink}}` is a
 * bug, not a feature.
 *
 * ## i18n readiness (Issue #436 design discussion)
 *
 * The `mail_template` schema carries a `locale` column from day 1.
 * v1 only seeds `en`, but the storage shape is the i18n-ready one so
 * that adding language variants later is purely additive — no schema
 * migration, no row backfill. The render-time fallback chain is:
 *
 *   1. requested locale exact match  (e.g. `de-CH`)
 *   2. language-base                  (`de-CH` → `de`)
 *   3. application default            (`general.default_language`)
 *   4. enum-default below             ([defaultSubject] / [defaultBodyTemplate])
 *
 * Stage 4 guarantees a render never throws because no row was found —
 * critical for auth flows (registration, password reset).
 *
 * @property key dotted identifier, unique across the registry; used as
 *   the `template_key` column value and as the API path segment in #438.
 * @property defaultSubject hard-coded fallback subject. Mustache-templated
 *   like the DB-stored variant.
 * @property defaultBodyTemplate hard-coded fallback plaintext body.
 * @property placeholders closed set of `{{var}}` names this template
 *   may reference. The service validates both the DB-stored variant and
 *   the enum default at write time — typos surface as 400, not as a
 *   runtime "undefined variable" exception during a live send.
 */
enum class MailTemplate(
    val key: String,
    val defaultSubject: String,
    val defaultBodyTemplate: String,
    val placeholders: Set<String>,
) {
    /**
     * Verification email sent during opt-in self-registration (#420).
     * The recipient clicks `verificationLink` to activate their account.
     */
    AUTH_REGISTRATION_VERIFICATION(
        key = "auth.registration_verification",
        defaultSubject = "Verify your Plugwerk account",
        defaultBodyTemplate = """
            Hello {{username}},

            Welcome to Plugwerk! Please verify your email address by visiting the
            link below — the link is valid {{expiresAtHuman}}.

            {{verificationLink}}

            If you did not create an account, you can safely ignore this message.
        """.trimIndent(),
        placeholders = setOf("username", "verificationLink", "expiresAtHuman"),
    ),

    /**
     * Password-reset email triggered by the forgot-password flow (#421).
     * The recipient clicks `resetLink` to set a new password.
     */
    AUTH_PASSWORD_RESET(
        key = "auth.password_reset",
        defaultSubject = "Reset your Plugwerk password",
        defaultBodyTemplate = """
            Hello {{username}},

            We received a request to reset the password for your Plugwerk account.
            Click the link below to set a new password — it is valid {{expiresAtHuman}}.

            {{resetLink}}

            If you did not request a password reset, you can safely ignore this
            message — your existing password remains active.
        """.trimIndent(),
        placeholders = setOf("username", "resetLink", "expiresAtHuman"),
    ),
    ;

    companion object {
        private val BY_KEY: Map<String, MailTemplate> = entries.associateBy { it.key }

        /** Looks up a [MailTemplate] by its dotted identifier; null on unknown key. */
        fun byKey(key: String): MailTemplate? = BY_KEY[key]
    }
}
