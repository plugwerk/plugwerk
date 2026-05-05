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

import io.plugwerk.server.PlugwerkProperties
import io.plugwerk.server.domain.UserEntity
import io.plugwerk.server.domain.UserSource
import io.plugwerk.server.security.ChangePasswordRateLimitFilter
import io.plugwerk.server.security.LoginRateLimitFilter
import io.plugwerk.server.security.NamespaceAccessKeyAuthFilter
import io.plugwerk.server.security.PasswordChangeRequiredFilter
import io.plugwerk.server.security.PasswordResetRateLimitFilter
import io.plugwerk.server.security.PublicNamespaceFilter
import io.plugwerk.server.security.RateLimitResult
import io.plugwerk.server.security.RefreshRateLimitFilter
import io.plugwerk.server.security.RegisterRateLimitFilter
import io.plugwerk.server.security.RegisterRateLimitService
import io.plugwerk.server.service.ConflictException
import io.plugwerk.server.service.UserService
import io.plugwerk.server.service.auth.EmailVerificationTokenService
import io.plugwerk.server.service.auth.InvalidVerificationTokenException
import io.plugwerk.server.service.auth.IssuedToken
import io.plugwerk.server.service.mail.MailService
import io.plugwerk.server.service.mail.MailTemplate
import io.plugwerk.server.service.settings.ApplicationSettingsService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argThat
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
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.time.OffsetDateTime
import java.util.UUID

