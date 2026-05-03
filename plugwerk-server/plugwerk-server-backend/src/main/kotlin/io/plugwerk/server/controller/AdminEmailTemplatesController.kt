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
package io.plugwerk.server.controller

import io.plugwerk.api.AdminEmailTemplatesApi
import io.plugwerk.api.model.MailTemplateListResponse
import io.plugwerk.api.model.MailTemplatePreviewRequest
import io.plugwerk.api.model.MailTemplatePreviewResponse
import io.plugwerk.api.model.MailTemplateResponse
import io.plugwerk.api.model.MailTemplateUpdateRequest
import io.plugwerk.server.security.NamespaceAuthorizationService
import io.plugwerk.server.security.currentAuthentication
import io.plugwerk.server.service.mail.MailTemplate
import io.plugwerk.server.service.mail.MailTemplateService
import io.plugwerk.server.service.mail.MailTemplateView
import io.plugwerk.server.service.mail.TemplateSource
import io.plugwerk.server.service.settings.ApplicationSettingsService
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Admin endpoints for editing transactional email templates (#438).
 *
 * Surfaces the registered [MailTemplate] entries with their current effective
 * values (DB override or enum default) plus the seeded enum defaults so the
 * UI can render a diff and offer "Reset to default" without a second round
 * trip. The registry is closed: admins cannot add new template keys at
 * runtime — only edit existing ones. Unknown keys yield 404.
 *
 * Locale handling is intentionally implicit in v1: every read and write
 * targets the application's default language ([ApplicationSettingsService.defaultLanguage]).
 * The DB schema already carries `(template_key, locale)` so a future i18n
 * picker can add a `?locale=` query parameter without a migration.
 */
@RestController
@RequestMapping("/api/v1")
class AdminEmailTemplatesController(
    private val templates: MailTemplateService,
    private val settings: ApplicationSettingsService,
    private val namespaceAuthorizationService: NamespaceAuthorizationService,
) : AdminEmailTemplatesApi {

    private val log = LoggerFactory.getLogger(AdminEmailTemplatesController::class.java)

    @PreAuthorize("@namespaceAuthorizationService.isCurrentUserSuperadmin()")
    override fun listMailTemplates(): ResponseEntity<MailTemplateListResponse> {
        requireSuperadmin()
        val locale = settings.defaultLanguage()
        val responses = MailTemplate.entries.map { template ->
            templates.findEffective(template, locale).toResponse(template)
        }
        return ResponseEntity.ok(MailTemplateListResponse(templates = responses))
    }

    @PreAuthorize("@namespaceAuthorizationService.isCurrentUserSuperadmin()")
    override fun getMailTemplate(key: String): ResponseEntity<MailTemplateResponse> {
        requireSuperadmin()
        val template = MailTemplate.byKey(key) ?: throw MailTemplateNotFoundException(key)
        val locale = settings.defaultLanguage()
        return ResponseEntity.ok(templates.findEffective(template, locale).toResponse(template))
    }

    @PreAuthorize("@namespaceAuthorizationService.isCurrentUserSuperadmin()")
    override fun updateMailTemplate(
        key: String,
        mailTemplateUpdateRequest: MailTemplateUpdateRequest,
    ): ResponseEntity<MailTemplateResponse> {
        val principal = requireSuperadmin()
        val template = MailTemplate.byKey(key) ?: throw MailTemplateNotFoundException(key)
        val locale = settings.defaultLanguage()
        log.info("Superadmin {} updating mail template {} (locale={})", principal, key, locale)

        val view = templates.update(
            template = template,
            locale = locale,
            subject = mailTemplateUpdateRequest.subject,
            bodyPlain = mailTemplateUpdateRequest.bodyPlain,
            bodyHtml = mailTemplateUpdateRequest.bodyHtml,
            updatedBy = principal,
        )
        return ResponseEntity.ok(view.toResponse(template))
    }

    @PreAuthorize("@namespaceAuthorizationService.isCurrentUserSuperadmin()")
    override fun previewMailTemplate(
        key: String,
        mailTemplatePreviewRequest: MailTemplatePreviewRequest,
    ): ResponseEntity<MailTemplatePreviewResponse> {
        requireSuperadmin()
        val template = MailTemplate.byKey(key) ?: throw MailTemplateNotFoundException(key)
        // Generated DTO types `sampleVars` as nullable; treat null as
        // "no overrides" so the registry defaults apply unchanged.
        val overrides = mailTemplatePreviewRequest.sampleVars ?: emptyMap()
        val result = templates.previewWith(
            template = template,
            subject = mailTemplatePreviewRequest.subject,
            bodyPlain = mailTemplatePreviewRequest.bodyPlain,
            bodyHtml = mailTemplatePreviewRequest.bodyHtml,
            sampleVarsOverride = overrides,
        )
        return ResponseEntity.ok(
            MailTemplatePreviewResponse(
                subject = result.rendered.subject,
                bodyPlain = result.rendered.bodyPlain,
                sampleVars = result.sampleVars,
                bodyHtml = result.rendered.bodyHtml,
            ),
        )
    }

    @PreAuthorize("@namespaceAuthorizationService.isCurrentUserSuperadmin()")
    override fun resetMailTemplate(key: String): ResponseEntity<Unit> {
        val principal = requireSuperadmin()
        val template = MailTemplate.byKey(key) ?: throw MailTemplateNotFoundException(key)
        val locale = settings.defaultLanguage()
        val deleted = templates.delete(template, locale)
        log.info("Superadmin {} reset mail template {} (locale={}, hadOverride={})", principal, key, locale, deleted)
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build()
    }

    private fun requireSuperadmin(): String {
        val auth = currentAuthentication()
        namespaceAuthorizationService.requireSuperadmin(auth)
        return auth.name
    }

    private fun MailTemplateView.toResponse(template: MailTemplate): MailTemplateResponse = MailTemplateResponse(
        key = template.key,
        friendlyName = friendlyName(template),
        locale = locale,
        subject = subject,
        bodyPlain = bodyPlain,
        defaultSubject = template.defaultSubject,
        defaultBodyPlain = template.defaultBodyPlainTemplate,
        placeholders = template.placeholders.toList().sorted(),
        source = source.toApi(),
        bodyHtml = bodyHtml,
        defaultBodyHtml = template.defaultBodyHtmlTemplate,
        updatedAt = updatedAt,
        updatedBy = updatedBy,
    )

    private fun TemplateSource.toApi(): MailTemplateResponse.Source = when (this) {
        TemplateSource.DATABASE -> MailTemplateResponse.Source.DATABASE
        TemplateSource.DEFAULT -> MailTemplateResponse.Source.DEFAULT
    }

    /**
     * Humanises an `auth.password_reset` key into `Auth · Password Reset`
     * for the UI. Intentionally locale-agnostic in v1 — the registry key is
     * stable and short, and a translated friendly name belongs in the
     * frontend i18n layer rather than the API.
     */
    private fun friendlyName(template: MailTemplate): String =
        template.key.split('.').joinToString(separator = " · ") { segment ->
            segment.split('_').joinToString(separator = " ") { word ->
                word.replaceFirstChar { it.uppercase() }
            }
        }
}

/** Thrown when a request targets a registry key that is not in [MailTemplate]. Mapped to 404. */
class MailTemplateNotFoundException(key: String) : RuntimeException("No mail template registered for key '$key'")
