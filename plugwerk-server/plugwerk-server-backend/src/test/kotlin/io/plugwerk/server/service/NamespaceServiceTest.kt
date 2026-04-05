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

import io.plugwerk.server.domain.NamespaceEntity
import io.plugwerk.server.domain.PluginEntity
import io.plugwerk.server.domain.PluginReleaseEntity
import io.plugwerk.server.repository.NamespaceRepository
import io.plugwerk.server.repository.PluginReleaseRepository
import io.plugwerk.server.repository.PluginRepository
import io.plugwerk.server.service.storage.ArtifactStorageService
import io.plugwerk.spi.model.ReleaseStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.Optional
import kotlin.test.assertFailsWith

@ExtendWith(MockitoExtension::class)
class NamespaceServiceTest {

    @Mock
    lateinit var namespaceRepository: NamespaceRepository

    @Mock
    lateinit var pluginRepository: PluginRepository

    @Mock
    lateinit var pluginReleaseRepository: PluginReleaseRepository

    @Mock
    lateinit var storageService: ArtifactStorageService

    @InjectMocks
    lateinit var namespaceService: NamespaceService

    @Test
    fun `findBySlug returns namespace when it exists`() {
        val entity = NamespaceEntity(slug = "acme", ownerOrg = "ACME Corp")
        whenever(namespaceRepository.findBySlug("acme")).thenReturn(Optional.of(entity))

        val result = namespaceService.findBySlug("acme")

        assertThat(result.slug).isEqualTo("acme")
        assertThat(result.ownerOrg).isEqualTo("ACME Corp")
    }

    @Test
    fun `findBySlug throws NamespaceNotFoundException when not found`() {
        whenever(namespaceRepository.findBySlug("missing")).thenReturn(Optional.empty())

        assertFailsWith<NamespaceNotFoundException> {
            namespaceService.findBySlug("missing")
        }
    }

    @Test
    fun `create saves and returns new namespace`() {
        whenever(namespaceRepository.existsBySlug("new-ns")).thenReturn(false)
        val saved = NamespaceEntity(slug = "new-ns", ownerOrg = "Org")
        whenever(namespaceRepository.save(any<NamespaceEntity>())).thenReturn(saved)

        val result = namespaceService.create("new-ns", "Org")

        assertThat(result.slug).isEqualTo("new-ns")
        verify(namespaceRepository).save(any<NamespaceEntity>())
    }

    @Test
    fun `create throws NamespaceAlreadyExistsException when slug is taken`() {
        whenever(namespaceRepository.existsBySlug("existing")).thenReturn(true)

        assertFailsWith<NamespaceAlreadyExistsException> {
            namespaceService.create("existing", "Org")
        }

        verify(namespaceRepository, never()).save(any<NamespaceEntity>())
    }

    @Test
    fun `update changes ownerOrg and settings`() {
        val entity = NamespaceEntity(slug = "acme", ownerOrg = "Old Org")
        whenever(namespaceRepository.findBySlug("acme")).thenReturn(Optional.of(entity))
        whenever(namespaceRepository.save(any<NamespaceEntity>())).thenReturn(entity)

        namespaceService.update("acme", ownerOrg = "New Org", settings = """{"key":"val"}""")

        assertThat(entity.ownerOrg).isEqualTo("New Org")
        assertThat(entity.settings).isEqualTo("""{"key":"val"}""")
    }

    @Test
    fun `delete removes storage artifacts and namespace`() {
        val namespace = NamespaceEntity(slug = "to-delete", ownerOrg = "Org")
        whenever(namespaceRepository.findBySlug("to-delete")).thenReturn(Optional.of(namespace))

        val plugin1 = PluginEntity(namespace = namespace, pluginId = "p1", name = "P1")
        val plugin2 = PluginEntity(namespace = namespace, pluginId = "p2", name = "P2")
        whenever(pluginRepository.findAllByNamespace(namespace)).thenReturn(listOf(plugin1, plugin2))

        val release1 = PluginReleaseEntity(
            plugin = plugin1,
            version = "1.0.0",
            artifactSha256 = "sha1",
            artifactKey = "to-delete/p1/1.0.0.jar",
            status = ReleaseStatus.PUBLISHED,
        )
        val release2 = PluginReleaseEntity(
            plugin = plugin2,
            version = "2.0.0",
            artifactSha256 = "sha2",
            artifactKey = "to-delete/p2/2.0.0.jar",
            status = ReleaseStatus.PUBLISHED,
        )
        whenever(pluginReleaseRepository.findAllByPluginOrderByCreatedAtDesc(plugin1))
            .thenReturn(listOf(release1))
        whenever(pluginReleaseRepository.findAllByPluginOrderByCreatedAtDesc(plugin2))
            .thenReturn(listOf(release2))

        namespaceService.delete("to-delete")

        verify(storageService).delete("to-delete/p1/1.0.0.jar")
        verify(storageService).delete("to-delete/p2/2.0.0.jar")
        verify(namespaceRepository).delete(namespace)
    }

    @Test
    fun `delete continues when storage deletion fails for individual artifacts`() {
        val namespace = NamespaceEntity(slug = "ns", ownerOrg = "Org")
        whenever(namespaceRepository.findBySlug("ns")).thenReturn(Optional.of(namespace))

        val plugin = PluginEntity(namespace = namespace, pluginId = "p1", name = "P1")
        whenever(pluginRepository.findAllByNamespace(namespace)).thenReturn(listOf(plugin))

        val release1 = PluginReleaseEntity(
            plugin = plugin,
            version = "1.0.0",
            artifactSha256 = "sha1",
            artifactKey = "ns/p1/1.0.0.jar",
            status = ReleaseStatus.PUBLISHED,
        )
        val release2 = PluginReleaseEntity(
            plugin = plugin,
            version = "2.0.0",
            artifactSha256 = "sha2",
            artifactKey = "ns/p1/2.0.0.jar",
            status = ReleaseStatus.PUBLISHED,
        )
        whenever(pluginReleaseRepository.findAllByPluginOrderByCreatedAtDesc(plugin))
            .thenReturn(listOf(release1, release2))
        doThrow(RuntimeException("storage error")).whenever(storageService).delete(eq("ns/p1/1.0.0.jar"))

        namespaceService.delete("ns")

        verify(storageService).delete("ns/p1/1.0.0.jar")
        verify(storageService).delete("ns/p1/2.0.0.jar")
        verify(namespaceRepository).delete(namespace)
    }

    @Test
    fun `delete works for namespace with no plugins`() {
        val namespace = NamespaceEntity(slug = "empty", ownerOrg = "Org")
        whenever(namespaceRepository.findBySlug("empty")).thenReturn(Optional.of(namespace))
        whenever(pluginRepository.findAllByNamespace(namespace)).thenReturn(emptyList())

        namespaceService.delete("empty")

        verify(namespaceRepository).delete(namespace)
    }

    @Test
    fun `delete throws NamespaceNotFoundException when namespace missing`() {
        whenever(namespaceRepository.findBySlug("missing")).thenReturn(Optional.empty())

        assertFailsWith<NamespaceNotFoundException> {
            namespaceService.delete("missing")
        }
    }
}
