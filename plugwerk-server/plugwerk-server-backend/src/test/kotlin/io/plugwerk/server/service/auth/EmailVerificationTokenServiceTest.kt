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

import io.plugwerk.server.domain.UserSource
import io.plugwerk.server.repository.EmailVerificationTokenRepository
import io.plugwerk.server.service.UserService
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
 * H2-backed integration test for [EmailVerificationTokenService] (#420).
 *
 * Verifies the security-critical lifecycle properties: hash-at-rest (raw
 * token never persisted), single-use consume, expired-token rejection,
 * and the resend-supersedes-previous invariant.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(
    properties = [
        "spring.datasource.url=jdbc:h2:mem:email-verification;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
    ],
)
class EmailVerificationTokenServiceTest {

    @Autowired private lateinit var service: EmailVerificationTokenService

    @Autowired private lateinit var repository: EmailVerificationTokenRepository

    @Autowired private lateinit var userService: UserService

    @BeforeEach
    fun reset() {
        // Token rows only — user rows persist across tests because the
        // shared SpringBootTest application context caches the schema.
        // Each createUser() picks a fresh UUID-derived username so per-test
        // calls never collide with rows left behind by earlier tests.
        repository.deleteAll()
    }

    private fun createUser(): io.plugwerk.server.domain.UserEntity {
        val tag = UUID.randomUUID().toString().take(8)
        return userService.createSelfRegistered(
            username = "alice-$tag",
            email = "alice-$tag@example.com",
            password = "correct-horse-battery-staple",
            displayName = "Alice $tag",
            enabled = false,
        )
    }

    @Test
    fun `issue stores the token hash, not the raw token, and returns the raw token to the caller`() {
        val user = createUser()
        val issued = service.issue(user)

        // The raw token is base64url-encoded, 43 characters for 32-byte
        // entropy with no padding. The persisted hash is the 64-char SHA-256.
        assertThat(issued.rawToken).hasSize(43)
        assertThat(issued.rawToken).matches("[A-Za-z0-9_-]+")
        val rows = repository.findAll()
        assertThat(rows).hasSize(1)
        assertThat(rows[0].tokenHash).hasSize(64)
        assertThat(rows[0].tokenHash).doesNotContain(issued.rawToken)
        // Expiry approximately 24 h in the future.
        val secondsUntilExpiry = java.time.Duration.between(
            OffsetDateTime.now(),
            issued.expiresAt,
        ).seconds
        assertThat(secondsUntilExpiry).isBetween(24 * 3600 - 5, 24 * 3600 + 5)
    }

    @Test
    fun `consume returns the linked user and marks the row consumed (single-use)`() {
        val user = createUser()
        val issued = service.issue(user)

        val consumed = service.consume(issued.rawToken)
        assertThat(consumed.id).isEqualTo(user.id)

        val row = repository.findAll().single()
        assertThat(row.consumedAt).isNotNull

        // Re-using the same raw token must fail.
        assertThatThrownBy { service.consume(issued.rawToken) }
            .isInstanceOf(InvalidVerificationTokenException::class.java)
            .hasMessageContaining("already been used")
    }

    @Test
    fun `consume rejects an unknown token`() {
        assertThatThrownBy { service.consume("never-issued-token") }
            .isInstanceOf(InvalidVerificationTokenException::class.java)
            .hasMessageContaining("invalid")
    }

    @Test
    fun `consume rejects an expired token`() {
        val user = createUser()
        val issued = service.issue(user)
        // Force expiry by mutating the row directly — cleaner than waiting
        // 24h or injecting a Clock. The test owns the entity so mutation
        // is local and does not leak across tests.
        val row = repository.findAll().single()
        row.expiresAt = OffsetDateTime.now().minusMinutes(1)
        repository.save(row)

        assertThatThrownBy { service.consume(issued.rawToken) }
            .isInstanceOf(InvalidVerificationTokenException::class.java)
            .hasMessageContaining("expired")
    }

    @Test
    fun `re-issuing for the same user invalidates the previous token (resend semantics)`() {
        val user = createUser()
        val first = service.issue(user)

        val second = service.issue(user)

        // Two rows now (one per issue), but the older one is consumed
        // (semantically: "burned because superseded"). Trying to use the
        // first raw token must fail; the new one still works.
        assertThatThrownBy { service.consume(first.rawToken) }
            .isInstanceOf(InvalidVerificationTokenException::class.java)
        val verified = service.consume(second.rawToken)
        assertThat(verified.id).isEqualTo(user.id)
    }

    @Test
    fun `sweep removes rows past the grace window and leaves fresh ones intact`() {
        val user = createUser()
        val keep = service.issue(user)

        // Add a stale row directly via the repository — older than the
        // 7-day sweep grace.
        val stale = io.plugwerk.server.domain.EmailVerificationTokenEntity(
            user = user,
            tokenHash = "stale".repeat(13).take(64),
            expiresAt = OffsetDateTime.now().minusDays(10),
        )
        repository.save(stale)

        service.sweep()

        val remaining = repository.findAll()
        assertThat(remaining).hasSize(1)
        assertThat(remaining[0].tokenHash).isNotEqualTo(stale.tokenHash)
        // Sanity: the kept token is still consumable.
        val verified = service.consume(keep.rawToken)
        assertThat(verified.id).isEqualTo(user.id)
        // Suppress the "secondaryLink unused" warning in some IDEs.
        assertThat(verified.source).isEqualTo(UserSource.INTERNAL)
    }
}
