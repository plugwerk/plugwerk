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

import io.plugwerk.server.SharedPostgresContainer
import io.plugwerk.server.domain.UserEntity
import io.plugwerk.server.domain.UserSource
import io.plugwerk.server.repository.PasswordResetTokenRepository
import io.plugwerk.server.repository.RefreshTokenRepository
import io.plugwerk.server.repository.UserRepository
import io.plugwerk.server.service.UserService
import io.plugwerk.server.service.mail.MailService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.security.MessageDigest
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

/**
 * Real-Postgres integration test that pins the dual-revocation persistence
 * contract (#450).
 *
 * The bug this test guards against: `RefreshTokenRepository.revokeAllForUser`
 * is annotated `@Modifying(clearAutomatically = true)`. Hibernate clears
 * the persistence context AFTER the bulk UPDATE without flushing pending
 * entity changes first (`flushAutomatically = false` is the default).
 *
 * If `AdminPasswordResetService.trigger` mutated the `UserEntity`
 * (`passwordChangeRequired = true`) BEFORE the refresh-token bulk
 * revocation ran, those pending changes would be silently discarded on
 * the PC clear. The fix reorders the bulk query to run first; this test
 * verifies the DB ends up in the right state regardless of intra-TX
 * persistence-context shape.
 *
 * Mocks `MailService` only — every other collaborator runs against the
 * real Postgres so JPA semantics are exercised end-to-end.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(AdminPasswordResetServiceIT.MockConfig::class)
@Tag("integration")
class AdminPasswordResetServiceIT {

    @TestConfiguration
    class MockConfig {
        @Bean fun mailService(): MailService = org.mockito.Mockito.mock(MailService::class.java)
    }

    companion object {
        @DynamicPropertySource
        @JvmStatic
        fun configureProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { SharedPostgresContainer.instance.jdbcUrl }
            registry.add("spring.datasource.username") { SharedPostgresContainer.instance.username }
            registry.add("spring.datasource.password") { SharedPostgresContainer.instance.password }
        }
    }

    @Autowired private lateinit var service: AdminPasswordResetService

    @Autowired private lateinit var userService: UserService

    @Autowired private lateinit var userRepository: UserRepository

    @Autowired private lateinit var refreshTokenRepository: RefreshTokenRepository

    @Autowired
    private lateinit var passwordResetTokenRepository: PasswordResetTokenRepository

    @Autowired private lateinit var mailService: MailService

    private lateinit var actor: UserEntity

    private lateinit var target: UserEntity

    @AfterEach
    fun tearDown() {
        // The MailService stub is reset per-test by the Spring test context
        // but `@SpringBootTest` reuses the context — clear users so consecutive
        // runs do not collide on the unique-username constraint.
        target.id?.let { runCatching { userRepository.deleteById(it) } }
        actor.id?.let { runCatching { userRepository.deleteById(it) } }
    }

    /**
     * Uses `createSelfRegistered` rather than the admin `create` so the
     * resulting row starts with `passwordChangeRequired = false`. The
     * post-condition `passwordChangeRequired = true` then proves the
     * trigger flipped the flag — the admin-create default is `true`,
     * which would mask the regression class this test guards against.
     */
    private fun freshUser(prefix: String): UserEntity {
        val tag = UUID.randomUUID().toString().take(8)
        return userService.createSelfRegistered(
            username = "$prefix-$tag",
            email = "$prefix-$tag@example.com",
            password = "correct-horse-battery-staple",
            displayName = "$prefix $tag",
            enabled = true,
        )
    }

    @Test
    fun `trigger persists passwordChangeRequired=true to the database after dual revocation`() {
        // Arrange: SMTP-disabled stub so MailService.sendMailFromTemplate
        // returns Disabled — server-side state changes must still happen.
        whenever(mailService.sendMailFromTemplate(any(), any(), any(), anyOrNull()))
            .thenReturn(MailService.SendResult.Disabled)

        actor = freshUser("admin")
        target = freshUser("alice")
        val targetId = requireNotNull(target.id)
        // Sanity: the column starts out at false on a fresh row.
        assertThat(target.passwordChangeRequired).isFalse

        // Act
        val result = service.trigger(targetUserId = targetId, actorUserId = requireNotNull(actor.id))

        // Assert: DB row reflects the admin-reset state. Re-fetch via the
        // repository to defeat any stale persistence-context caching from
        // the test thread.
        assertThat(result.tokenIssued).isTrue
        assertThat(result.emailSent).isFalse
        assertThat(result.resetUrl).isNotBlank

        val persisted = userRepository.findById(targetId).orElseThrow()
        assertThat(persisted.passwordChangeRequired)
            .withFailMessage(
                "password_change_required must be true after admin reset (regression: cleared by @Modifying(clearAutomatically=true) without preceding flush)",
            )
            .isTrue
        assertThat(persisted.passwordInvalidatedBefore)
            .withFailMessage("password_invalidated_before must be set after admin reset (same regression class)")
            .isNotNull
        assertThat(persisted.passwordInvalidatedBefore)
            .isAfter(OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1))

        // Pin the token-row persistence: derive the expected hash from
        // result.resetUrl's `?token=…` segment and look it up. The user-
        // visible regression that triggered this test class is precisely
        // an unflushed `PasswordResetTokenEntity` insert getting discarded
        // by the bulk-revocation PC clear — without this assertion the IT
        // would be green even when the emailed link is broken.
        val rawToken = result.resetUrl!!.substringAfter("?token=")
        val tokenHash = sha256Hex(rawToken)
        val tokenRow = passwordResetTokenRepository.findByTokenHash(tokenHash)
        assertThat(tokenRow.isPresent)
            .withFailMessage(
                "PasswordResetTokenEntity row must be persisted after admin reset (regression: unflushed insert discarded by @Modifying(clearAutomatically=true))",
            )
            .isTrue
        assertThat(tokenRow.get().consumedAt).isNull()
        assertThat(tokenRow.get().user.id).isEqualTo(targetId)
    }

    private fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString(separator = "") { "%02x".format(it) }
    }
}
