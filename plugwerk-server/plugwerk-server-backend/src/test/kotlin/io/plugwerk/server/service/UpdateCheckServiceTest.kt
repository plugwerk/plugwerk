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
package io.plugwerk.server.service

import io.plugwerk.api.model.InstalledPluginInfo
import io.plugwerk.common.model.ReleaseStatus
import io.plugwerk.server.domain.NamespaceEntity
import io.plugwerk.server.domain.PluginEntity
import io.plugwerk.server.domain.PluginReleaseEntity
import io.plugwerk.server.repository.NamespaceRepository
import io.plugwerk.server.repository.PluginReleaseRepository
import io.plugwerk.server.repository.PluginRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.whenever
import java.util.Optional
import java.util.UUID
import kotlin.test.assertFailsWith

@ExtendWith(MockitoExtension::class)
class UpdateCheckServiceTest {

    @Mock lateinit var namespaceRepository: NamespaceRepository

    @Mock lateinit var pluginRepository: PluginRepository

    @Mock lateinit var releaseRepository: PluginReleaseRepository

    @InjectMocks
    lateinit var updateCheckService: UpdateCheckService

    private val namespace = NamespaceEntity(slug = "acme", ownerOrg = "ACME Corp")
    private val plugin = PluginEntity(namespace = namespace, pluginId = "my-plugin", name = "My Plugin")

    private fun release(version: String, status: ReleaseStatus = ReleaseStatus.PUBLISHED) = PluginReleaseEntity(
        plugin = plugin,
        version = version,
        artifactSha256 = "sha-$version",
        artifactKey = "acme/my-plugin/$version",
        status = status,
    ).also { it.id = UUID.randomUUID() }

    @Test
    fun `checkUpdates returns update when newer version exists`() {
        whenever(namespaceRepository.findBySlug("acme")).thenReturn(Optional.of(namespace))
        whenever(pluginRepository.findByNamespaceAndPluginId(namespace, "my-plugin")).thenReturn(Optional.of(plugin))
        whenever(releaseRepository.findAllByPluginAndStatus(plugin, ReleaseStatus.PUBLISHED))
            .thenReturn(listOf(release("1.0.0"), release("1.1.0"), release("2.0.0")))

        val installed = listOf(InstalledPluginInfo(pluginId = "my-plugin", currentVersion = "1.0.0"))
        val response = updateCheckService.checkUpdates("acme", installed)

        assertThat(response.updates).hasSize(1)
        assertThat(response.updates.first().latestVersion).isEqualTo("2.0.0")
        assertThat(response.updates.first().currentVersion).isEqualTo("1.0.0")
    }

    @Test
    fun `checkUpdates returns no updates when already on latest`() {
        whenever(namespaceRepository.findBySlug("acme")).thenReturn(Optional.of(namespace))
        whenever(pluginRepository.findByNamespaceAndPluginId(namespace, "my-plugin")).thenReturn(Optional.of(plugin))
        whenever(releaseRepository.findAllByPluginAndStatus(plugin, ReleaseStatus.PUBLISHED))
            .thenReturn(listOf(release("1.0.0")))

        val installed = listOf(InstalledPluginInfo(pluginId = "my-plugin", currentVersion = "1.0.0"))
        val response = updateCheckService.checkUpdates("acme", installed)

        assertThat(response.updates).isEmpty()
    }

    @Test
    fun `checkUpdates skips unknown plugins silently`() {
        whenever(namespaceRepository.findBySlug("acme")).thenReturn(Optional.of(namespace))
        whenever(pluginRepository.findByNamespaceAndPluginId(namespace, "unknown")).thenReturn(Optional.empty())

        val installed = listOf(InstalledPluginInfo(pluginId = "unknown", currentVersion = "1.0.0"))
        val response = updateCheckService.checkUpdates("acme", installed)

        assertThat(response.updates).isEmpty()
    }

    @Test
    fun `checkUpdates skips plugins with no published releases`() {
        whenever(namespaceRepository.findBySlug("acme")).thenReturn(Optional.of(namespace))
        whenever(pluginRepository.findByNamespaceAndPluginId(namespace, "my-plugin")).thenReturn(Optional.of(plugin))
        whenever(releaseRepository.findAllByPluginAndStatus(plugin, ReleaseStatus.PUBLISHED)).thenReturn(emptyList())

        val installed = listOf(InstalledPluginInfo(pluginId = "my-plugin", currentVersion = "1.0.0"))
        val response = updateCheckService.checkUpdates("acme", installed)

        assertThat(response.updates).isEmpty()
    }

    @Test
    fun `checkUpdates throws NamespaceNotFoundException for unknown namespace`() {
        whenever(namespaceRepository.findBySlug("missing")).thenReturn(Optional.empty())

        assertFailsWith<NamespaceNotFoundException> {
            updateCheckService.checkUpdates("missing", emptyList())
        }
    }

    @Test
    fun `checkUpdates handles multiple plugins`() {
        val plugin2 = PluginEntity(namespace = namespace, pluginId = "other-plugin", name = "Other")
        val release2 = PluginReleaseEntity(
            plugin = plugin2,
            version = "3.0.0",
            artifactSha256 = "sha",
            artifactKey = "acme/other-plugin/3.0.0",
            status = ReleaseStatus.PUBLISHED,
        ).also { it.id = UUID.randomUUID() }
        whenever(namespaceRepository.findBySlug("acme")).thenReturn(Optional.of(namespace))
        whenever(pluginRepository.findByNamespaceAndPluginId(namespace, "my-plugin")).thenReturn(Optional.of(plugin))
        whenever(
            pluginRepository.findByNamespaceAndPluginId(namespace, "other-plugin"),
        ).thenReturn(Optional.of(plugin2))
        whenever(releaseRepository.findAllByPluginAndStatus(plugin, ReleaseStatus.PUBLISHED))
            .thenReturn(listOf(release("2.0.0")))
        whenever(releaseRepository.findAllByPluginAndStatus(plugin2, ReleaseStatus.PUBLISHED))
            .thenReturn(listOf(release2))

        val installed = listOf(
            InstalledPluginInfo(pluginId = "my-plugin", currentVersion = "1.0.0"),
            InstalledPluginInfo(pluginId = "other-plugin", currentVersion = "3.0.0"),
        )
        val response = updateCheckService.checkUpdates("acme", installed)

        assertThat(response.updates).hasSize(1)
        assertThat(response.updates.first().pluginId).isEqualTo("my-plugin")
    }
}
