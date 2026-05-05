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

import io.plugwerk.server.domain.PasswordResetTokenEntity
import io.plugwerk.server.repository.PasswordResetTokenRepository
import io.plugwerk.server.service.UserService
import io.plugwerk.server.service.settings.ApplicationSettingKey
import io.plugwerk.server.service.settings.ApplicationSettingsService
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import java.time.OffsetDateTime
import java.util.UUID

/**
 * H2-backed integration test for [PasswordResetTokenService] (#421).
 *
 * Mirrors [EmailVerificationTokenServiceTest] (#420). The two services
 * have almost-identical lifecycles; the unique behaviour to nail down
 * here is that the TTL is **operator-tunable at runtime** (via the
 * `auth.password_reset_token_ttl_minutes` setting) — flipping the
 * setting must affect tokens issued from the next call onwards.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(
    properties = [
        "spring.datasource.url=jdbc:h2:mem:password-reset;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
    ],
)
class PasswordResetTokenServiceTest {

    @Autowired private lateinit var service: PasswordResetTokenService

    @Autowired private lateinit var repository: PasswordResetTokenRepository

    @Autowired private lateinit var userService: UserService

    @Autowired private lateinit var settingsService: ApplicationSettingsService

    @BeforeEach
    fun reset() {
        repository.deleteAll()
        // Restore the seeded default in case a previous test mutated it.
        settingsService.update(
            ApplicationSettingKey.AUTH_PASSWORD_RESET_TOKEN_TTL_MINUTES,
            "60",
            updatedBy = "test-setup",
        )
    }

    private fun createUser(): io.plugwerk.server.domain.UserEntity {
        val tag = UUID.randomUUID().toString().take(8)
        return userService.createSelfRegistered(
            username = "alice-$tag",
            email = "alice-$tag@example.com",
            password = "correct-horse-battery-staple",
            displayName = "Alice $tag",
            enabled = true,
        )
    }

    @Test
    fun `issue stores the token hash, not the raw token, and returns the raw token to the caller`() {
        val user = createUser()
        val issued = service.issue(user)

        // 32-byte entropy → base64url with no padding → 43 chars.
        assertThat(issued.rawToken).hasSize(43)
        assertThat(issued.rawToken).matches("[A-Za-z0-9_-]+")
        val rows = repository.findAll()
        assertThat(rows).hasSize(1)
        assertThat(rows[0].tokenHash).hasSize(64)
        assertThat(rows[0].tokenHash).doesNotContain(issued.rawToken)
    }

    @Test
    fun `issue uses the TTL configured in application settings (default 60 minutes)`() {
        val user = createUser()
        val issued = service.issue(user)

        val secondsUntilExpiry = java.time.Duration.between(
            OffsetDateTime.now(),
            issued.expiresAt,
        ).seconds
        assertThat(secondsUntilExpiry).isBetween(60 * 60 - 5, 60 * 60 + 5)
    }

    @Test
    fun `changing the TTL setting affects subsequently issued tokens (operator-tunable)`() {
        settingsService.update(
            ApplicationSettingKey.AUTH_PASSWORD_RESET_TOKEN_TTL_MINUTES,
            "5",
            updatedBy = "test",
        )
        val user = createUser()

        val issued = service.issue(user)

        val secondsUntilExpiry = java.time.Duration.between(
            OffsetDateTime.now(),
            issued.expiresAt,
        ).seconds
        // 5 minutes ± a small slack for clock drift across the test boundary.
        assertThat(secondsUntilExpiry).isBetween(5 * 60 - 5, 5 * 60 + 5)
    }

    @Test
    fun `consume returns the linked user and marks the row consumed (single-use)`() {
        val user = createUser()
        val issued = service.issue(user)

        val consumed = service.consume(issued.rawToken)
        assertThat(consumed.id).isEqualTo(user.id)

        val row = repository.findAll().single()
        assertThat(row.consumedAt).isNotNull

        assertThatThrownBy { service.consume(issued.rawToken) }
            .isInstanceOf(InvalidPasswordResetTokenException::class.java)
            .hasMessageContaining("already been used")
    }

    @Test
    fun `consume rejects an unknown token`() {
        assertThatThrownBy { service.consume("never-issued-token") }
            .isInstanceOf(InvalidPasswordResetTokenException::class.java)
            .hasMessageContaining("invalid")
    }

    @Test
    fun `consume rejects an expired token`() {
        val user = createUser()
        val issued = service.issue(user)
        val row = repository.findAll().single()
        row.expiresAt = OffsetDateTime.now().minusMinutes(1)
        repository.save(row)

        assertThatThrownBy { service.consume(issued.rawToken) }
            .isInstanceOf(InvalidPasswordResetTokenException::class.java)
            .hasMessageContaining("expired")
    }

    @Test
    fun `re-issuing for the same user invalidates the previous token (supersede semantics)`() {
        val user = createUser()
        val first = service.issue(user)

        val second = service.issue(user)

        assertThatThrownBy { service.consume(first.rawToken) }
            .isInstanceOf(InvalidPasswordResetTokenException::class.java)
        val redeemed = service.consume(second.rawToken)
        assertThat(redeemed.id).isEqualTo(user.id)
    }

    @Test
    fun `sweep removes rows past the grace window and leaves fresh ones intact`() {
        val user = createUser()
        val keep = service.issue(user)

        val stale = PasswordResetTokenEntity(
            user = user,
            tokenHash = "stale".repeat(13).take(64),
            expiresAt = OffsetDateTime.now().minusDays(10),
        )
        repository.save(stale)

        service.sweep()

        val remaining = repository.findAll()
        assertThat(remaining).hasSize(1)
        assertThat(remaining[0].tokenHash).isNotEqualTo(stale.tokenHash)
        val redeemed = service.consume(keep.rawToken)
        assertThat(redeemed.id).isEqualTo(user.id)
    }
}
