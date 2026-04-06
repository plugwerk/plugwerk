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

import io.plugwerk.server.SharedPostgresContainer
import io.plugwerk.server.domain.NamespaceAccessKeyEntity
import io.plugwerk.server.domain.NamespaceMemberEntity
import io.plugwerk.server.domain.NamespaceRole
import io.plugwerk.server.domain.PluginEntity
import io.plugwerk.server.domain.PluginReleaseEntity
import io.plugwerk.server.repository.NamespaceAccessKeyRepository
import io.plugwerk.server.repository.NamespaceMemberRepository
import io.plugwerk.server.repository.NamespaceRepository
import io.plugwerk.server.repository.PluginReleaseRepository
import io.plugwerk.server.repository.PluginRepository
import io.plugwerk.server.service.storage.ArtifactStorageService
import io.plugwerk.spi.model.ReleaseStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import kotlin.test.assertFailsWith

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(
    NamespaceService::class,
    NamespaceServiceIntegrationTest.MockConfig::class,
)
@Tag("integration")
class NamespaceServiceIntegrationTest {

    @Configuration
    class MockConfig {
        @Bean
        fun artifactStorageService(): ArtifactStorageService = mock(ArtifactStorageService::class.java)
    }

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

    @Autowired
    lateinit var pluginRepository: PluginRepository

    @Autowired
    lateinit var pluginReleaseRepository: PluginReleaseRepository

    @Autowired
    lateinit var namespaceMemberRepository: NamespaceMemberRepository

    @Autowired
    lateinit var namespaceAccessKeyRepository: NamespaceAccessKeyRepository

    @Autowired
    lateinit var storageService: ArtifactStorageService

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

    @Test
    fun `delete cascades to plugins, releases, members, access keys, and storage`() {
        val namespace = namespaceService.create("cascade-ns", "Cascade Org")

        val plugin1 = pluginRepository.save(
            PluginEntity(namespace = namespace, pluginId = "plugin-a", name = "Plugin A"),
        )
        val plugin2 = pluginRepository.save(
            PluginEntity(namespace = namespace, pluginId = "plugin-b", name = "Plugin B"),
        )

        val release1 = pluginReleaseRepository.save(
            PluginReleaseEntity(
                plugin = plugin1,
                version = "1.0.0",
                artifactSha256 = "aaa",
                artifactKey = "cascade-ns/plugin-a/1.0.0.jar",
                status = ReleaseStatus.PUBLISHED,
            ),
        )
        val release2 = pluginReleaseRepository.save(
            PluginReleaseEntity(
                plugin = plugin2,
                version = "2.0.0",
                artifactSha256 = "bbb",
                artifactKey = "cascade-ns/plugin-b/2.0.0.jar",
                status = ReleaseStatus.PUBLISHED,
            ),
        )

        namespaceMemberRepository.save(
            NamespaceMemberEntity(
                namespace = namespace,
                userSubject = "alice",
                role = NamespaceRole.ADMIN,
            ),
        )

        namespaceAccessKeyRepository.save(
            NamespaceAccessKeyEntity(
                namespace = namespace,
                keyHash = "\$2a\$10\$cascade-test-bcrypt-hash",
                keyPrefix = "pwk_casc",
                name = "Test key",
            ),
        )

        namespaceService.delete("cascade-ns")

        assertThat(namespaceRepository.existsBySlug("cascade-ns")).isFalse()
        assertThat(pluginRepository.findById(plugin1.id!!)).isEmpty
        assertThat(pluginRepository.findById(plugin2.id!!)).isEmpty
        assertThat(pluginReleaseRepository.findById(release1.id!!)).isEmpty
        assertThat(pluginReleaseRepository.findById(release2.id!!)).isEmpty
        assertThat(namespaceMemberRepository.findAllByNamespaceId(namespace.id!!)).isEmpty()
        assertThat(namespaceAccessKeyRepository.findAllByNamespace(namespace)).isEmpty()

        verify(storageService).delete("cascade-ns/plugin-a/1.0.0.jar")
        verify(storageService).delete("cascade-ns/plugin-b/2.0.0.jar")
    }
}
