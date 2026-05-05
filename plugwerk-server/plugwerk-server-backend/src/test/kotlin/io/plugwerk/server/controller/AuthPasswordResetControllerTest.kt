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
import io.plugwerk.server.repository.UserRepository
import io.plugwerk.server.security.ChangePasswordRateLimitFilter
import io.plugwerk.server.security.LoginRateLimitFilter
import io.plugwerk.server.security.NamespaceAccessKeyAuthFilter
import io.plugwerk.server.security.PasswordChangeRequiredFilter
import io.plugwerk.server.security.PasswordResetRateLimitFilter
import io.plugwerk.server.security.PasswordResetRateLimitService
import io.plugwerk.server.security.PublicNamespaceFilter
import io.plugwerk.server.security.RateLimitResult
import io.plugwerk.server.security.RefreshRateLimitFilter
import io.plugwerk.server.security.RefreshTokenCookieFactory
import io.plugwerk.server.security.RegisterRateLimitFilter
import io.plugwerk.server.service.RefreshTokenService
import io.plugwerk.server.service.UserService
import io.plugwerk.server.service.auth.InvalidPasswordResetTokenException
import io.plugwerk.server.service.auth.IssuedResetToken
import io.plugwerk.server.service.auth.PasswordResetTokenService
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
import org.springframework.http.ResponseCookie
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import java.time.OffsetDateTime
import java.util.Optional
import java.util.UUID

