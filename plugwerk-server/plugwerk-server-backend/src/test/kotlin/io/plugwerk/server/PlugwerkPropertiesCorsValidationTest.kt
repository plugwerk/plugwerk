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
package io.plugwerk.server

import jakarta.validation.Validation
import jakarta.validation.Validator
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Validates [PlugwerkProperties.ServerProperties.CorsProperties] via the Bean Validation
 * framework, pinning the startup-time guard against the classic `"*"` +
 * `allow-credentials: true` misconfiguration. Audit row SBS-002.
 */
class PlugwerkPropertiesCorsValidationTest {

    private val validator: Validator =
        Validation.buildDefaultValidatorFactory().use { it.validator }

    @Test
    fun `default CorsProperties is valid (empty allow-list, credentials true)`() {
        val props = PlugwerkProperties.ServerProperties.CorsProperties()
        assertThat(validator.validate(props)).isEmpty()
    }

    @Test
    fun `wildcard origin with credentials is rejected`() {
        val props = PlugwerkProperties.ServerProperties.CorsProperties(
            allowedOrigins = listOf("*"),
            allowCredentials = true,
        )
        val violations = validator.validate(props)
        assertThat(violations).hasSize(1)
        assertThat(violations.single().message).contains("must not contain '*'")
    }

    @Test
    fun `wildcard origin without credentials is allowed`() {
        val props = PlugwerkProperties.ServerProperties.CorsProperties(
            allowedOrigins = listOf("*"),
            allowCredentials = false,
        )
        assertThat(validator.validate(props)).isEmpty()
    }

    @Test
    fun `explicit origins with credentials is allowed`() {
        val props = PlugwerkProperties.ServerProperties.CorsProperties(
            allowedOrigins = listOf("https://frontend.example.com", "https://admin.example.com"),
            allowCredentials = true,
        )
        assertThat(validator.validate(props)).isEmpty()
    }

    @Test
    fun `negative maxAge is rejected`() {
        val props = PlugwerkProperties.ServerProperties.CorsProperties(maxAge = -1)
        val violations = validator.validate(props)
        assertThat(violations).hasSize(1)
        assertThat(violations.single().propertyPath.toString()).isEqualTo("maxAge")
    }

    @Test
    fun `maxAge above 24 hours is rejected`() {
        val props = PlugwerkProperties.ServerProperties.CorsProperties(maxAge = 86_401)
        val violations = validator.validate(props)
        assertThat(violations).hasSize(1)
        assertThat(violations.single().propertyPath.toString()).isEqualTo("maxAge")
    }
}
