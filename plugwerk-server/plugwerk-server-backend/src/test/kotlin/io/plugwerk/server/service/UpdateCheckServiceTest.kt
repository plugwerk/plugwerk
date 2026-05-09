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

import io.plugwerk.api.model.InstalledPluginInfo
import io.plugwerk.server.domain.NamespaceEntity
import io.plugwerk.server.domain.PluginEntity
import io.plugwerk.server.domain.PluginReleaseEntity
import io.plugwerk.server.repository.NamespaceRepository
import io.plugwerk.server.repository.PluginReleaseRepository
import io.plugwerk.server.repository.PluginRepository
import io.plugwerk.spi.model.ReleaseStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
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

    private val namespace = NamespaceEntity(slug = "acme", name = "ACME Corp")
    private val plugin = PluginEntity(namespace = namespace, pluginId = "my-plugin", name = "My Plugin")

    private fun release(
        version: String,
        status: ReleaseStatus = ReleaseStatus.PUBLISHED,
        owningPlugin: PluginEntity = plugin,
    ) = PluginReleaseEntity(
        plugin = owningPlugin,
        version = version,
        artifactSha256 = "sha-$version",
        artifactKey = "${owningPlugin.namespace.slug}/${owningPlugin.pluginId}/$version",
        status = status,
    ).also { it.id = UUID.randomUUID() }

    @Test
    fun `checkUpdates returns update when newer version exists`() {
        whenever(namespaceRepository.findBySlug("acme")).thenReturn(Optional.of(namespace))
        whenever(pluginRepository.findAllByNamespaceAndPluginIdIn(namespace, listOf("my-plugin")))
            .thenReturn(listOf(plugin))
        whenever(releaseRepository.findAllByPluginInAndStatus(any(), eq(ReleaseStatus.PUBLISHED)))
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
        whenever(pluginRepository.findAllByNamespaceAndPluginIdIn(namespace, listOf("my-plugin")))
            .thenReturn(listOf(plugin))
        whenever(releaseRepository.findAllByPluginInAndStatus(any(), eq(ReleaseStatus.PUBLISHED)))
            .thenReturn(listOf(release("1.0.0")))

        val installed = listOf(InstalledPluginInfo(pluginId = "my-plugin", currentVersion = "1.0.0"))
        val response = updateCheckService.checkUpdates("acme", installed)

        assertThat(response.updates).isEmpty()
    }

    @Test
    fun `checkUpdates skips unknown plugins silently`() {
        whenever(namespaceRepository.findBySlug("acme")).thenReturn(Optional.of(namespace))
        whenever(pluginRepository.findAllByNamespaceAndPluginIdIn(namespace, listOf("unknown")))
            .thenReturn(emptyList())

        val installed = listOf(InstalledPluginInfo(pluginId = "unknown", currentVersion = "1.0.0"))
        val response = updateCheckService.checkUpdates("acme", installed)

        assertThat(response.updates).isEmpty()
        // No batch release lookup should fire when no plugins matched.
        verify(releaseRepository, never()).findAllByPluginInAndStatus(any(), any())
    }

    @Test
    fun `checkUpdates skips plugins with no published releases`() {
        whenever(namespaceRepository.findBySlug("acme")).thenReturn(Optional.of(namespace))
        whenever(pluginRepository.findAllByNamespaceAndPluginIdIn(namespace, listOf("my-plugin")))
            .thenReturn(listOf(plugin))
        whenever(releaseRepository.findAllByPluginInAndStatus(any(), eq(ReleaseStatus.PUBLISHED)))
            .thenReturn(emptyList())

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
        whenever(namespaceRepository.findBySlug("acme")).thenReturn(Optional.of(namespace))
        whenever(
            pluginRepository.findAllByNamespaceAndPluginIdIn(namespace, listOf("my-plugin", "other-plugin")),
        ).thenReturn(listOf(plugin, plugin2))
        whenever(releaseRepository.findAllByPluginInAndStatus(any(), eq(ReleaseStatus.PUBLISHED)))
            .thenReturn(
                listOf(
                    release("2.0.0", owningPlugin = plugin),
                    release("3.0.0", owningPlugin = plugin2),
                ),
            )

        val installed = listOf(
            InstalledPluginInfo(pluginId = "my-plugin", currentVersion = "1.0.0"),
            InstalledPluginInfo(pluginId = "other-plugin", currentVersion = "3.0.0"),
        )
        val response = updateCheckService.checkUpdates("acme", installed)

        assertThat(response.updates).hasSize(1)
        assertThat(response.updates.first().pluginId).isEqualTo("my-plugin")
    }

    @Test
    fun `checkUpdates issues only two batch repository calls regardless of input size (issue #480)`() {
        // Build 25 plugins + 25 published releases (one each, 1.0.0).
        val plugins = (1..25).map {
            PluginEntity(namespace = namespace, pluginId = "plugin-$it", name = "Plugin $it")
        }
        val pluginIds = plugins.map { it.pluginId }
        val releases = plugins.map { release("2.0.0", owningPlugin = it) }

        whenever(namespaceRepository.findBySlug("acme")).thenReturn(Optional.of(namespace))
        whenever(pluginRepository.findAllByNamespaceAndPluginIdIn(namespace, pluginIds))
            .thenReturn(plugins)
        whenever(releaseRepository.findAllByPluginInAndStatus(any(), eq(ReleaseStatus.PUBLISHED)))
            .thenReturn(releases)

        val installed = pluginIds.map { InstalledPluginInfo(pluginId = it, currentVersion = "1.0.0") }
        val response = updateCheckService.checkUpdates("acme", installed)

        // All 25 plugins should report an update from 1.0.0 to 2.0.0.
        assertThat(response.updates).hasSize(25)
        // Statement count is bounded: exactly one plugin batch + one release batch,
        // independent of installed.size. Regression-guards the N+1 fix.
        verify(pluginRepository, times(1)).findAllByNamespaceAndPluginIdIn(any(), any())
        verify(releaseRepository, times(1)).findAllByPluginInAndStatus(any(), any())
        // The legacy per-plugin entry points must NOT be reached.
        verify(pluginRepository, never()).findByNamespaceAndPluginId(any(), any())
        verify(releaseRepository, never()).findAllByPluginAndStatus(any(), any())
    }
}