@WebMvcTest(
    AuthPasswordResetController::class,
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
class AuthPasswordResetControllerTest {

    @Autowired private lateinit var mockMvc: MockMvc

    @MockitoBean private lateinit var jwtDecoder: JwtDecoder

    @MockitoBean private lateinit var settings: ApplicationSettingsService

    @MockitoBean private lateinit var userService: UserService

    @MockitoBean private lateinit var userRepository: UserRepository

    @MockitoBean private lateinit var tokenService: PasswordResetTokenService

    @MockitoBean private lateinit var mailService: MailService

    @MockitoBean private lateinit var rateLimitService: PasswordResetRateLimitService

    @MockitoBean private lateinit var refreshTokenService: RefreshTokenService

    @MockitoBean private lateinit var refreshTokenCookieFactory: RefreshTokenCookieFactory

    @MockitoBean private lateinit var plugwerkProperties: PlugwerkProperties

    @BeforeEach
    fun setUp() {
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken("anon", null, emptyList())
        // Defaults: feature on, SMTP on, IP+token buckets allow.
        whenever(settings.passwordResetEnabled()).thenReturn(true)
        whenever(settings.smtpEnabled()).thenReturn(true)
        whenever(rateLimitService.tryConsumeToken(any()))
            .thenReturn(RateLimitResult.Allowed(remainingTokens = 5))
        whenever(plugwerkProperties.server).thenReturn(
            PlugwerkProperties.ServerProperties(baseUrl = "https://plugwerk.test"),
        )
        whenever(refreshTokenCookieFactory.clear()).thenReturn(
            ResponseCookie.from("refresh_token", "").maxAge(0).path("/").build(),
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
        enabled = true,
        passwordChangeRequired = false,
    )

    private val forgotBody = """{"usernameOrEmail":"alice@example.com"}"""

    private val resetBody = """{"token":"abc-token","newPassword":"correct-horse-battery-staple"}"""

    @Test
    fun `POST forgot-password returns 404 when password reset is disabled — does not advertise the endpoint`() {
        whenever(settings.passwordResetEnabled()).thenReturn(false)

        mockMvc.post("/api/v1/auth/forgot-password") {
            contentType = MediaType.APPLICATION_JSON
            content = forgotBody
        }.andExpect {
            status { isNotFound() }
        }
        verify(tokenService, never()).issue(any())
        verify(mailService, never()).sendMailFromTemplate(any(), any(), any(), anyOrNull())
    }

    @Test
    fun `POST forgot-password returns 503 when SMTP is disabled (operator-actionable)`() {
        whenever(settings.smtpEnabled()).thenReturn(false)

        mockMvc.post("/api/v1/auth/forgot-password") {
            contentType = MediaType.APPLICATION_JSON
            content = forgotBody
        }.andExpect {
            status { isServiceUnavailable() }
        }
        verify(tokenService, never()).issue(any())
    }

    @Test
    fun `POST forgot-password returns 204 silently when no INTERNAL user matches (anti-enumeration)`() {
        whenever(userRepository.findByUsernameOrEmailAndSource(any(), eq(UserSource.INTERNAL)))
            .thenReturn(Optional.empty())

        mockMvc.post("/api/v1/auth/forgot-password") {
            contentType = MediaType.APPLICATION_JSON
            content = forgotBody
        }.andExpect {
            status { isNoContent() }
        }
        verify(tokenService, never()).issue(any())
        verify(mailService, never()).sendMailFromTemplate(any(), any(), any(), anyOrNull())
    }

    @Test
    fun `POST forgot-password returns 204 silently for a disabled user (no email sent)`() {
        val disabled = stubUser().also { it.enabled = false }
        whenever(userRepository.findByUsernameOrEmailAndSource(any(), eq(UserSource.INTERNAL)))
            .thenReturn(Optional.of(disabled))

        mockMvc.post("/api/v1/auth/forgot-password") {
            contentType = MediaType.APPLICATION_JSON
            content = forgotBody
        }.andExpect {
            status { isNoContent() }
        }
        verify(tokenService, never()).issue(any())
    }

    @Test
    fun `POST forgot-password happy path issues a token, sends the template email, and returns 204`() {
        val user = stubUser()
        whenever(userRepository.findByUsernameOrEmailAndSource(any(), eq(UserSource.INTERNAL)))
            .thenReturn(Optional.of(user))
        whenever(tokenService.issue(user)).thenReturn(
            IssuedResetToken(
                rawToken = "demo-raw-token",
                expiresAt = OffsetDateTime.now().plusHours(1),
            ),
        )
        whenever(mailService.sendMailFromTemplate(any(), any(), any(), anyOrNull()))
            .thenReturn(MailService.SendResult.Sent)

        mockMvc.post("/api/v1/auth/forgot-password") {
            contentType = MediaType.APPLICATION_JSON
            content = forgotBody
        }.andExpect {
            status { isNoContent() }
        }
        verify(mailService).sendMailFromTemplate(
            eq(MailTemplate.AUTH_PASSWORD_RESET),
            eq("alice@example.com"),
            argThat {
                this["resetLink"].toString().contains("demo-raw-token") &&
                    this["username"] == "Alice"
            },
            anyOrNull(),
        )
    }

    @Test
    fun `POST forgot-password still returns 204 when mail send fails (no timing oracle)`() {
        val user = stubUser()
        whenever(userRepository.findByUsernameOrEmailAndSource(any(), eq(UserSource.INTERNAL)))
            .thenReturn(Optional.of(user))
        whenever(tokenService.issue(user)).thenReturn(
            IssuedResetToken(rawToken = "x", expiresAt = OffsetDateTime.now().plusHours(1)),
        )
        whenever(mailService.sendMailFromTemplate(any(), any(), any(), anyOrNull()))
            .thenReturn(MailService.SendResult.Failed("smtp down"))

        mockMvc.post("/api/v1/auth/forgot-password") {
            contentType = MediaType.APPLICATION_JSON
            content = forgotBody
        }.andExpect {
            status { isNoContent() }
        }
    }

    @Test
    fun `POST reset-password returns 404 when password reset is disabled`() {
        whenever(settings.passwordResetEnabled()).thenReturn(false)

        mockMvc.post("/api/v1/auth/reset-password") {
            contentType = MediaType.APPLICATION_JSON
            content = resetBody
        }.andExpect {
            status { isNotFound() }
        }
        verify(tokenService, never()).consume(any())
    }

    @Test
    fun `POST reset-password returns 429 when token bucket is exhausted (brute-force defence)`() {
        whenever(rateLimitService.tryConsumeToken(any()))
            .thenReturn(RateLimitResult.Rejected(retryAfterSeconds = 30))

        mockMvc.post("/api/v1/auth/reset-password") {
            contentType = MediaType.APPLICATION_JSON
            content = resetBody
        }.andExpect {
            status { isTooManyRequests() }
        }
        verify(tokenService, never()).consume(any())
    }

    @Test
    fun `POST reset-password returns 400 with the reason when the token is invalid`() {
        whenever(tokenService.consume(any()))
            .doThrow(InvalidPasswordResetTokenException("Password-reset token has already been used"))

        mockMvc.post("/api/v1/auth/reset-password") {
            contentType = MediaType.APPLICATION_JSON
            content = resetBody
        }.andExpect {
            status { isBadRequest() }
        }
        verify(userService, never()).applyPasswordReset(any(), any())
    }

    @Test
    fun `POST reset-password happy path applies the new password, revokes refresh family, clears cookie`() {
        val user = stubUser()
        whenever(tokenService.consume(any())).thenReturn(user)
        whenever(userService.applyPasswordReset(eq(requireNotNull(user.id)), eq("correct-horse-battery-staple")))
            .thenReturn(user)

        mockMvc.post("/api/v1/auth/reset-password") {
            contentType = MediaType.APPLICATION_JSON
            content = resetBody
        }.andExpect {
            status { isNoContent() }
            // Cookie-Clear header is set so the browser drops the stale refresh
            // cookie alongside the server-side revocation.
            header { exists("Set-Cookie") }
        }
        verify(userService).applyPasswordReset(requireNotNull(user.id), "correct-horse-battery-staple")
        verify(refreshTokenService).revokeAllForUser(requireNotNull(user.id), RefreshTokenService.LOGOUT)
    }
}
