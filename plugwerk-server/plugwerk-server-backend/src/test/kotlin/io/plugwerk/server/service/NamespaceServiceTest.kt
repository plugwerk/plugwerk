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
import io.plugwerk.server.service.settings.UserSettingsService
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

    @Mock
    lateinit var userSettingsService: UserSettingsService

    @Mock
    lateinit var namespaceDeletionTransaction: NamespaceDeletionTransaction

    @InjectMocks
    lateinit var namespaceService: NamespaceService

    @Test
    fun `findBySlug returns namespace when it exists`() {
        val entity = NamespaceEntity(slug = "acme", name = "ACME Corp")
        whenever(namespaceRepository.findBySlug("acme")).thenReturn(Optional.of(entity))

        val result = namespaceService.findBySlug("acme")

        assertThat(result.slug).isEqualTo("acme")
        assertThat(result.name).isEqualTo("ACME Corp")
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
        val saved = NamespaceEntity(slug = "new-ns", name = "Org")
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
    fun `update changes name and autoApproveReleases`() {
        val entity = NamespaceEntity(slug = "acme", name = "Old Org")
        whenever(namespaceRepository.findBySlug("acme")).thenReturn(Optional.of(entity))
        whenever(namespaceRepository.save(any<NamespaceEntity>())).thenReturn(entity)

        namespaceService.update("acme", name = "New Org", autoApproveReleases = true)

        assertThat(entity.name).isEqualTo("New Org")
        assertThat(entity.autoApproveReleases).isTrue()
    }

    @Test
    fun `delete delegates to deletion-transaction and runs storage cleanup for returned keys (#481)`() {
        // Phase-1 (DB) lives in NamespaceDeletionTransaction; the service is
        // responsible for phase-2 (best-effort storage cleanup outside any TX).
        whenever(namespaceDeletionTransaction.deleteFromDb("to-delete"))
            .thenReturn(listOf("to-delete/p1/1.0.0.jar", "to-delete/p2/2.0.0.jar"))

        namespaceService.delete("to-delete")

        verify(namespaceDeletionTransaction).deleteFromDb("to-delete")
        verify(storageService).delete("to-delete/p1/1.0.0.jar")
        verify(storageService).delete("to-delete/p2/2.0.0.jar")
    }

    @Test
    fun `delete continues when storage deletion fails for individual artifacts (#481)`() {
        whenever(namespaceDeletionTransaction.deleteFromDb("ns"))
            .thenReturn(listOf("ns/p1/1.0.0.jar", "ns/p1/2.0.0.jar"))
        doThrow(RuntimeException("storage error")).whenever(storageService).delete(eq("ns/p1/1.0.0.jar"))

        // Must not rethrow.
        namespaceService.delete("ns")

        // Both keys attempted — failure on the first did not short-circuit.
        verify(storageService).delete("ns/p1/1.0.0.jar")
        verify(storageService).delete("ns/p1/2.0.0.jar")
    }

    @Test
    fun `delete is a no-op for storage when deletion-transaction returns no keys (#481)`() {
        whenever(namespaceDeletionTransaction.deleteFromDb("empty")).thenReturn(emptyList())

        namespaceService.delete("empty")

        verify(namespaceDeletionTransaction).deleteFromDb("empty")
        verify(storageService, org.mockito.kotlin.never()).delete(org.mockito.kotlin.any())
    }

    @Test
    fun `delete propagates NamespaceNotFoundException from deletion-transaction (#481)`() {
        whenever(namespaceDeletionTransaction.deleteFromDb("missing"))
            .thenThrow(NamespaceNotFoundException("missing"))

        assertFailsWith<NamespaceNotFoundException> {
            namespaceService.delete("missing")
        }
    }
}
