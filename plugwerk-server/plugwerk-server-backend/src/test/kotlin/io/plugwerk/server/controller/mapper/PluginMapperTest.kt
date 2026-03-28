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

import io.plugwerk.api.model.PluginDto
import io.plugwerk.api.model.PluginReleaseDto
import io.plugwerk.server.domain.NamespaceEntity
import io.plugwerk.server.domain.PluginEntity
import io.plugwerk.server.domain.PluginReleaseEntity
import io.plugwerk.spi.model.PluginStatus
import io.plugwerk.spi.model.ReleaseStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class PluginMapperTest {

    @Mock
    lateinit var releaseMapper: PluginReleaseMapper

    @InjectMocks
    lateinit var mapper: PluginMapper

    private val namespace = NamespaceEntity(slug = "acme", ownerOrg = "ACME Corp")
    private val plugin = PluginEntity(
        id = UUID.randomUUID(),
        namespace = namespace,
        pluginId = "my-plugin",
        name = "My Plugin",
        description = "A great plugin",
        author = "ACME",
        categories = arrayOf("tools", "productivity"),
        tags = arrayOf("kotlin", "pf4j"),
        status = PluginStatus.ACTIVE,
    )

    @Test
    fun `toDto maps all fields correctly with latestRelease`() {
        val release = PluginReleaseEntity(
            id = UUID.randomUUID(),
            plugin = plugin,
            version = "2.0.0",
            artifactSha256 = "abc",
            artifactKey = "acme/my-plugin/2.0.0",
            status = ReleaseStatus.PUBLISHED,
        )
        val releaseDto = PluginReleaseDto(
            id = release.id!!,
            pluginId = "my-plugin",
            version = "2.0.0",
            status = PluginReleaseDto.Status.PUBLISHED,
        )
        whenever(releaseMapper.toDto(any(), eq("my-plugin"))).thenReturn(releaseDto)

        val dto = mapper.toDto(plugin, "acme", release)

        assertThat(dto.id).isEqualTo(plugin.id)
        assertThat(dto.pluginId).isEqualTo("my-plugin")
        assertThat(dto.name).isEqualTo("My Plugin")
        assertThat(dto.description).isEqualTo("A great plugin")
        assertThat(dto.author).isEqualTo("ACME")
        assertThat(dto.namespace).isEqualTo("acme")
        assertThat(dto.status).isEqualTo(PluginDto.Status.ACTIVE)
        assertThat(dto.categories).containsExactly("tools", "productivity")
        assertThat(dto.tags).containsExactly("kotlin", "pf4j")
        assertThat(dto.latestRelease).isNotNull
        assertThat(dto.latestRelease?.version).isEqualTo("2.0.0")
    }

    @Test
    fun `toDto maps SUSPENDED status`() {
        val suspended = PluginEntity(
            id = UUID.randomUUID(),
            namespace = namespace,
            pluginId = "p",
            name = "P",
            status = PluginStatus.SUSPENDED,
        )
        assertThat(mapper.toDto(suspended, "acme").status).isEqualTo(PluginDto.Status.SUSPENDED)
    }

    @Test
    fun `toDto maps ARCHIVED status`() {
        val archived = PluginEntity(
            id = UUID.randomUUID(),
            namespace = namespace,
            pluginId = "p",
            name = "P",
            status = PluginStatus.ARCHIVED,
        )
        assertThat(mapper.toDto(archived, "acme").status).isEqualTo(PluginDto.Status.ARCHIVED)
    }

    @Test
    fun `toDto sets latestRelease to null when not provided`() {
        val dto = mapper.toDto(plugin, "acme")
        assertThat(dto.latestRelease).isNull()
    }

    @Test
    fun `toDto returns null for empty categories and tags`() {
        val minimal = PluginEntity(
            id = UUID.randomUUID(),
            namespace = namespace,
            pluginId = "p",
            name = "Minimal",
        )
        val dto = mapper.toDto(minimal, "acme")
        assertThat(dto.categories).isNull()
        assertThat(dto.tags).isNull()
    }
}
