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
 * @property defaultBodyPlainTemplate hard-coded fallback plaintext body.
 *   Always required — spam filters and accessibility-focused clients still
 *   benefit from a real plaintext body even when an HTML alternative is
 *   present.
 * @property defaultBodyHtmlTemplate optional hard-coded fallback HTML body.
 *   When non-null, the mail layer assembles a `multipart/alternative` MIME
 *   message with both parts; when null, the message is single-part
 *   `text/plain` with only [defaultBodyPlainTemplate]. Templates with no
 *   meaningful HTML representation (e.g. internal admin alerts) leave this
 *   `null`.
 * @property placeholders closed set of `{{var}}` names this template
 *   may reference. The service validates the DB-stored subject + plain +
 *   html variants at write time — typos surface as 400, not as a runtime
 *   "undefined variable" exception during a live send.
 * @property previewSampleVars demo values rendered into the admin preview
 *   (#438). Every name in [placeholders] must have an entry — verified by
 *   `init` so a forgotten sample fails the build, not the preview at
 *   runtime. The values are intentionally obviously-fake so an admin who
 *   pastes the rendered output into a real test send realises it isn't
 *   real data.
 */
enum class MailTemplate(
    val key: String,
    val defaultSubject: String,
    val defaultBodyPlainTemplate: String,
    val defaultBodyHtmlTemplate: String?,
    val placeholders: Set<String>,
    val previewSampleVars: Map<String, String>,
) {
    /**
     * Verification email sent during opt-in self-registration (#420).
     * The recipient clicks `verificationLink` to activate their account.
     */
    AUTH_REGISTRATION_VERIFICATION(
        key = "auth.registration_verification",
        defaultSubject = "Verify your Plugwerk account",
        defaultBodyPlainTemplate = """
            Hi {{username}},

            Please verify your email address to finish setting up your Plugwerk
            account. This link expires {{expiresAtHuman}}.

            {{verificationLink}}

            If you didn't create an account, you can safely ignore this message.

            —
            Sent by Plugwerk · {{siteName}}
            You're receiving this because you registered for Plugwerk.
        """.trimIndent(),
        defaultBodyHtmlTemplate = EmailLayoutBuilder.wrap(
            contentHtml = """
              <p style="margin:0 0 16px;">Hi {{username}},</p>
              <p style="margin:0 0 16px;">Please verify your email address to finish setting up your Plugwerk account. This link expires {{expiresAtHuman}}.</p>
              <p style="margin:24px 0 8px;font-size:13px;color:#6F6F6F;">If the button doesn't work, paste this link into your browser:</p>
              <p style="margin:0;font-size:13px;word-break:break-all;"><a href="{{verificationLink}}" class="pw-fallback-link" style="color:#0F62FE;text-decoration:underline;">{{verificationLink}}</a></p>
              <p style="margin:24px 0 0;font-size:13px;color:#6F6F6F;">If you didn't create an account, you can safely ignore this message.</p>
            """.trimIndent(),
            ctaUrl = "{{verificationLink}}",
            ctaText = "Verify email",
            footerLine2 = "You're receiving this because you registered for Plugwerk.",
        ),
        placeholders = setOf("username", "verificationLink", "expiresAtHuman", "siteName"),
        previewSampleVars = mapOf(
            "username" to "Alice",
            "verificationLink" to "https://app.plugwerk.test/verify?token=demo-token-123",
            "expiresAtHuman" to "in 24 hours",
            "siteName" to "marketplace.plugwerk.test",
        ),
    ),

    /**
     * Password-reset email triggered by the forgot-password flow (#421).
     * The recipient clicks `resetLink` to set a new password.
     */
    AUTH_PASSWORD_RESET(
        key = "auth.password_reset",
        defaultSubject = "Reset your Plugwerk password",
        defaultBodyPlainTemplate = """
            Hi {{username}},

            We received a request to reset the password on your Plugwerk account.
            Click the link below to choose a new one — this link is valid
            {{expiresAtHuman}}.

            {{resetLink}}

            If you didn't request a password reset, you can safely ignore this
            message; your existing password remains active.

            —
            Sent by Plugwerk · {{siteName}}
            You're receiving this because someone requested a password reset on Plugwerk.
        """.trimIndent(),
        defaultBodyHtmlTemplate = EmailLayoutBuilder.wrap(
            contentHtml = """
              <p style="margin:0 0 16px;">Hi {{username}},</p>
              <p style="margin:0 0 16px;">We received a request to reset the password on your Plugwerk account. Click the button below to choose a new one — this link is valid {{expiresAtHuman}}.</p>
              <p style="margin:24px 0 8px;font-size:13px;color:#6F6F6F;">If the button doesn't work, paste this link into your browser:</p>
              <p style="margin:0;font-size:13px;word-break:break-all;"><a href="{{resetLink}}" class="pw-fallback-link" style="color:#0F62FE;text-decoration:underline;">{{resetLink}}</a></p>
              <p style="margin:24px 0 0;font-size:13px;color:#6F6F6F;">If you didn't request a password reset, you can safely ignore this message; your existing password remains active.</p>
            """.trimIndent(),
            ctaUrl = "{{resetLink}}",
            ctaText = "Reset password",
            footerLine2 = "You're receiving this because someone requested a password reset on Plugwerk.",
        ),
        placeholders = setOf("username", "resetLink", "expiresAtHuman", "siteName"),
        previewSampleVars = mapOf(
            "username" to "Alice",
            "resetLink" to "https://app.plugwerk.test/reset?token=demo-token-123",
            "expiresAtHuman" to "in 30 minutes",
            "siteName" to "marketplace.plugwerk.test",
        ),
    ),

    /**
     * Admin-initiated password-reset email triggered from the Admin → Users
     * surface (#450). Differs from [AUTH_PASSWORD_RESET] in two intentional
     * ways:
     *  - body copy makes the **admin-initiated** part obvious so a recipient
     *    who did not request a reset themselves understands who triggered it,
     *  - body explicitly states that every active session has been signed
     *    out — operationally true and a reassurance signal in the case of a
     *    suspected-compromise reset.
     *
     * Reuses the same `{{resetLink}}` placeholder shape as the public flow,
     * so the existing `ResetPasswordPage.tsx` consumes the link unchanged.
     */
    AUTH_ADMIN_PASSWORD_RESET(
        key = "auth.admin_password_reset",
        defaultSubject = "An administrator reset your Plugwerk password",
        defaultBodyPlainTemplate = """
            Hi {{username}},

            A Plugwerk administrator has initiated a password reset on your
            account. Click the link below to choose a new password — the link
            is valid {{expiresAtHuman}}.

            {{resetLink}}

            For your security, every active Plugwerk session on this account
            has been signed out. You did not request this reset yourself; it
            was triggered by a site administrator. If this was unexpected,
            contact your administrator.

            —
            Sent by Plugwerk · {{siteName}}
            You're receiving this because an administrator initiated a password reset on your account.
        """.trimIndent(),
        defaultBodyHtmlTemplate = EmailLayoutBuilder.wrap(
            contentHtml = """
              <p style="margin:0 0 16px;">Hi {{username}},</p>
              <p style="margin:0 0 16px;">A Plugwerk administrator has initiated a password reset on your account. Click the button below to choose a new password — this link is valid {{expiresAtHuman}}.</p>
              <p style="margin:24px 0 8px;font-size:13px;color:#6F6F6F;">If the button doesn't work, paste this link into your browser:</p>
              <p style="margin:0;font-size:13px;word-break:break-all;"><a href="{{resetLink}}" class="pw-fallback-link" style="color:#0F62FE;text-decoration:underline;">{{resetLink}}</a></p>
              <p style="margin:24px 0 0;font-size:13px;color:#6F6F6F;">For your security, every active Plugwerk session on this account has been signed out. If this was unexpected, contact your administrator.</p>
            """.trimIndent(),
            ctaUrl = "{{resetLink}}",
            ctaText = "Choose new password",
            footerLine2 = "You're receiving this because an administrator initiated a password reset on your account.",
        ),
        placeholders = setOf("username", "resetLink", "expiresAtHuman", "siteName"),
        previewSampleVars = mapOf(
            "username" to "Alice",
            "resetLink" to "https://app.plugwerk.test/reset-password?token=demo-token-123",
            "expiresAtHuman" to "in 30 minutes",
            "siteName" to "marketplace.plugwerk.test",
        ),
    ),
    ;

    init {
        require(previewSampleVars.keys == placeholders) {
            "MailTemplate.$name: previewSampleVars must cover every placeholder. " +
                "Missing: ${placeholders - previewSampleVars.keys}, " +
                "extra: ${previewSampleVars.keys - placeholders}"
        }
    }

    companion object {
        private val BY_KEY: Map<String, MailTemplate> = entries.associateBy { it.key }

        /** Looks up a [MailTemplate] by its dotted identifier; null on unknown key. */
        fun byKey(key: String): MailTemplate? = BY_KEY[key]
    }
}
