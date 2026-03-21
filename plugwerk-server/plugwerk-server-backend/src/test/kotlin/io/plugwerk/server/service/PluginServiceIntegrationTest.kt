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
import io.plugwerk.server.SharedPostgresContainer
import io.plugwerk.server.repository.PluginRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import kotlin.test.assertFailsWith

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PluginService::class, NamespaceService::class)
@Tag("integration")
class PluginServiceIntegrationTest {

    companion object {
        @DynamicPropertySource
        @JvmStatic
        fun configureProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { SharedPostgresContainer.instance.jdbcUrl }
            registry.add("spring.datasource.username") { SharedPostgresContainer.instance.username }
            registry.add("spring.datasource.password") { SharedPostgresContainer.instance.password }
        }
    }

    @Autowired
    lateinit var pluginService: PluginService

    @Autowired
    lateinit var namespaceService: NamespaceService

    @Autowired
    lateinit var pluginRepository: PluginRepository

    @BeforeEach
    fun setUp() {
        namespaceService.create("plugin-int-ns", "Integration Org")
    }

    @Test
    fun `create persists plugin and findByNamespaceAndPluginId retrieves it`() {
        val created = pluginService.create(
            namespaceSlug = "plugin-int-ns",
            pluginId = "my-plugin",
            name = "My Plugin",
            description = "A test plugin",
        )

        val found = pluginService.findByNamespaceAndPluginId("plugin-int-ns", "my-plugin")

        assertThat(found.id).isEqualTo(created.id)
        assertThat(found.description).isEqualTo("A test plugin")
    }

    @Test
    fun `create throws PluginAlreadyExistsException on duplicate pluginId`() {
        pluginService.create("plugin-int-ns", "dup-plugin", "Plugin")

        assertFailsWith<PluginAlreadyExistsException> {
            pluginService.create("plugin-int-ns", "dup-plugin", "Duplicate")
        }
    }

    @Test
    fun `findAllByNamespace with status filter returns only matching plugins`() {
        pluginService.create("plugin-int-ns", "active-plugin", "Active")
        val archived = pluginService.create("plugin-int-ns", "archived-plugin", "Archived")
        pluginService.update("plugin-int-ns", "archived-plugin", status = PluginStatus.ARCHIVED)

        val active = pluginService.findAllByNamespace("plugin-int-ns", PluginStatus.ACTIVE)
        val archivedList = pluginService.findAllByNamespace("plugin-int-ns", PluginStatus.ARCHIVED)

        assertThat(active.map { it.pluginId }).contains("active-plugin").doesNotContain("archived-plugin")
        assertThat(archivedList.map { it.pluginId }).contains("archived-plugin")
    }

    @Test
    fun `update modifies name and description in database`() {
        pluginService.create("plugin-int-ns", "upd-plugin", "Old Name")

        pluginService.update("plugin-int-ns", "upd-plugin", name = "New Name", description = "New Desc")

        val found = pluginService.findByNamespaceAndPluginId("plugin-int-ns", "upd-plugin")
        assertThat(found.name).isEqualTo("New Name")
        assertThat(found.description).isEqualTo("New Desc")
    }

    @Test
    fun `delete removes plugin from database`() {
        pluginService.create("plugin-int-ns", "del-plugin", "To Delete")

        pluginService.delete("plugin-int-ns", "del-plugin")

        assertFailsWith<PluginNotFoundException> {
            pluginService.findByNamespaceAndPluginId("plugin-int-ns", "del-plugin")
        }
    }
}
