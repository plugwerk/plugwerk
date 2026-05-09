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
package io.plugwerk.server.service.auth

import io.plugwerk.server.PlugwerkProperties
import io.plugwerk.server.domain.UserEntity
import io.plugwerk.server.domain.UserSource
import io.plugwerk.server.repository.UserRepository
import io.plugwerk.server.service.EntityNotFoundException
import io.plugwerk.server.service.RefreshTokenService
import io.plugwerk.server.service.TokenRevocationService
import io.plugwerk.server.service.mail.MailService
import io.plugwerk.server.service.mail.MailTemplate
import io.plugwerk.server.service.settings.ApplicationSettingsService
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.whenever
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.Optional
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class AdminPasswordResetServiceTest {

    @Mock private lateinit var userRepository: UserRepository

    @Mock private lateinit var tokenService: PasswordResetTokenService

    @Mock private lateinit var tokenRevocationService: TokenRevocationService

    @Mock private lateinit var refreshTokenService: RefreshTokenService

    @Mock private lateinit var mailService: MailService

    @Mock private lateinit var settings: ApplicationSettingsService

    private val plugwerkProperties = PlugwerkProperties(
        server = PlugwerkProperties.ServerProperties(
            baseUrl = "https://plugwerk.example.com",
        ),
    )

    private lateinit var service: AdminPasswordResetService

    private val targetUserId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val actorUserId = UUID.fromString("00000000-0000-0000-0000-000000000002")
    private val expiresAt: OffsetDateTime = OffsetDateTime.of(2026, 5, 9, 13, 0, 0, 0, ZoneOffset.UTC)

    @BeforeEach
    fun setUp() {
        service = AdminPasswordResetService(
            userRepository = userRepository,
            tokenService = tokenService,
            tokenRevocationService = tokenRevocationService,
            refreshTokenService = refreshTokenService,
            mailService = mailService,
            settings = settings,
            plugwerkProperties = plugwerkProperties,
        )
    }

    @Test
    fun `success — issues token, revokes both atoms, sends mail, returns emailSent=true and no resetUrl`() {
        val user = internalUser()
        whenever(userRepository.findById(targetUserId)).thenReturn(Optional.of(user))
        whenever(tokenService.issue(user))
            .thenReturn(IssuedResetToken(rawToken = "raw-token-abc", expiresAt = expiresAt))
        whenever(settings.siteName()).thenReturn("plugwerk.example.com")
        whenever(
            mailService.sendMailFromTemplate(any(), any(), any(), anyOrNull()),
        ).thenReturn(MailService.SendResult.Sent)

        val result = service.trigger(targetUserId, actorUserId)

        assertThat(result.tokenIssued).isTrue
        assertThat(result.emailSent).isTrue
        assertThat(result.resetUrl).isNull()
        assertThat(result.expiresAt).isEqualTo(expiresAt)
        // Mark passwordChangeRequired and persist
        assertThat(user.passwordChangeRequired).isTrue
        verify(userRepository).save(user)
        // Both revocation atoms — both are mandatory.
        verify(tokenRevocationService).revokeAllForUser(targetUserId)
        verify(refreshTokenService).revokeAllForUser(targetUserId, RefreshTokenService.LOGOUT)
        // Mail uses the admin-specific template and the canonical reset link.
        val templateCaptor = argumentCaptor<MailTemplate>()
        val varsCaptor = argumentCaptor<Map<String, Any?>>()
        verify(mailService).sendMailFromTemplate(
            templateCaptor.capture(),
            eq("alice@example.com"),
            varsCaptor.capture(),
            isNull(),
        )
        assertThat(templateCaptor.firstValue).isEqualTo(MailTemplate.AUTH_ADMIN_PASSWORD_RESET)
        assertThat(
            varsCaptor.firstValue,
        ).containsEntry("username", "Alice").containsEntry("siteName", "plugwerk.example.com")
        assertThat(
            varsCaptor.firstValue["resetLink"],
        ).isEqualTo("https://plugwerk.example.com/reset-password?token=raw-token-abc")
        assertThat(varsCaptor.firstValue).containsKey("expiresAtHuman")
    }

    @Test
    fun `success but SMTP disabled — returns emailSent=false with absolute resetUrl in response`() {
        val user = internalUser()
        whenever(userRepository.findById(targetUserId)).thenReturn(Optional.of(user))
        whenever(tokenService.issue(user))
            .thenReturn(IssuedResetToken(rawToken = "raw-token-xyz", expiresAt = expiresAt))
        whenever(settings.siteName()).thenReturn("plugwerk.example.com")
        whenever(mailService.sendMailFromTemplate(any(), any(), any(), anyOrNull()))
            .thenReturn(MailService.SendResult.Disabled)

        val result = service.trigger(targetUserId, actorUserId)

        assertThat(result.tokenIssued).isTrue
        assertThat(result.emailSent).isFalse
        assertThat(result.resetUrl).isEqualTo("https://plugwerk.example.com/reset-password?token=raw-token-xyz")
        // Server-side state changes still happened — the operator can deliver the link out-of-band.
        assertThat(user.passwordChangeRequired).isTrue
        verify(tokenRevocationService).revokeAllForUser(targetUserId)
        verify(refreshTokenService).revokeAllForUser(targetUserId, RefreshTokenService.LOGOUT)
    }

    @Test
    fun `mail-send-failure (Failed) — degraded same shape as SMTP-disabled (resetUrl populated)`() {
        val user = internalUser()
        whenever(userRepository.findById(targetUserId)).thenReturn(Optional.of(user))
        whenever(tokenService.issue(user))
            .thenReturn(IssuedResetToken(rawToken = "raw-token-fail", expiresAt = expiresAt))
        whenever(settings.siteName()).thenReturn("plugwerk.example.com")
        whenever(mailService.sendMailFromTemplate(any(), any(), any(), anyOrNull()))
            .thenReturn(MailService.SendResult.Failed("connection refused"))

        val result = service.trigger(targetUserId, actorUserId)

        assertThat(result.emailSent).isFalse
        assertThat(result.resetUrl).isEqualTo("https://plugwerk.example.com/reset-password?token=raw-token-fail")
    }

    @Test
    fun `mail-send-throws — caught and treated as Failed (degraded)`() {
        val user = internalUser()
        whenever(userRepository.findById(targetUserId)).thenReturn(Optional.of(user))
        whenever(tokenService.issue(user))
            .thenReturn(IssuedResetToken(rawToken = "raw-token-throw", expiresAt = expiresAt))
        whenever(settings.siteName()).thenReturn("plugwerk.example.com")
        whenever(mailService.sendMailFromTemplate(any(), any(), any(), anyOrNull()))
            .thenThrow(RuntimeException("template render exploded"))

        val result = service.trigger(targetUserId, actorUserId)

        // Server-side side effects still completed before mail dispatch.
        assertThat(result.emailSent).isFalse
        assertThat(result.resetUrl).contains("raw-token-throw")
        verify(tokenRevocationService).revokeAllForUser(targetUserId)
        verify(refreshTokenService).revokeAllForUser(targetUserId, RefreshTokenService.LOGOUT)
    }

    @Test
    fun `EXTERNAL user — refused with ExternalUserResetNotAllowedException, no side effects`() {
        val externalUser = internalUser().also { it.source = UserSource.EXTERNAL }
        whenever(userRepository.findById(targetUserId)).thenReturn(Optional.of(externalUser))

        assertThatThrownBy { service.trigger(targetUserId, actorUserId) }
            .isInstanceOf(ExternalUserResetNotAllowedException::class.java)
            .hasMessageContaining("OIDC")

        verifyNoInteractions(tokenService, tokenRevocationService, refreshTokenService, mailService)
    }

    @Test
    fun `self-reset — refused with SelfResetNotAllowedException before any DB lookup`() {
        assertThatThrownBy { service.trigger(actorUserId, actorUserId) }
            .isInstanceOf(SelfResetNotAllowedException::class.java)
            .hasMessageContaining("Profile")

        verifyNoInteractions(
            userRepository,
            tokenService,
            tokenRevocationService,
            refreshTokenService,
            mailService,
        )
    }

    @Test
    fun `unknown user — propagates EntityNotFoundException, no side effects`() {
        whenever(userRepository.findById(targetUserId)).thenReturn(Optional.empty())

        assertThatThrownBy { service.trigger(targetUserId, actorUserId) }
            .isInstanceOf(EntityNotFoundException::class.java)
            .hasMessageContaining(targetUserId.toString())

        verifyNoInteractions(tokenService, tokenRevocationService, refreshTokenService, mailService)
    }

    private fun internalUser(): UserEntity = UserEntity(
        username = "alice",
        email = "alice@example.com",
        displayName = "Alice",
        passwordHash = "\$2a\$10\$dummyHashThatLooksLikeBcrypt",
        source = UserSource.INTERNAL,
        enabled = true,
        passwordChangeRequired = false,
        isSuperadmin = false,
    ).also { it.id = targetUserId }
}
