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
package io.plugwerk.server.repository

import io.plugwerk.server.AbstractRepositoryTest
import io.plugwerk.server.domain.UserEntity
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DataIntegrityViolationException
import kotlin.test.assertFailsWith

class UserRepositoryTest : AbstractRepositoryTest() {

    @Autowired
    lateinit var userRepository: UserRepository

    private fun user(username: String, email: String? = null) =
        UserEntity(username = username, email = email, passwordHash = "\$2a\$12\$hash")

    @Test
    fun `findByUsername returns user when username exists`() {
        userRepository.save(user("alice"))

        val found = userRepository.findByUsername("alice")

        assertThat(found).isPresent
        assertThat(found.get().username).isEqualTo("alice")
    }

    @Test
    fun `findByUsername returns empty when username does not exist`() {
        val found = userRepository.findByUsername("nobody")

        assertThat(found).isEmpty
    }

    @Test
    fun `findByUsername is case-sensitive`() {
        userRepository.save(user("alice"))

        assertThat(userRepository.findByUsername("Alice")).isEmpty
        assertThat(userRepository.findByUsername("ALICE")).isEmpty
    }

    @Test
    fun `existsByUsername returns true for existing user`() {
        userRepository.save(user("bob"))

        assertThat(userRepository.existsByUsername("bob")).isTrue()
        assertThat(userRepository.existsByUsername("notbob")).isFalse()
    }

    @Test
    fun `save fails on duplicate username`() {
        userRepository.save(user("duplicate"))
        userRepository.flush()

        assertFailsWith<DataIntegrityViolationException> {
            userRepository.saveAndFlush(user("duplicate"))
        }
    }

    @Test
    fun `save fails on duplicate email`() {
        userRepository.save(user("alice", email = "alice@example.com"))
        userRepository.flush()

        assertFailsWith<DataIntegrityViolationException> {
            userRepository.saveAndFlush(user("alice2", email = "alice@example.com"))
        }
    }

    @Test
    fun `passwordChangeRequired defaults to true`() {
        val saved = userRepository.save(user("newuser"))

        assertThat(saved.passwordChangeRequired).isTrue()
    }

    @Test
    fun `enabled defaults to true`() {
        val saved = userRepository.save(user("activeuser"))

        assertThat(saved.enabled).isTrue()
    }
}
