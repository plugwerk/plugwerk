/*
 * Copyright (c) 2025-present devtank42 GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.plugwerk.client.internal

import io.plugwerk.api.model.PluginReleaseDto
import io.plugwerk.spi.model.ReleaseStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.util.UUID

/**
 * Unit tests for the internal DTO → SPI mappers in `DtoMappers.kt`.
 *
 * The higher-level catalog/updater tests exercise these mappers only through the
 * `published` JSON path; this test locks the full field-mapping contract and
 * every branch of the status mapping directly.
 */
class DtoMappersTest {

    private fun dto(
        status: PluginReleaseDto.Status = PluginReleaseDto.Status.PUBLISHED,
        artifactSha256: String? = "a".repeat(64),
        requiresSystemVersion: String? = ">=2.0.0",
    ) = PluginReleaseDto(
        id = UUID.randomUUID(),
        pluginId = "io.example.my-plugin",
        version = "1.2.3",
        status = status,
        artifactSha256 = artifactSha256,
        requiresSystemVersion = requiresSystemVersion,
    )

    @Test
    fun `toReleaseInfo copies all mapped fields`() {
        val info = dto().toReleaseInfo()

        assertEquals("io.example.my-plugin", info.pluginId)
        assertEquals("1.2.3", info.version)
        assertEquals("a".repeat(64), info.artifactSha256)
        assertEquals(">=2.0.0", info.requiresSystemVersion)
        assertEquals(ReleaseStatus.PUBLISHED, info.status)
    }

    @Test
    fun `toReleaseInfo leaves downloadUrl null - it is not part of the release DTO`() {
        // The download URL is resolved separately via PlugwerkCatalog.getPluginRelease;
        // the mapper must never fabricate one, otherwise consumers could try to
        // download from a null/blank URL.
        assertNull(dto().toReleaseInfo().downloadUrl)
    }

    @Test
    fun `toReleaseInfo preserves null artifactSha256 as unverified`() {
        val info = dto(artifactSha256 = null).toReleaseInfo()
        assertNull(info.artifactSha256)
    }

    @Test
    fun `toReleaseInfo preserves null requiresSystemVersion as no constraint`() {
        val info = dto(requiresSystemVersion = null).toReleaseInfo()
        assertNull(info.requiresSystemVersion)
    }

    @ParameterizedTest
    @CsvSource(
        "DRAFT, DRAFT",
        "PUBLISHED, PUBLISHED",
        "DEPRECATED, DEPRECATED",
        "YANKED, YANKED",
    )
    fun `toReleaseStatus maps every DTO status to its SPI counterpart`(
        dtoStatus: PluginReleaseDto.Status,
        expected: ReleaseStatus,
    ) {
        assertEquals(expected, dtoStatus.toReleaseStatus())
        assertEquals(expected, dto(status = dtoStatus).toReleaseInfo().status)
    }
}
