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
package io.plugwerk.server.repository

import io.plugwerk.server.AbstractRepositoryTest
import io.plugwerk.server.domain.RevokedTokenEntity
import io.plugwerk.server.domain.UserEntity
import io.plugwerk.server.domain.UserSource
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

/**
 * Behavioural tests for [RevokedTokenRepository] focused on the user_id FK
 * introduced in migration 0024 (issue #422). The schema-level CASCADE is
 * verified separately by [RevokedTokenUserFkMigrationIT] against PostgreSQL —
 * the H2 test profile uses Hibernate-generated DDL, so we only exercise the
 * Spring Data query semantics here.
 */
class RevokedTokenRepositoryTest : AbstractRepositoryTest() {

    @Autowired
    lateinit var revokedTokenRepository: RevokedTokenRepository

    @Autowired
    lateinit var userRepository: UserRepository

    private fun newUser(username: String): UserEntity = userRepository.save(
        UserEntity(
            username = username,
            displayName = username,
            email = "$username@example.test",
            source = UserSource.INTERNAL,
            passwordHash = "\$2a\$12\$hash",
        ),
    )

    private fun newRevokedToken(userId: UUID, jtiSuffix: String): RevokedTokenEntity = revokedTokenRepository.save(
        RevokedTokenEntity(
            jti = jtiSuffix.padEnd(64, '0'),
            userId = userId,
            expiresAt = OffsetDateTime.now(ZoneOffset.UTC).plusHours(1),
        ),
    )

    @Test
    fun `deleteByUserId removes all rows for the given user and leaves others untouched`() {
        val alice = newUser("alice")
        val bob = newUser("bob")
        newRevokedToken(requireNotNull(alice.id), "a-1")
        newRevokedToken(requireNotNull(alice.id), "a-2")
        newRevokedToken(requireNotNull(bob.id), "b-1")

        val deleted = revokedTokenRepository.deleteByUserId(requireNotNull(alice.id))

        assertThat(deleted).isEqualTo(2)
        assertThat(revokedTokenRepository.findAll())
            .extracting<UUID> { it.userId }
            .containsExactly(requireNotNull(bob.id))
    }

    @Test
    fun `deleteByUserId returns zero when no rows exist for the user`() {
        val alice = newUser("alice")

        val deleted = revokedTokenRepository.deleteByUserId(requireNotNull(alice.id))

        assertThat(deleted).isZero()
    }

    @Test
    fun `existsByJti finds the row regardless of which user owns it`() {
        val alice = newUser("alice")
        val token = newRevokedToken(requireNotNull(alice.id), "lookup")

        assertThat(revokedTokenRepository.existsByJti(token.jti)).isTrue()
        assertThat(revokedTokenRepository.existsByJti("missing".padEnd(64, '0'))).isFalse()
    }
}
