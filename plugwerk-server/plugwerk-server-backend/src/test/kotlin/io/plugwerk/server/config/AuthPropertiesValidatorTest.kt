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
package io.plugwerk.server.config

import io.plugwerk.server.PlugwerkProperties
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.validation.BeanPropertyBindingResult

class AuthPropertiesValidatorTest {

    private val validator = AuthPropertiesValidator()

    @Test
    fun `supports PlugwerkProperties`() {
        assertTrue(validator.supports(PlugwerkProperties::class.java))
    }

    @Test
    fun `does not support unrelated class`() {
        assertFalse(validator.supports(String::class.java))
    }

    @Test
    fun `accepts unique jwt-secret and encryption-key`() {
        val props = PlugwerkProperties(
            auth = PlugwerkProperties.AuthProperties(
                jwtSecret = "a-unique-production-secret-at-least-32ch",
                encryptionKey = "prod-encrypt-16c",
            ),
        )
        val errors = BeanPropertyBindingResult(props, "plugwerkProperties")

        validator.validate(props, errors)

        assertFalse(errors.hasErrors(), "Expected no validation errors but got: ${errors.allErrors}")
    }

    @Test
    fun `rejects blocked jwt-secret`() {
        for (blocked in AuthPropertiesValidator.BLOCKED_JWT_SECRETS) {
            val props = PlugwerkProperties(
                auth = PlugwerkProperties.AuthProperties(
                    jwtSecret = blocked,
                    encryptionKey = "safe-encrypt-16c",
                ),
            )
            val errors = BeanPropertyBindingResult(props, "plugwerkProperties")

            validator.validate(props, errors)

            assertTrue(errors.hasFieldErrors("auth.jwtSecret"), "Expected error for blocked jwt-secret: $blocked")
        }
    }

    @Test
    fun `rejects blocked encryption-key`() {
        for (blocked in AuthPropertiesValidator.BLOCKED_ENCRYPTION_KEYS) {
            val props = PlugwerkProperties(
                auth = PlugwerkProperties.AuthProperties(
                    jwtSecret = "a-unique-production-secret-at-least-32ch",
                    encryptionKey = blocked,
                ),
            )
            val errors = BeanPropertyBindingResult(props, "plugwerkProperties")

            validator.validate(props, errors)

            assertTrue(
                errors.hasFieldErrors("auth.encryptionKey"),
                "Expected error for blocked encryption-key: $blocked",
            )
        }
    }

    @Test
    fun `rejects both blocked values simultaneously`() {
        val props = PlugwerkProperties(
            auth = PlugwerkProperties.AuthProperties(
                jwtSecret = "dev-secret-change-in-production-min32chars!!",
                encryptionKey = "change-me-16char",
            ),
        )
        val errors = BeanPropertyBindingResult(props, "plugwerkProperties")

        validator.validate(props, errors)

        assertTrue(errors.hasFieldErrors("auth.jwtSecret"))
        assertTrue(errors.hasFieldErrors("auth.encryptionKey"))
    }
}
