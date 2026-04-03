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
package io.plugwerk.server.controller.mapper

import io.plugwerk.api.model.PluginReleaseDto
import io.plugwerk.server.domain.FileFormat
import io.plugwerk.server.domain.NamespaceEntity
import io.plugwerk.server.domain.PluginEntity
import io.plugwerk.server.domain.PluginReleaseEntity
import io.plugwerk.spi.model.ReleaseStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import tools.jackson.databind.ObjectMapper
import java.util.UUID

class PluginReleaseMapperTest {

    private val objectMapper = ObjectMapper()
    private val mapper = PluginReleaseMapper(objectMapper)

    private val namespace = NamespaceEntity(slug = "acme", ownerOrg = "ACME Corp")
    private val plugin = PluginEntity(
        id = UUID.randomUUID(),
        namespace = namespace,
        pluginId = "my-plugin",
        name = "My Plugin",
    )

    @Test
    fun `toDto maps all base fields correctly`() {
        val release = PluginReleaseEntity(
            id = UUID.randomUUID(),
            plugin = plugin,
            version = "1.2.3",
            artifactSha256 = "deadbeef",
            artifactKey = "acme/my-plugin/1.2.3",
            requiresSystemVersion = ">=2.0.0",
            status = ReleaseStatus.PUBLISHED,
        )

        val dto = mapper.toDto(release, "my-plugin")

        assertThat(dto.id).isEqualTo(release.id)
        assertThat(dto.pluginId).isEqualTo("my-plugin")
        assertThat(dto.version).isEqualTo("1.2.3")
        assertThat(dto.artifactSha256).isEqualTo("deadbeef")
        assertThat(dto.requiresSystemVersion).isEqualTo(">=2.0.0")
        assertThat(dto.status).isEqualTo(PluginReleaseDto.Status.PUBLISHED)
        assertThat(dto.fileFormat).isEqualTo(PluginReleaseDto.FileFormat.JAR)
        assertThat(dto.downloadCount).isEqualTo(0L)
    }

    @Test
    fun `toDto maps non-zero downloadCount`() {
        val release = PluginReleaseEntity(
            id = UUID.randomUUID(),
            plugin = plugin,
            version = "1.0.0",
            artifactSha256 = "abc",
            artifactKey = "key",
            downloadCount = 42L,
        )

        val dto = mapper.toDto(release, "my-plugin")

        assertThat(dto.downloadCount).isEqualTo(42L)
    }

    @Test
    fun `toDto deserializes plugin dependencies from JSON`() {
        val release = PluginReleaseEntity(
            id = UUID.randomUUID(),
            plugin = plugin,
            version = "1.0.0",
            artifactSha256 = "abc",
            artifactKey = "acme/my-plugin/1.0.0",
            pluginDependencies = """[{"id":"dep-plugin","version":">=1.0.0"}]""",
        )

        val dto = mapper.toDto(release, "my-plugin")

        assertThat(dto.pluginDependencies).hasSize(1)
        assertThat(dto.pluginDependencies!![0].id).isEqualTo("dep-plugin")
        assertThat(dto.pluginDependencies!![0].version).isEqualTo(">=1.0.0")
    }

    @Test
    fun `toDto returns null dependencies when field is null`() {
        val release = PluginReleaseEntity(
            id = UUID.randomUUID(),
            plugin = plugin,
            version = "1.0.0",
            artifactSha256 = "abc",
            artifactKey = "acme/my-plugin/1.0.0",
            pluginDependencies = null,
        )

        val dto = mapper.toDto(release, "my-plugin")

        assertThat(dto.pluginDependencies).isNull()
    }

    @Test
    fun `toDto maps fileFormat JAR correctly`() {
        val release = PluginReleaseEntity(
            id = UUID.randomUUID(),
            plugin = plugin,
            version = "1.0.0",
            artifactSha256 = "abc",
            artifactKey = "key",
            fileFormat = FileFormat.JAR,
        )

        val dto = mapper.toDto(release, "my-plugin")

        assertThat(dto.fileFormat).isEqualTo(PluginReleaseDto.FileFormat.JAR)
    }

    @Test
    fun `toDto maps fileFormat ZIP correctly`() {
        val release = PluginReleaseEntity(
            id = UUID.randomUUID(),
            plugin = plugin,
            version = "1.0.0",
            artifactSha256 = "abc",
            artifactKey = "key",
            fileFormat = FileFormat.ZIP,
        )

        val dto = mapper.toDto(release, "my-plugin")

        assertThat(dto.fileFormat).isEqualTo(PluginReleaseDto.FileFormat.ZIP)
    }

    @Test
    fun `toDto maps all release statuses correctly`() {
        val statusMap = mapOf(
            ReleaseStatus.DRAFT to PluginReleaseDto.Status.DRAFT,
            ReleaseStatus.PUBLISHED to PluginReleaseDto.Status.PUBLISHED,
            ReleaseStatus.DEPRECATED to PluginReleaseDto.Status.DEPRECATED,
            ReleaseStatus.YANKED to PluginReleaseDto.Status.YANKED,
        )
        statusMap.forEach { (entity, dto) ->
            val release = PluginReleaseEntity(
                id = UUID.randomUUID(),
                plugin = plugin,
                version = "1.0.0",
                artifactSha256 = "abc",
                artifactKey = "key",
                status = entity,
            )
            assertThat(mapper.toDto(release, "my-plugin").status).isEqualTo(dto)
        }
    }
}
