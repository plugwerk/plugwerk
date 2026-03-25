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

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Verifies that the deprecated [DevUserCredentialValidator] always returns false.
 *
 * This validator was replaced by [DatabaseUserCredentialValidator] in Phase 2. It is
 * kept for reference only and must not grant access to any credentials.
 */
@Suppress("DEPRECATION")
class DevUserCredentialValidatorTest {

    private val validator = DevUserCredentialValidator()

    @Test
    fun `always returns false regardless of credentials`() {
        assertThat(validator.validate("admin", "admin")).isFalse()
        assertThat(validator.validate("test", "test")).isFalse()
        assertThat(validator.validate("", "")).isFalse()
    }
}
