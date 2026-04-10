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
package io.plugwerk.server.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.boot.info.BuildProperties

class VersionProviderTest {

    @Test
    fun `returns version from BuildProperties when available`() {
        val buildProperties = mock<BuildProperties>()
        whenever(buildProperties.version).thenReturn("1.2.3")

        val provider = VersionProvider(buildProperties)

        assertThat(provider.getVersion()).isEqualTo("1.2.3")
    }

    @Test
    fun `returns unknown when BuildProperties is null`() {
        val provider = VersionProvider(null)

        // In a test environment without a packaged JAR, manifest version is also null
        assertThat(provider.getVersion()).isEqualTo("unknown")
    }

    @Test
    fun `returns unknown when BuildProperties version is null`() {
        val buildProperties = mock<BuildProperties>()
        whenever(buildProperties.version).thenReturn(null)

        val provider = VersionProvider(buildProperties)

        // Falls back to manifest, which is null in test → "unknown"
        assertThat(provider.getVersion()).isEqualTo("unknown")
    }
}
