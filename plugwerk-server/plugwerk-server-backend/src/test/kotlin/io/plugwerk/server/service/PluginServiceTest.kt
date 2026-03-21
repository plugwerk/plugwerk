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

import io.plugwerk.common.model.PluginStatus
import io.plugwerk.server.domain.NamespaceEntity
import io.plugwerk.server.domain.PluginEntity
import io.plugwerk.server.repository.PluginRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.Optional
import kotlin.test.assertFailsWith

@ExtendWith(MockitoExtension::class)
class PluginServiceTest {

    @Mock
    lateinit var pluginRepository: PluginRepository

    @Mock
    lateinit var namespaceService: NamespaceService

    @InjectMocks
    lateinit var pluginService: PluginService

    private val namespace = NamespaceEntity(slug = "acme", ownerOrg = "ACME Corp")

    @Test
    fun `findByNamespaceAndPluginId returns plugin when it exists`() {
        val plugin = PluginEntity(namespace = namespace, pluginId = "my-plugin", name = "My Plugin")
        whenever(namespaceService.findBySlug("acme")).thenReturn(namespace)
        whenever(pluginRepository.findByNamespaceAndPluginId(namespace, "my-plugin")).thenReturn(Optional.of(plugin))

        val result = pluginService.findByNamespaceAndPluginId("acme", "my-plugin")

        assertThat(result.pluginId).isEqualTo("my-plugin")
    }

    @Test
    fun `findByNamespaceAndPluginId throws PluginNotFoundException when missing`() {
        whenever(namespaceService.findBySlug("acme")).thenReturn(namespace)
        whenever(pluginRepository.findByNamespaceAndPluginId(namespace, "missing")).thenReturn(Optional.empty())

        assertFailsWith<PluginNotFoundException> {
            pluginService.findByNamespaceAndPluginId("acme", "missing")
        }
    }

    @Test
    fun `findAllByNamespace returns all plugins when no status filter`() {
        val plugins = listOf(
            PluginEntity(namespace = namespace, pluginId = "p1", name = "Plugin 1"),
            PluginEntity(namespace = namespace, pluginId = "p2", name = "Plugin 2"),
        )
        whenever(namespaceService.findBySlug("acme")).thenReturn(namespace)
        whenever(pluginRepository.findAllByNamespace(namespace)).thenReturn(plugins)

        val result = pluginService.findAllByNamespace("acme")

        assertThat(result).hasSize(2)
    }

    @Test
    fun `findAllByNamespace filters by status when provided`() {
        val active = listOf(PluginEntity(namespace = namespace, pluginId = "p1", name = "Plugin 1"))
        whenever(namespaceService.findBySlug("acme")).thenReturn(namespace)
        whenever(pluginRepository.findAllByNamespaceAndStatus(namespace, PluginStatus.ACTIVE)).thenReturn(active)

        val result = pluginService.findAllByNamespace("acme", PluginStatus.ACTIVE)

        assertThat(result).hasSize(1)
        verify(pluginRepository, never()).findAllByNamespace(any<io.plugwerk.server.domain.NamespaceEntity>())
    }

    @Test
    fun `create saves and returns new plugin`() {
        whenever(namespaceService.findBySlug("acme")).thenReturn(namespace)
        whenever(pluginRepository.existsByNamespaceAndPluginId(namespace, "new-plugin")).thenReturn(false)
        val saved = PluginEntity(namespace = namespace, pluginId = "new-plugin", name = "New Plugin")
        whenever(pluginRepository.save(any<PluginEntity>())).thenReturn(saved)

        val result = pluginService.create("acme", "new-plugin", "New Plugin")

        assertThat(result.pluginId).isEqualTo("new-plugin")
        verify(pluginRepository).save(any<PluginEntity>())
    }

    @Test
    fun `create throws PluginAlreadyExistsException when pluginId is taken`() {
        whenever(namespaceService.findBySlug("acme")).thenReturn(namespace)
        whenever(pluginRepository.existsByNamespaceAndPluginId(namespace, "existing")).thenReturn(true)

        assertFailsWith<PluginAlreadyExistsException> {
            pluginService.create("acme", "existing", "Existing Plugin")
        }

        verify(pluginRepository, never()).save(any<PluginEntity>())
    }

    @Test
    fun `update modifies only provided fields`() {
        val plugin = PluginEntity(namespace = namespace, pluginId = "p1", name = "Old Name")
        whenever(namespaceService.findBySlug("acme")).thenReturn(namespace)
        whenever(pluginRepository.findByNamespaceAndPluginId(namespace, "p1")).thenReturn(Optional.of(plugin))
        whenever(pluginRepository.save(any<PluginEntity>())).thenReturn(plugin)

        pluginService.update("acme", "p1", name = "New Name", description = "Desc")

        assertThat(plugin.name).isEqualTo("New Name")
        assertThat(plugin.description).isEqualTo("Desc")
    }

    @Test
    fun `delete removes plugin`() {
        val plugin = PluginEntity(namespace = namespace, pluginId = "p1", name = "Plugin 1")
        whenever(namespaceService.findBySlug("acme")).thenReturn(namespace)
        whenever(pluginRepository.findByNamespaceAndPluginId(namespace, "p1")).thenReturn(Optional.of(plugin))

        pluginService.delete("acme", "p1")

        verify(pluginRepository).delete(plugin)
    }
}
