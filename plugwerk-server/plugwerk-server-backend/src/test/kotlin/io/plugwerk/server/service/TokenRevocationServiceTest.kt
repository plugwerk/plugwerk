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
import io.plugwerk.server.domain.UserSource
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
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
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

    private fun localUser(id: UUID, passwordInvalidatedBefore: OffsetDateTime? = null) = UserEntity(
        id = id,
        username = "u-$id",
        displayName = "User $id",
        email = "$id@example.test",
        source = UserSource.INTERNAL,
        passwordHash = "hash",
        passwordInvalidatedBefore = passwordInvalidatedBefore,
    )

    @Test
    fun `revokeToken persists entry to database`() {
        val jti = UUID.randomUUID().toString()
        val subject = UUID.randomUUID().toString()
        whenever(revokedTokenRepository.existsByJti(sha256Hex(jti))).thenReturn(false)
        whenever(revokedTokenRepository.save(any<RevokedTokenEntity>())).thenAnswer { it.arguments[0] }

        service.revokeToken(jti, subject, Instant.now().plusSeconds(3600))

        verify(revokedTokenRepository).save(argThat { this.jti == sha256Hex(jti) && this.subject == subject })
    }

    @Test
    fun `revokeToken stores SHA-256 hash of jti, never the plain value (SBS-013 #268)`() {
        val jti = "0123abcd-4567-89ef-0123-456789abcdef"
        whenever(revokedTokenRepository.existsByJti(any())).thenReturn(false)
        whenever(revokedTokenRepository.save(any<RevokedTokenEntity>())).thenAnswer { it.arguments[0] }

        service.revokeToken(jti, UUID.randomUUID().toString(), Instant.now().plusSeconds(3600))

        verify(revokedTokenRepository).save(
            argThat {
                this.jti != jti &&
                    this.jti.length == 64 &&
                    this.jti.matches(Regex("[0-9a-f]{64}")) &&
                    this.jti == sha256Hex(jti)
            },
        )
    }

    @Test
    fun `revokeToken skips duplicate jti`() {
        val jti = UUID.randomUUID().toString()
        whenever(revokedTokenRepository.existsByJti(sha256Hex(jti))).thenReturn(true)

        service.revokeToken(jti, UUID.randomUUID().toString(), Instant.now().plusSeconds(3600))

        verify(revokedTokenRepository, never()).save(any())
    }

    @Test
    fun `isRevoked returns true for explicitly revoked token`() {
        val jti = UUID.randomUUID().toString()
        whenever(revokedTokenRepository.existsByJti(sha256Hex(jti))).thenReturn(true)

        assertThat(service.isRevoked(jti, UUID.randomUUID().toString(), Instant.now())).isTrue()
    }

    @Test
    fun `isRevoked returns false for non-revoked token`() {
        val userId = UUID.randomUUID()
        val jti = UUID.randomUUID().toString()
        whenever(revokedTokenRepository.existsByJti(sha256Hex(jti))).thenReturn(false)
        whenever(userRepository.findById(userId)).thenReturn(Optional.of(localUser(userId)))

        assertThat(service.isRevoked(jti, userId.toString(), Instant.now())).isFalse()
    }

    @Test
    fun `isRevoked returns true when token issued before password invalidation`() {
        val userId = UUID.randomUUID()
        val jti = UUID.randomUUID().toString()
        val passwordChangedAt = OffsetDateTime.now(ZoneOffset.UTC)
        val tokenIssuedAt = passwordChangedAt.toInstant().minusSeconds(60)

        whenever(revokedTokenRepository.existsByJti(sha256Hex(jti))).thenReturn(false)
        whenever(userRepository.findById(userId)).thenReturn(
            Optional.of(localUser(userId, passwordInvalidatedBefore = passwordChangedAt)),
        )

        assertThat(service.isRevoked(jti, userId.toString(), tokenIssuedAt)).isTrue()
    }

    @Test
    fun `isRevoked returns false when token issued after password invalidation`() {
        val userId = UUID.randomUUID()
        val jti = UUID.randomUUID().toString()
        val passwordChangedAt = OffsetDateTime.now(ZoneOffset.UTC).minusSeconds(60)
        val tokenIssuedAt = Instant.now()

        whenever(revokedTokenRepository.existsByJti(sha256Hex(jti))).thenReturn(false)
        whenever(userRepository.findById(userId)).thenReturn(
            Optional.of(localUser(userId, passwordInvalidatedBefore = passwordChangedAt)),
        )

        assertThat(service.isRevoked(jti, userId.toString(), tokenIssuedAt)).isFalse()
    }

    @Test
    fun `isRevoked returns false when subject is not a UUID (legacy or forged)`() {
        // After #351 the JWT-`sub` is the plugwerk_user.id UUID. A non-UUID
        // subject can only originate from a forged or pre-#351 token. The
        // explicit-revocation path (above) still rejects forged tokens via
        // JWS verification before this check runs; here we just ensure the
        // bulk-invalidation path no-ops gracefully instead of throwing.
        val jti = UUID.randomUUID().toString()
        whenever(revokedTokenRepository.existsByJti(sha256Hex(jti))).thenReturn(false)

        assertThat(service.isRevoked(jti, "alice-legacy-username", Instant.now())).isFalse()
    }

    @Test
    fun `revokeAllForUser sets passwordInvalidatedBefore`() {
        val userId = UUID.randomUUID()
        val user = localUser(userId)
        whenever(userRepository.findById(userId)).thenReturn(Optional.of(user))
        whenever(userRepository.save(any<UserEntity>())).thenAnswer { it.arguments[0] }

        service.revokeAllForUser(userId)

        verify(userRepository).save(argThat { passwordInvalidatedBefore != null })
    }

    @Test
    fun `cleanupExpired deletes expired entries`() {
        whenever(revokedTokenRepository.deleteExpiredBefore(any())).thenReturn(3)

        service.cleanupExpired()

        verify(revokedTokenRepository).deleteExpiredBefore(any())
    }

    /**
     * Mirror of [TokenRevocationService.hashJti] — kept here so the test can
     * compute the expected hashed value to stub against without exposing the
     * private helper. If the production helper changes shape, this must
     * change in lockstep — and the regression test above will catch any
     * accidental drift.
     */
    private fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray(StandardCharsets.UTF_8))
        return buildString(digest.size * 2) {
            digest.forEach { append("%02x".format(it)) }
        }
    }
}
