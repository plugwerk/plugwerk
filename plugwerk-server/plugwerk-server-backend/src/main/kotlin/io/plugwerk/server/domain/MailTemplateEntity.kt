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
package io.plugwerk.server.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.UpdateTimestamp
import org.hibernate.annotations.UuidGenerator
import java.time.OffsetDateTime
import java.util.UUID

/**
 * One per-locale email template variant (#436).
 *
 * The [templateKey] is the dotted identifier registered in
 * [io.plugwerk.server.service.mail.MailTemplate]; [locale] is a BCP-47
 * language tag (e.g. `en`, `de`, `de-CH`). The pair is unique — every
 * `(key, locale)` has at most one row.
 *
 * Storage is locale-explicit from day 1 even though only `en` is seeded
 * today. The composite-unique design means future i18n work just inserts
 * additional rows without any schema migration (no NULL-locale row to
 * backfill, no constraint to retroactively install). See the issue
 * thread on #436 for the i18n-readiness rationale.
 *
 * @property templateKey dotted registry key, e.g. `auth.password_reset`. Unique per [locale].
 * @property locale BCP-47 language tag. The render-time fallback chain
 *   walks `requested → language-base → general.default_language → enum-default`,
 *   so a row can be missing without breaking sends — see
 *   [io.plugwerk.server.service.mail.MailTemplateService.render].
 * @property subject Mustache-templated subject line (`{{var}}` placeholders allowed).
 * @property bodyPlain Mustache-templated plaintext body. Always required —
 *   spam filters and accessibility-focused clients still benefit from a
 *   real plaintext body even when an HTML alternative is present.
 * @property bodyHtml Optional Mustache-templated HTML body. When set, the
 *   mail layer assembles a `multipart/alternative` MIME message with both
 *   parts; when null, the message is single-part `text/plain` with only
 *   [bodyPlain].
 */
@Entity
@Table(
    name = "mail_template",
    uniqueConstraints = [
        UniqueConstraint(name = "uq_mail_template_key_locale", columnNames = ["template_key", "locale"]),
    ],
    indexes = [
        // Supports `findByTemplateKey(...)` which lists every locale variant
        // of a given template — used by the future Templates admin page (#438).
        Index(name = "idx_mail_template_key", columnList = "template_key"),
    ],
)
class MailTemplateEntity(
    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(name = "id", updatable = false)
    var id: UUID? = null,

    @Column(name = "template_key", nullable = false, length = 128, updatable = false)
    var templateKey: String,

    @Column(name = "locale", nullable = false, length = 16, updatable = false)
    var locale: String,

    @Column(name = "subject", nullable = false, columnDefinition = "text")
    var subject: String,

    @Column(name = "body_plain", nullable = false, columnDefinition = "text")
    var bodyPlain: String,

    @Column(name = "body_html", nullable = true, columnDefinition = "text")
    var bodyHtml: String? = null,

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    var updatedAt: OffsetDateTime = OffsetDateTime.now(),

    @Column(name = "updated_by", nullable = true, length = 255)
    var updatedBy: String? = null,
)
