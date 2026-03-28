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

class PlugwerkPropertiesValidatorTest {

    private val validator = PlugwerkPropertiesValidator()

    private fun validAuth() = PlugwerkProperties.AuthProperties(
        jwtSecret = "a-unique-production-secret-at-least-32ch",
        encryptionKey = "prod-encrypt-16c",
    )

    private fun propsWithBaseUrl(baseUrl: String) = PlugwerkProperties(
        auth = validAuth(),
        server = PlugwerkProperties.ServerProperties(baseUrl = baseUrl),
    )

    private fun validate(props: PlugwerkProperties): BeanPropertyBindingResult {
        val errors = BeanPropertyBindingResult(props, "plugwerkProperties")
        validator.validate(props, errors)
        return errors
    }

    // --- supports ---

    @Test
    fun `supports PlugwerkProperties`() {
        assertTrue(validator.supports(PlugwerkProperties::class.java))
    }

    @Test
    fun `does not support unrelated class`() {
        assertFalse(validator.supports(String::class.java))
    }

    // --- auth secret blocklist ---

    @Test
    fun `accepts unique jwt-secret and encryption-key`() {
        val errors = validate(PlugwerkProperties(auth = validAuth()))
        assertFalse(errors.hasErrors(), "Expected no validation errors but got: ${errors.allErrors}")
    }

    @Test
    fun `rejects blocked jwt-secret`() {
        for (blocked in PlugwerkPropertiesValidator.BLOCKED_JWT_SECRETS) {
            val props = PlugwerkProperties(
                auth = PlugwerkProperties.AuthProperties(
                    jwtSecret = blocked,
                    encryptionKey = "safe-encrypt-16c",
                ),
            )
            val errors = validate(props)
            assertTrue(errors.hasFieldErrors("auth.jwtSecret"), "Expected error for blocked jwt-secret: $blocked")
        }
    }

    @Test
    fun `rejects blocked encryption-key`() {
        for (blocked in PlugwerkPropertiesValidator.BLOCKED_ENCRYPTION_KEYS) {
            val props = PlugwerkProperties(
                auth = PlugwerkProperties.AuthProperties(
                    jwtSecret = "a-unique-production-secret-at-least-32ch",
                    encryptionKey = blocked,
                ),
            )
            val errors = validate(props)
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
        val errors = validate(props)
        assertTrue(errors.hasFieldErrors("auth.jwtSecret"))
        assertTrue(errors.hasFieldErrors("auth.encryptionKey"))
    }

    // --- baseUrl validation ---

    @Test
    fun `accepts valid http baseUrl`() {
        val errors = validate(propsWithBaseUrl("http://localhost:8080"))
        assertFalse(errors.hasFieldErrors("server.baseUrl"))
    }

    @Test
    fun `accepts valid https baseUrl`() {
        val errors = validate(propsWithBaseUrl("https://plugins.example.com"))
        assertFalse(errors.hasFieldErrors("server.baseUrl"))
    }

    @Test
    fun `accepts baseUrl with context path`() {
        val errors = validate(propsWithBaseUrl("https://example.com/plugwerk"))
        assertFalse(errors.hasFieldErrors("server.baseUrl"))
    }

    @Test
    fun `accepts baseUrl with port and path`() {
        val errors = validate(propsWithBaseUrl("https://example.com:9443/plugins"))
        assertFalse(errors.hasFieldErrors("server.baseUrl"))
    }

    @Test
    fun `rejects blank baseUrl`() {
        val errors = validate(propsWithBaseUrl(""))
        assertTrue(errors.hasFieldErrors("server.baseUrl"))
    }

    @Test
    fun `rejects baseUrl with ftp scheme`() {
        val errors = validate(propsWithBaseUrl("ftp://evil.com"))
        assertTrue(errors.hasFieldErrors("server.baseUrl"))
    }

    @Test
    fun `rejects baseUrl with javascript scheme`() {
        val errors = validate(propsWithBaseUrl("javascript:alert(1)"))
        assertTrue(errors.hasFieldErrors("server.baseUrl"))
    }

    @Test
    fun `rejects baseUrl without scheme`() {
        val errors = validate(propsWithBaseUrl("just-a-hostname"))
        assertTrue(errors.hasFieldErrors("server.baseUrl"))
    }

    @Test
    fun `rejects baseUrl with query string`() {
        val errors = validate(propsWithBaseUrl("https://example.com?redirect=evil"))
        assertTrue(errors.hasFieldErrors("server.baseUrl"))
    }

    @Test
    fun `rejects baseUrl with fragment`() {
        val errors = validate(propsWithBaseUrl("https://example.com#foo"))
        assertTrue(errors.hasFieldErrors("server.baseUrl"))
    }

    @Test
    fun `rejects baseUrl with trailing slash`() {
        val errors = validate(propsWithBaseUrl("https://example.com/"))
        assertTrue(errors.hasFieldErrors("server.baseUrl"))
    }
}
