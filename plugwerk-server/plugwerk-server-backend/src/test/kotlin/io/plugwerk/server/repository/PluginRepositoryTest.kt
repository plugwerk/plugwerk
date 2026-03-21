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
package io.plugwerk.server.repository

import io.plugwerk.common.model.PluginStatus
import io.plugwerk.server.AbstractRepositoryTest
import io.plugwerk.server.domain.NamespaceEntity
import io.plugwerk.server.domain.PluginEntity
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DataIntegrityViolationException
import kotlin.test.assertFailsWith

open class PluginRepositoryTest : AbstractRepositoryTest() {

    @Autowired
    lateinit var namespaceRepository: NamespaceRepository

    @Autowired
    lateinit var pluginRepository: PluginRepository

    lateinit var namespace: NamespaceEntity

    @BeforeEach
    fun setup() {
        namespace = namespaceRepository.save(NamespaceEntity(slug = "test-ns", ownerOrg = "Test Org"))
    }

    @Test
    fun `findByNamespaceAndPluginId returns plugin when it exists`() {
        val plugin =
            pluginRepository.save(
                PluginEntity(namespace = namespace, pluginId = "my-plugin", name = "My Plugin"),
            )

        val found = pluginRepository.findByNamespaceAndPluginId(namespace, "my-plugin")

        assertThat(found).isPresent
        assertThat(found.get().id).isEqualTo(plugin.id!!)
    }

    @Test
    fun `findByNamespaceAndPluginId returns empty when plugin does not exist`() {
        val found = pluginRepository.findByNamespaceAndPluginId(namespace, "missing-plugin")

        assertThat(found).isEmpty
    }

    @Test
    fun `findAllByNamespace returns only plugins of that namespace`() {
        val otherNamespace = namespaceRepository.save(NamespaceEntity(slug = "other-ns", ownerOrg = "Other Org"))
        pluginRepository.save(PluginEntity(namespace = namespace, pluginId = "plugin-a", name = "Plugin A"))
        pluginRepository.save(PluginEntity(namespace = namespace, pluginId = "plugin-b", name = "Plugin B"))
        pluginRepository.save(PluginEntity(namespace = otherNamespace, pluginId = "plugin-c", name = "Plugin C"))

        val plugins = pluginRepository.findAllByNamespace(namespace)

        assertThat(plugins).hasSize(2)
        assertThat(plugins.map { it.pluginId }).containsExactlyInAnyOrder("plugin-a", "plugin-b")
    }

    @Test
    fun `findAllByNamespaceAndStatus filters by status`() {
        pluginRepository.save(
            PluginEntity(namespace = namespace, pluginId = "active-1", name = "Active 1", status = PluginStatus.ACTIVE),
        )
        pluginRepository.save(
            PluginEntity(namespace = namespace, pluginId = "active-2", name = "Active 2", status = PluginStatus.ACTIVE),
        )
        pluginRepository.save(
            PluginEntity(
                namespace = namespace,
                pluginId = "archived-1",
                name = "Archived 1",
                status = PluginStatus.ARCHIVED,
            ),
        )

        val activePlugins = pluginRepository.findAllByNamespaceAndStatus(namespace, PluginStatus.ACTIVE)
        val archivedPlugins = pluginRepository.findAllByNamespaceAndStatus(namespace, PluginStatus.ARCHIVED)

        assertThat(activePlugins).hasSize(2)
        assertThat(archivedPlugins).hasSize(1)
    }

    @Test
    fun `save persists categories and tags as arrays`() {
        val plugin =
            pluginRepository.save(
                PluginEntity(
                    namespace = namespace,
                    pluginId = "categorized",
                    name = "Categorized Plugin",
                    categories = arrayOf("ui", "reporting"),
                    tags = arrayOf("beta", "v2"),
                ),
            )

        val found = pluginRepository.findById(plugin.id!!).orElseThrow()

        assertThat(found.categories).containsExactlyInAnyOrder("ui", "reporting")
        assertThat(found.tags).containsExactlyInAnyOrder("beta", "v2")
    }

    @Test
    fun `save fails on duplicate pluginId within namespace`() {
        pluginRepository.save(PluginEntity(namespace = namespace, pluginId = "duplicate-plugin", name = "Original"))
        pluginRepository.flush()

        assertFailsWith<DataIntegrityViolationException> {
            pluginRepository.saveAndFlush(
                PluginEntity(namespace = namespace, pluginId = "duplicate-plugin", name = "Duplicate"),
            )
        }
    }

    @Test
    fun `same pluginId is allowed in different namespaces`() {
        val otherNamespace = namespaceRepository.save(NamespaceEntity(slug = "ns-two", ownerOrg = "Org Two"))

        pluginRepository.save(PluginEntity(namespace = namespace, pluginId = "shared-id", name = "Plugin in NS1"))
        pluginRepository.saveAndFlush(
            PluginEntity(namespace = otherNamespace, pluginId = "shared-id", name = "Plugin in NS2"),
        )

        assertThat(pluginRepository.existsByNamespaceAndPluginId(namespace, "shared-id")).isTrue()
        assertThat(pluginRepository.existsByNamespaceAndPluginId(otherNamespace, "shared-id")).isTrue()
    }
}
