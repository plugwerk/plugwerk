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
package io.plugwerk.server.service

import io.plugwerk.server.PlugwerkProperties
import io.plugwerk.server.domain.RevokedTokenEntity
import io.plugwerk.server.domain.UserEntity
import io.plugwerk.server.repository.RevokedTokenRepository
import io.plugwerk.server.repository.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.Optional
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class TokenRevocationServiceTest {

    @Mock lateinit var revokedTokenRepository: RevokedTokenRepository

    @Mock lateinit var userRepository: UserRepository

    private lateinit var service: TokenRevocationService

    private val props = PlugwerkProperties(
        auth = PlugwerkProperties.AuthProperties(
            jwtSecret = "test-secret-at-least-32-chars-long!!",
            tokenValidityHours = 8,
        ),
    )

    @BeforeEach
    fun setUp() {
        service = TokenRevocationService(revokedTokenRepository, userRepository, props)
    }

    @Test
    fun `revokeToken persists entry to database`() {
        val jti = UUID.randomUUID().toString()
        whenever(revokedTokenRepository.existsByJti(jti)).thenReturn(false)
        whenever(revokedTokenRepository.save(any<RevokedTokenEntity>())).thenAnswer { it.arguments[0] }

        service.revokeToken(jti, "alice", Instant.now().plusSeconds(3600))

        verify(revokedTokenRepository).save(argThat { this.jti == jti && this.username == "alice" })
    }

    @Test
    fun `revokeToken skips duplicate jti`() {
        val jti = UUID.randomUUID().toString()
        whenever(revokedTokenRepository.existsByJti(jti)).thenReturn(true)

        service.revokeToken(jti, "alice", Instant.now().plusSeconds(3600))

        verify(revokedTokenRepository, never()).save(any())
    }

    @Test
    fun `isRevoked returns true for explicitly revoked token`() {
        val jti = UUID.randomUUID().toString()
        whenever(revokedTokenRepository.existsByJti(jti)).thenReturn(true)

        assertThat(service.isRevoked(jti, "alice", Instant.now())).isTrue()
    }

    @Test
    fun `isRevoked returns false for non-revoked token`() {
        val jti = UUID.randomUUID().toString()
        whenever(revokedTokenRepository.existsByJti(jti)).thenReturn(false)
        whenever(userRepository.findByUsername("alice")).thenReturn(
            Optional.of(
                UserEntity(username = "alice", passwordHash = "hash"),
            ),
        )

        assertThat(service.isRevoked(jti, "alice", Instant.now())).isFalse()
    }

    @Test
    fun `isRevoked returns true when token issued before password invalidation`() {
        val jti = UUID.randomUUID().toString()
        val passwordChangedAt = OffsetDateTime.now(ZoneOffset.UTC)
        val tokenIssuedAt = passwordChangedAt.toInstant().minusSeconds(60)

        whenever(revokedTokenRepository.existsByJti(jti)).thenReturn(false)
        whenever(userRepository.findByUsername("alice")).thenReturn(
            Optional.of(
                UserEntity(
                    username = "alice",
                    passwordHash = "hash",
                    passwordInvalidatedBefore = passwordChangedAt,
                ),
            ),
        )

        assertThat(service.isRevoked(jti, "alice", tokenIssuedAt)).isTrue()
    }

    @Test
    fun `isRevoked returns false when token issued after password invalidation`() {
        val jti = UUID.randomUUID().toString()
        val passwordChangedAt = OffsetDateTime.now(ZoneOffset.UTC).minusSeconds(60)
        val tokenIssuedAt = Instant.now()

        whenever(revokedTokenRepository.existsByJti(jti)).thenReturn(false)
        whenever(userRepository.findByUsername("alice")).thenReturn(
            Optional.of(
                UserEntity(
                    username = "alice",
                    passwordHash = "hash",
                    passwordInvalidatedBefore = passwordChangedAt,
                ),
            ),
        )

        assertThat(service.isRevoked(jti, "alice", tokenIssuedAt)).isFalse()
    }

    @Test
    fun `revokeAllForUser sets passwordInvalidatedBefore`() {
        val user = UserEntity(username = "alice", passwordHash = "hash")
        whenever(userRepository.findByUsername("alice")).thenReturn(Optional.of(user))
        whenever(userRepository.save(any<UserEntity>())).thenAnswer { it.arguments[0] }

        service.revokeAllForUser("alice")

        verify(userRepository).save(argThat { passwordInvalidatedBefore != null })
    }

    @Test
    fun `cleanupExpired deletes expired entries`() {
        whenever(revokedTokenRepository.deleteExpiredBefore(any())).thenReturn(3)

        service.cleanupExpired()

        verify(revokedTokenRepository).deleteExpiredBefore(any())
    }
}
