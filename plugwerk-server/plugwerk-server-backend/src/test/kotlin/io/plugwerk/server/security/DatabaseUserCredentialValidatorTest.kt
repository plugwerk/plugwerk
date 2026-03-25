/*
 * Plugwerk — Plugin Marketplace for the PF4J Ecosystem
 * Copyright (C) 2026 devtank42 GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package io.plugwerk.server.security

import io.plugwerk.server.domain.UserEntity
import io.plugwerk.server.repository.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.whenever
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import java.util.Optional

@ExtendWith(MockitoExtension::class)
class DatabaseUserCredentialValidatorTest {

    @Mock
    private lateinit var userRepository: UserRepository

    @Mock
    private lateinit var passwordEncoder: BCryptPasswordEncoder

    @InjectMocks
    private lateinit var validator: DatabaseUserCredentialValidator

    private fun userWith(enabled: Boolean, hash: String = "\$2a\$12\$hash") =
        UserEntity(username = "alice", passwordHash = hash, enabled = enabled)

    @Test
    fun `returns true for valid credentials of an enabled user`() {
        val user = userWith(enabled = true, hash = "\$2a\$12\$correcthash")
        whenever(userRepository.findByUsername("alice")).thenReturn(Optional.of(user))
        whenever(passwordEncoder.matches("secret", user.passwordHash)).thenReturn(true)

        assertThat(validator.validate("alice", "secret")).isTrue()
    }

    @Test
    fun `returns false when user does not exist`() {
        whenever(userRepository.findByUsername("nobody")).thenReturn(Optional.empty())

        assertThat(validator.validate("nobody", "any")).isFalse()
    }

    @Test
    fun `returns false when account is disabled`() {
        val user = userWith(enabled = false)
        whenever(userRepository.findByUsername("alice")).thenReturn(Optional.of(user))

        assertThat(validator.validate("alice", "secret")).isFalse()
    }

    @Test
    fun `returns false when password does not match`() {
        val user = userWith(enabled = true)
        whenever(userRepository.findByUsername("alice")).thenReturn(Optional.of(user))
        whenever(passwordEncoder.matches("wrong", user.passwordHash)).thenReturn(false)

        assertThat(validator.validate("alice", "wrong")).isFalse()
    }
}
