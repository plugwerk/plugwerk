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
package io.plugwerk.server.service.storage.consistency

import io.plugwerk.server.domain.NamespaceEntity
import io.plugwerk.server.domain.PluginEntity
import io.plugwerk.server.domain.PluginReleaseEntity
import io.plugwerk.server.repository.PluginReleaseRepository
import io.plugwerk.server.service.storage.ArtifactStorageService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.Optional
import java.util.UUID

class StorageConsistencyAdminServiceTest {

    private lateinit var releaseRepository: PluginReleaseRepository
    private lateinit var storage: ArtifactStorageService
    private lateinit var service: StorageConsistencyAdminService

    @BeforeEach
    fun setUp() {
        releaseRepository = mock()
        storage = mock()
        service = StorageConsistencyAdminService(releaseRepository, storage)
    }

    @Test
    fun `deleteOrphanedRelease deletes DB row and storage file`() {
        val release = release(key = "ns:p:1.0.0:jar")
        whenever(releaseRepository.findById(release.id!!)).thenReturn(Optional.of(release))

        service.deleteOrphanedRelease(release.id!!)

        verify(releaseRepository).delete(release)
        verify(storage).delete("ns:p:1.0.0:jar")
    }

    @Test
    fun `deleteOrphanedRelease is idempotent when release already gone`() {
        val id = UUID.randomUUID()
        whenever(releaseRepository.findById(id)).thenReturn(Optional.empty())

        service.deleteOrphanedRelease(id)

        verify(releaseRepository, never()).delete(any<PluginReleaseEntity>())
        verify(storage, never()).delete(any())
    }

    @Test
    fun `deleteOrphanedArtifacts removes only keys with no DB reference`() {
        whenever(releaseRepository.findAllArtifactKeys()).thenReturn(listOf("ns:p:1.0.0:jar"))

        val result = service.deleteOrphanedArtifacts(
            listOf("ns:p:1.0.0:jar", "ns:orphan:0.1.0:jar"),
        )

        verify(storage, never()).delete("ns:p:1.0.0:jar")
        verify(storage).delete("ns:orphan:0.1.0:jar")
        assertThat(result.deleted).containsExactly("ns:orphan:0.1.0:jar")
        assertThat(result.skipped).containsExactly("ns:p:1.0.0:jar")
    }

    @Test
    fun `deleteOrphanedArtifacts with empty input is a no-op`() {
        val result = service.deleteOrphanedArtifacts(emptyList())

        assertThat(result.deleted).isEmpty()
        assertThat(result.skipped).isEmpty()
        verify(storage, never()).delete(any())
    }

    @Test
    fun `deleteOrphanedArtifacts captures storage-failures as skipped, continues with next`() {
        whenever(releaseRepository.findAllArtifactKeys()).thenReturn(emptyList())
        whenever(storage.delete("flaky"))
            .thenThrow(io.plugwerk.server.service.ArtifactStorageException("backend hiccup"))

        val result = service.deleteOrphanedArtifacts(listOf("flaky", "ok"))

        assertThat(result.deleted).containsExactly("ok")
        assertThat(result.skipped).containsExactly("flaky")
    }

    private fun release(key: String): PluginReleaseEntity {
        val ns = NamespaceEntity(slug = "ns", name = "Namespace")
        val plugin = PluginEntity(namespace = ns, pluginId = "p", name = "P")
        return PluginReleaseEntity(
            id = UUID.randomUUID(),
            plugin = plugin,
            version = "1.0.0",
            artifactSha256 = "sha",
            artifactKey = key,
        )
    }
}
