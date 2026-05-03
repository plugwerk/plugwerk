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

import io.plugwerk.server.security.ChangePasswordRateLimitFilter
import io.plugwerk.server.security.LoginRateLimitFilter
import io.plugwerk.server.security.NamespaceAccessKeyAuthFilter
import io.plugwerk.server.security.NamespaceAuthorizationService
import io.plugwerk.server.security.PasswordChangeRequiredFilter
import io.plugwerk.server.security.PublicNamespaceFilter
import io.plugwerk.server.security.RefreshRateLimitFilter
import io.plugwerk.server.service.mail.MailTemplate
import io.plugwerk.server.service.mail.MailTemplateService
import io.plugwerk.server.service.mail.MailTemplateView
import io.plugwerk.server.service.mail.TemplateSource
import io.plugwerk.server.service.settings.ApplicationSettingsService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.FilterType
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.put
import java.time.OffsetDateTime

@WebMvcTest(
    AdminEmailTemplatesController::class,
    excludeAutoConfiguration = [SecurityAutoConfiguration::class, ServletWebSecurityAutoConfiguration::class],
    excludeFilters = [
        ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = [ChangePasswordRateLimitFilter::class]),
        ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = [LoginRateLimitFilter::class]),
        ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = [RefreshRateLimitFilter::class]),
        ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = [NamespaceAccessKeyAuthFilter::class]),
        ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = [PublicNamespaceFilter::class]),
        ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = [PasswordChangeRequiredFilter::class]),
    ],
)
class AdminEmailTemplatesControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var jwtDecoder: JwtDecoder

    @MockitoBean
    private lateinit var templates: MailTemplateService

    @MockitoBean
    private lateinit var settings: ApplicationSettingsService

    @MockitoBean
    private lateinit var namespaceAuthorizationService: NamespaceAuthorizationService

    @BeforeEach
    fun setUp() {
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken("admin", null, emptyList())
        whenever(settings.defaultLanguage()).thenReturn("en")
    }

    @AfterEach
    fun tearDown() {
        SecurityContextHolder.clearContext()
    }

    private fun overrideView(
        template: MailTemplate,
        subject: String = "Custom subject",
        bodyPlain: String = "Custom plaintext body {{username}}",
        bodyHtml: String? = "<p>Custom HTML {{username}}</p>",
    ) = MailTemplateView(
        key = template,
        locale = "en",
        subject = subject,
        bodyPlain = bodyPlain,
        bodyHtml = bodyHtml,
        source = TemplateSource.DATABASE,
        updatedAt = OffsetDateTime.parse("2026-05-03T10:00:00Z"),
        updatedBy = "admin",
    )

    private fun defaultView(template: MailTemplate) = MailTemplateView(
        key = template,
        locale = "en",
        subject = template.defaultSubject,
        bodyPlain = template.defaultBodyPlainTemplate,
        bodyHtml = template.defaultBodyHtmlTemplate,
        source = TemplateSource.DEFAULT,
        updatedAt = null,
        updatedBy = null,
    )

    @Test
    fun `GET list returns one entry per registered template with override OR default values`() {
        // First template overridden, second falls through to enum default —
        // proves the controller iterates the full registry, not just rows.
        whenever(templates.findEffective(MailTemplate.AUTH_REGISTRATION_VERIFICATION, "en"))
            .thenReturn(overrideView(MailTemplate.AUTH_REGISTRATION_VERIFICATION))
        whenever(templates.findEffective(MailTemplate.AUTH_PASSWORD_RESET, "en"))
            .thenReturn(defaultView(MailTemplate.AUTH_PASSWORD_RESET))

        mockMvc.get("/api/v1/admin/email/templates").andExpect {
            status { isOk() }
            jsonPath("$.templates.length()") { value(MailTemplate.entries.size) }
            jsonPath("$.templates[?(@.key=='auth.registration_verification')].source") { value("DATABASE") }
            jsonPath("$.templates[?(@.key=='auth.password_reset')].source") { value("DEFAULT") }
            // Friendly name humanises both segments and joins them with a separator.
            jsonPath("$.templates[?(@.key=='auth.password_reset')].friendlyName") { value("Auth · Password Reset") }
            // Default fields are always present, regardless of source.
            jsonPath("$.templates[?(@.key=='auth.password_reset')].defaultSubject") { exists() }
            jsonPath("$.templates[?(@.key=='auth.password_reset')].defaultBodyPlain") { exists() }
            // Placeholders surface from the enum so the UI can show the var reference list.
            jsonPath("$.templates[?(@.key=='auth.password_reset')].placeholders.length()") {
                value(MailTemplate.AUTH_PASSWORD_RESET.placeholders.size)
            }
        }
    }

    @Test
    fun `GET single template returns 404 for unknown registry key`() {
        mockMvc.get("/api/v1/admin/email/templates/unknown.key").andExpect {
            status { isNotFound() }
        }
    }

    @Test
    fun `GET single template returns the effective view`() {
        whenever(templates.findEffective(MailTemplate.AUTH_PASSWORD_RESET, "en"))
            .thenReturn(overrideView(MailTemplate.AUTH_PASSWORD_RESET, subject = "Reset overridden"))

        mockMvc.get("/api/v1/admin/email/templates/auth.password_reset").andExpect {
            status { isOk() }
            jsonPath("$.key") { value("auth.password_reset") }
            jsonPath("$.subject") { value("Reset overridden") }
            jsonPath("$.source") { value("DATABASE") }
            jsonPath("$.bodyHtml") { exists() }
            jsonPath("$.defaultBodyHtml") { exists() }
        }
    }

    @Test
    fun `PUT updates the template and returns the new effective view`() {
        whenever(
            templates.update(
                eq(MailTemplate.AUTH_PASSWORD_RESET),
                eq("en"),
                any(),
                any(),
                anyOrNull(),
                eq("admin"),
            ),
        ).thenReturn(overrideView(MailTemplate.AUTH_PASSWORD_RESET, subject = "v2"))

        val body = """
            {
              "subject": "v2",
              "bodyPlain": "Hi {{username}}",
              "bodyHtml": "<p>Hi {{username}}</p>"
            }
        """.trimIndent()

        mockMvc.put("/api/v1/admin/email/templates/auth.password_reset") {
            contentType = MediaType.APPLICATION_JSON
            content = body
        }.andExpect {
            status { isOk() }
            jsonPath("$.subject") { value("v2") }
            jsonPath("$.source") { value("DATABASE") }
        }
        verify(templates).update(
            eq(MailTemplate.AUTH_PASSWORD_RESET),
            eq("en"),
            eq("v2"),
            eq("Hi {{username}}"),
            eq("<p>Hi {{username}}</p>"),
            eq("admin"),
        )
    }

    @Test
    fun `PUT returns 400 when the service rejects an undocumented placeholder`() {
        whenever(
            templates.update(
                eq(MailTemplate.AUTH_PASSWORD_RESET),
                any(),
                any(),
                any(),
                anyOrNull(),
                any(),
            ),
        ).doThrow(IllegalArgumentException("Template references undocumented variables"))

        mockMvc.put("/api/v1/admin/email/templates/auth.password_reset") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"subject":"x","bodyPlain":"{{badVar}}"}"""
        }.andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun `PUT returns 404 for unknown registry key without touching the service`() {
        mockMvc.put("/api/v1/admin/email/templates/unknown.key") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"subject":"x","bodyPlain":"y"}"""
        }.andExpect {
            status { isNotFound() }
        }
        verify(templates, never()).update(any(), any(), any(), any(), anyOrNull(), any())
    }

    @Test
    fun `PUT accepts null bodyHtml — drops the HTML alternative`() {
        whenever(
            templates.update(
                eq(MailTemplate.AUTH_PASSWORD_RESET),
                eq("en"),
                any(),
                any(),
                anyOrNull(),
                any(),
            ),
        ).thenReturn(
            overrideView(MailTemplate.AUTH_PASSWORD_RESET, bodyHtml = null),
        )

        mockMvc.put("/api/v1/admin/email/templates/auth.password_reset") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"subject":"plain only","bodyPlain":"Hi {{username}}","bodyHtml":null}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.bodyHtml") { value(null) }
        }
        val captor = argumentCaptor<String?>()
        verify(templates).update(
            eq(MailTemplate.AUTH_PASSWORD_RESET),
            eq("en"),
            any(),
            any(),
            captor.capture(),
            any(),
        )
        assert(captor.firstValue == null) { "Expected null bodyHtml forwarded to service, got ${captor.firstValue}" }
    }

    @Test
    fun `DELETE returns 204 and forwards to service`() {
        whenever(templates.delete(MailTemplate.AUTH_PASSWORD_RESET, "en")).thenReturn(true)

        mockMvc.delete("/api/v1/admin/email/templates/auth.password_reset").andExpect {
            status { isNoContent() }
        }
        verify(templates).delete(MailTemplate.AUTH_PASSWORD_RESET, "en")
    }

    @Test
    fun `DELETE is idempotent — returns 204 even when no override existed`() {
        whenever(templates.delete(MailTemplate.AUTH_PASSWORD_RESET, "en")).thenReturn(false)

        mockMvc.delete("/api/v1/admin/email/templates/auth.password_reset").andExpect {
            status { isNoContent() }
        }
    }

    @Test
    fun `DELETE returns 404 for unknown registry key`() {
        mockMvc.delete("/api/v1/admin/email/templates/unknown.key").andExpect {
            status { isNotFound() }
        }
        verify(templates, never()).delete(any(), any())
    }
}
