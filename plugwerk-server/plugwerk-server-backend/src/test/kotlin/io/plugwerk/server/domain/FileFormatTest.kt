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
package io.plugwerk.server.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class FileFormatTest {

    @Test
    fun `zip extension resolves to ZIP`() {
        assertThat(FileFormat.fromExtension("zip")).isEqualTo(FileFormat.ZIP)
    }

    @ParameterizedTest
    @ValueSource(strings = ["ZIP", "Zip", "zIp"])
    fun `extension matching is case-insensitive`(extension: String) {
        assertThat(FileFormat.fromExtension(extension)).isEqualTo(FileFormat.ZIP)
    }

    @Test
    fun `jar extension resolves to JAR`() {
        assertThat(FileFormat.fromExtension("jar")).isEqualTo(FileFormat.JAR)
    }

    @ParameterizedTest
    @ValueSource(strings = ["jar", "war", "tar", "", "unknown"])
    fun `unrecognized extensions default to JAR`(extension: String) {
        assertThat(FileFormat.fromExtension(extension)).isEqualTo(FileFormat.JAR)
    }
}
