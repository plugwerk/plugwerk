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

import io.plugwerk.server.PlugwerkProperties
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class DevUserCredentialValidatorTest {

    private fun validatorWith(vararg users: Pair<String, String>): DevUserCredentialValidator {
        val devUsers = users.map { (u, p) -> PlugwerkProperties.AuthProperties.DevUser(u, p) }
        val props = PlugwerkProperties(
            auth = PlugwerkProperties.AuthProperties(devUsers = devUsers),
        )
        return DevUserCredentialValidator(props)
    }

    @Test
    fun `returns true for valid credentials`() {
        val validator = validatorWith("test" to "test")
        assertThat(validator.validate("test", "test")).isTrue()
    }

    @Test
    fun `returns false for wrong password`() {
        val validator = validatorWith("test" to "test")
        assertThat(validator.validate("test", "wrong")).isFalse()
    }

    @Test
    fun `returns false for unknown username`() {
        val validator = validatorWith("test" to "test")
        assertThat(validator.validate("unknown", "test")).isFalse()
    }

    @Test
    fun `returns false when dev-users list is empty`() {
        val validator = validatorWith()
        assertThat(validator.validate("test", "test")).isFalse()
    }

    @Test
    fun `is case-sensitive for username`() {
        val validator = validatorWith("test" to "test")
        assertThat(validator.validate("Test", "test")).isFalse()
        assertThat(validator.validate("TEST", "test")).isFalse()
    }

    @Test
    fun `is case-sensitive for password`() {
        val validator = validatorWith("test" to "secret")
        assertThat(validator.validate("test", "Secret")).isFalse()
        assertThat(validator.validate("test", "SECRET")).isFalse()
    }

    @Test
    fun `validates first of multiple dev-users`() {
        val validator = validatorWith("alice" to "pass1", "bob" to "pass2")
        assertThat(validator.validate("alice", "pass1")).isTrue()
    }

    @Test
    fun `validates second of multiple dev-users`() {
        val validator = validatorWith("alice" to "pass1", "bob" to "pass2")
        assertThat(validator.validate("bob", "pass2")).isTrue()
    }

    @Test
    fun `cross-user password is rejected`() {
        val validator = validatorWith("alice" to "pass1", "bob" to "pass2")
        assertThat(validator.validate("alice", "pass2")).isFalse()
    }
}
