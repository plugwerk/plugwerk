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

import io.plugwerk.server.SharedPostgresContainer
import io.plugwerk.server.repository.NamespaceRepository
import org.assertj.core.api.Assertions.assertThat
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
@Import(NamespaceService::class)
@Tag("integration")
class NamespaceServiceIntegrationTest {

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
    lateinit var namespaceService: NamespaceService

    @Autowired
    lateinit var namespaceRepository: NamespaceRepository

    @Test
    fun `create persists namespace and findBySlug retrieves it`() {
        val created = namespaceService.create("int-ns", "Integration Org")

        val found = namespaceService.findBySlug("int-ns")

        assertThat(found.id).isEqualTo(created.id)
        assertThat(found.ownerOrg).isEqualTo("Integration Org")
    }

    @Test
    fun `create throws NamespaceAlreadyExistsException on duplicate slug`() {
        namespaceService.create("dup-ns", "Org A")

        assertFailsWith<NamespaceAlreadyExistsException> {
            namespaceService.create("dup-ns", "Org B")
        }
    }

    @Test
    fun `update changes ownerOrg in database`() {
        namespaceService.create("upd-ns", "Old Org")

        namespaceService.update("upd-ns", ownerOrg = "New Org")

        val found = namespaceService.findBySlug("upd-ns")
        assertThat(found.ownerOrg).isEqualTo("New Org")
    }

    @Test
    fun `delete removes namespace from database`() {
        namespaceService.create("del-ns", "Org")

        namespaceService.delete("del-ns")

        assertThat(namespaceRepository.existsBySlug("del-ns")).isFalse()
    }

    @Test
    fun `findAll returns all persisted namespaces`() {
        val before = namespaceService.findAll().size
        namespaceService.create("findall-ns-1", "Org 1")
        namespaceService.create("findall-ns-2", "Org 2")

        val all = namespaceService.findAll()

        assertThat(all.size).isEqualTo(before + 2)
    }
}