@WebMvcTest(
    AuthRegistrationController::class,
    excludeAutoConfiguration = [SecurityAutoConfiguration::class, ServletWebSecurityAutoConfiguration::class],
    excludeFilters = [
        ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = [ChangePasswordRateLimitFilter::class]),
        ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = [LoginRateLimitFilter::class]),
        ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = [RegisterRateLimitFilter::class]),
        ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = [PasswordResetRateLimitFilter::class]),
        ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = [RefreshRateLimitFilter::class]),
        ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = [NamespaceAccessKeyAuthFilter::class]),
        ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = [PublicNamespaceFilter::class]),
        ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = [PasswordChangeRequiredFilter::class]),
    ],
)
class AuthRegistrationControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean private lateinit var jwtDecoder: JwtDecoder

    @MockitoBean private lateinit var settings: ApplicationSettingsService

    @MockitoBean private lateinit var userService: UserService

    @MockitoBean private lateinit var tokenService: EmailVerificationTokenService

    @MockitoBean private lateinit var mailService: MailService

    @MockitoBean private lateinit var rateLimitService: RegisterRateLimitService

    @MockitoBean private lateinit var plugwerkProperties: PlugwerkProperties

    @BeforeEach
    fun setUp() {
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken("anon", null, emptyList())
        // Defaults: registration enabled, verification required, SMTP enabled,
        // rate limit allows. Individual tests override what they care about.
        whenever(settings.selfRegistrationEnabled()).thenReturn(true)
        whenever(settings.selfRegistrationEmailVerificationRequired()).thenReturn(true)
        whenever(settings.smtpEnabled()).thenReturn(true)
        whenever(rateLimitService.tryConsumeEmail(any()))
            .thenReturn(RateLimitResult.Allowed(remainingTokens = 5))
        // Some tests don't go far enough to need this; lenient stub keeps Mockito quiet.
        whenever(plugwerkProperties.server).thenReturn(
            PlugwerkProperties.ServerProperties(baseUrl = "https://plugwerk.test"),
        )
    }

    @AfterEach
    fun tearDown() {
        SecurityContextHolder.clearContext()
    }

    private fun stubUser(): UserEntity = UserEntity(
        id = UUID.randomUUID(),
        username = "alice",
        email = "alice@example.com",
        displayName = "Alice",
        source = UserSource.INTERNAL,
        passwordHash = "\$2a\$12\$hash",
        enabled = false,
        passwordChangeRequired = false,
    )

    private val registerBody = """
        {"username":"alice","email":"alice@example.com","password":"correct-horse-battery-staple"}
    """.trimIndent()

    @Test
    fun `POST register returns 404 when self-registration is disabled — does not advertise the endpoint`() {
        whenever(settings.selfRegistrationEnabled()).thenReturn(false)

        mockMvc.post("/api/v1/auth/register") {
            contentType = MediaType.APPLICATION_JSON
            content = registerBody
        }.andExpect {
            status { isNotFound() }
        }
        verify(userService, never()).createSelfRegistered(any(), any(), any(), anyOrNull(), any())
        verify(mailService, never()).sendMailFromTemplate(any(), any(), any(), anyOrNull())
    }

    @Test
    fun `POST register returns 503 when verification is required but SMTP is disabled`() {
        whenever(settings.smtpEnabled()).thenReturn(false)

        mockMvc.post("/api/v1/auth/register") {
            contentType = MediaType.APPLICATION_JSON
            content = registerBody
        }.andExpect {
            status { isServiceUnavailable() }
        }
        verify(userService, never()).createSelfRegistered(any(), any(), any(), anyOrNull(), any())
    }

    @Test
    fun `POST register happy path — creates user disabled, issues token, sends template mail`() {
        val user = stubUser()
        whenever(userService.createSelfRegistered(eq("alice"), eq("alice@example.com"), any(), anyOrNull(), eq(false)))
            .thenReturn(user)
        whenever(tokenService.issue(user)).thenReturn(
            IssuedToken(
                rawToken = "demo-raw-token",
                expiresAt = OffsetDateTime.now().plusHours(24),
            ),
        )
        whenever(mailService.sendMailFromTemplate(any(), any(), any(), anyOrNull()))
            .thenReturn(MailService.SendResult.Sent)

        mockMvc.post("/api/v1/auth/register") {
            contentType = MediaType.APPLICATION_JSON
            content = registerBody
        }.andExpect {
            status { isOk() }
            jsonPath("$.status") { value("VERIFICATION_PENDING") }
        }
        verify(mailService).sendMailFromTemplate(
            eq(MailTemplate.AUTH_REGISTRATION_VERIFICATION),
            eq("alice@example.com"),
            argThat {
                this["verificationLink"].toString().contains("demo-raw-token") &&
                    this["username"] == "Alice"
            },
            anyOrNull(),
        )
    }

    @Test
    fun `POST register builds verificationLink against webBaseUrl when set, falls back to baseUrl otherwise`() {
        whenever(plugwerkProperties.server).thenReturn(
            PlugwerkProperties.ServerProperties(
                baseUrl = "http://localhost:8080",
                webBaseUrl = "http://localhost:5173",
            ),
        )
        val user = stubUser()
        whenever(userService.createSelfRegistered(any(), any(), any(), anyOrNull(), eq(false)))
            .thenReturn(user)
        whenever(tokenService.issue(user)).thenReturn(
            IssuedToken(
                rawToken = "vite-token",
                expiresAt = OffsetDateTime.now().plusHours(24),
            ),
        )
        whenever(mailService.sendMailFromTemplate(any(), any(), any(), anyOrNull()))
            .thenReturn(MailService.SendResult.Sent)

        mockMvc.post("/api/v1/auth/register") {
            contentType = MediaType.APPLICATION_JSON
            content = registerBody
        }.andExpect { status { isOk() } }

        verify(mailService).sendMailFromTemplate(
            any(),
            any(),
            argThat {
                this["verificationLink"].toString() ==
                    "http://localhost:5173/verify-email?token=vite-token"
            },
            anyOrNull(),
        )
    }

    @Test
    fun `POST register with verification disabled creates an enabled user and skips the email`() {
        whenever(settings.selfRegistrationEmailVerificationRequired()).thenReturn(false)
        val user = stubUser().also { it.enabled = true }
        whenever(userService.createSelfRegistered(any(), any(), any(), anyOrNull(), eq(true))).thenReturn(user)

        mockMvc.post("/api/v1/auth/register") {
            contentType = MediaType.APPLICATION_JSON
            content = registerBody
        }.andExpect {
            status { isOk() }
            jsonPath("$.status") { value("ACTIVE") }
        }
        verify(tokenService, never()).issue(any())
        verify(mailService, never()).sendMailFromTemplate(any(), any(), any(), anyOrNull())
    }

    @Test
    fun `POST register silently swallows username and email collisions for anti-enumeration`() {
        whenever(userService.createSelfRegistered(any(), any(), any(), anyOrNull(), any()))
            .doThrow(ConflictException("Email 'alice@example.com' is already registered"))

        mockMvc.post("/api/v1/auth/register") {
            contentType = MediaType.APPLICATION_JSON
            content = registerBody
        }.andExpect {
            // Same status + body shape as the happy path — the legitimate
            // owner of the address sees nothing (no email is sent), and an
            // attacker probing existence cannot tell the difference.
            status { isOk() }
            jsonPath("$.status") { value("VERIFICATION_PENDING") }
        }
        verify(tokenService, never()).issue(any())
        verify(mailService, never()).sendMailFromTemplate(any(), any(), any(), anyOrNull())
    }

    @Test
    fun `POST register returns 429 when the email-keyed bucket is exhausted`() {
        whenever(rateLimitService.tryConsumeEmail(any()))
            .thenReturn(RateLimitResult.Rejected(retryAfterSeconds = 600))

        mockMvc.post("/api/v1/auth/register") {
            contentType = MediaType.APPLICATION_JSON
            content = registerBody
        }.andExpect {
            status { isTooManyRequests() }
        }
        verify(userService, never()).createSelfRegistered(any(), any(), any(), anyOrNull(), any())
    }

    @Test
    fun `POST register returns 503 when the verification mail itself fails to send`() {
        val user = stubUser()
        whenever(userService.createSelfRegistered(any(), any(), any(), anyOrNull(), eq(false))).thenReturn(user)
        whenever(tokenService.issue(user)).thenReturn(
            IssuedToken("token", OffsetDateTime.now().plusHours(24)),
        )
        whenever(mailService.sendMailFromTemplate(any(), any(), any(), anyOrNull()))
            .thenReturn(MailService.SendResult.Failed("auth required"))

        mockMvc.post("/api/v1/auth/register") {
            contentType = MediaType.APPLICATION_JSON
            content = registerBody
        }.andExpect {
            status { isServiceUnavailable() }
        }
    }

    @Test
    fun `GET verify-email returns 404 when self-registration is disabled`() {
        whenever(settings.selfRegistrationEnabled()).thenReturn(false)

        mockMvc.get("/api/v1/auth/verify-email") {
            param("token", "anything")
        }.andExpect {
            status { isNotFound() }
        }
        verify(tokenService, never()).consume(any())
    }

    @Test
    fun `GET verify-email consumes the token and flips the user enabled when needed`() {
        // The mock must return a fresh disabled user — using `also { it.enabled = true }`
        // on a stub value would mutate the same instance read by the controller's
        // `if (!user.enabled)` check and skip the setEnabled call.
        val disabledUser = stubUser()
        val enabledUser = stubUser().also {
            it.id = disabledUser.id
            it.enabled = true
        }
        whenever(tokenService.consume("good-token")).thenReturn(disabledUser)
        whenever(userService.setEnabled(eq(disabledUser.id!!), eq(true))).thenReturn(enabledUser)

        mockMvc.get("/api/v1/auth/verify-email") {
            param("token", "good-token")
        }.andExpect {
            status { isOk() }
            jsonPath("$.status") { value("VERIFIED") }
        }
        verify(userService).setEnabled(disabledUser.id!!, true)
    }

    @Test
    fun `GET verify-email returns 400 on invalid token`() {
        whenever(tokenService.consume(any()))
            .doThrow(InvalidVerificationTokenException("Verification token is invalid"))

        mockMvc.get("/api/v1/auth/verify-email") {
            param("token", "bad-token")
        }.andExpect {
            status { isBadRequest() }
        }
    }
}
