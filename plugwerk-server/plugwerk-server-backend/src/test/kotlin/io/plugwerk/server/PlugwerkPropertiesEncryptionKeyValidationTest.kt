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
 * Pins the validation contract for [PlugwerkProperties.AuthProperties.encryptionKey].
 * Audit row SBS-003 / ADR-0022: the constraint was previously `@Size(min = 16, max = 16)`,
 * which was read as "AES-128 only" even though Spring's `Encryptors.text()` always derives
 * a 256-bit key via PBKDF2. The relaxed bounds are `min = 16, max = 256` with 32+
 * recommended in operator-facing docs.
 */
class PlugwerkPropertiesEncryptionKeyValidationTest {

    private val validator: Validator =
        Validation.buildDefaultValidatorFactory().use { it.validator }

    private fun authProps(encryptionKey: String) = PlugwerkProperties.AuthProperties(
        jwtSecret = "a".repeat(32),
        encryptionKey = encryptionKey,
    )

    @Test
    fun `16-character encryption key is accepted (lower bound)`() {
        val props = authProps("k".repeat(16))
        assertThat(validator.validate(props)).isEmpty()
    }

    @Test
    fun `32-character encryption key is accepted (recommended length)`() {
        val props = authProps("k".repeat(32))
        assertThat(validator.validate(props)).isEmpty()
    }

    @Test
    fun `256-character encryption key is accepted (upper bound)`() {
        val props = authProps("k".repeat(256))
        assertThat(validator.validate(props)).isEmpty()
    }

    @Test
    fun `15-character encryption key is rejected (below minimum)`() {
        val props = authProps("k".repeat(15))
        val violations = validator.validate(props)
        assertThat(violations).hasSize(1)
        assertThat(violations.single().propertyPath.toString()).isEqualTo("encryptionKey")
        assertThat(violations.single().message).contains("at least 16 characters")
    }

    @Test
    fun `257-character encryption key is rejected (above maximum)`() {
        val props = authProps("k".repeat(257))
        val violations = validator.validate(props)
        assertThat(violations).hasSize(1)
        assertThat(violations.single().propertyPath.toString()).isEqualTo("encryptionKey")
    }

    @Test
    fun `blank encryption key is rejected`() {
        val props = authProps("")
        val violations = validator.validate(props)
        assertThat(violations).isNotEmpty
        assertThat(violations.map { it.propertyPath.toString() }).contains("encryptionKey")
    }
}
