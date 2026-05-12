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
import io.plugwerk.server.repository.PluginRepository
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
    private lateinit var pluginRepository: PluginRepository
    private lateinit var storage: ArtifactStorageService
    private lateinit var service: StorageConsistencyAdminService

    @BeforeEach
    fun setUp() {
        releaseRepository = mock()
        pluginRepository = mock()
        storage = mock()
        service =
            StorageConsistencyAdminService(releaseRepository, pluginRepository, storage)
    }

    @Test
    fun `deleteOrphanedRelease deletes DB row and storage file`() {
        val release = release(key = "ns:p:1.0.0:jar")
        whenever(releaseRepository.findById(release.id!!)).thenReturn(Optional.of(release))
        // Pretend another release of the same plugin is still around so the
        // plugin-GC branch does not interfere with this happy-path test.
        whenever(releaseRepository.existsByPlugin(release.plugin)).thenReturn(true)

        service.deleteOrphanedRelease(release.id!!)

        verify(releaseRepository).delete(release)
        verify(storage).delete("ns:p:1.0.0:jar")
    }

    @Test
    fun `deleteOrphanedReleases skips when storage file has reappeared`() {
        val present = release(key = "ns:present:1.0.0:jar")
        val gone = release(key = "ns:gone:1.0.0:jar")
        whenever(releaseRepository.findById(present.id!!)).thenReturn(Optional.of(present))
        whenever(releaseRepository.findById(gone.id!!)).thenReturn(Optional.of(gone))
        whenever(storage.exists("ns:present:1.0.0:jar")).thenReturn(true)
        whenever(storage.exists("ns:gone:1.0.0:jar")).thenReturn(false)

        val result = service.deleteOrphanedReleases(listOf(present.id!!, gone.id!!))

        verify(releaseRepository).delete(gone)
        verify(releaseRepository, never()).delete(present)
        assertThat(result.deleted).containsExactly(gone.id!!)
        assertThat(result.skipped).containsExactly(present.id!!)
    }

    @Test
    fun `deleteOrphanedReleases reports already-gone IDs as skipped`() {
        val ghostId = UUID.randomUUID()
        whenever(releaseRepository.findById(ghostId)).thenReturn(Optional.empty())

        val result = service.deleteOrphanedReleases(listOf(ghostId))

        verify(releaseRepository, never()).delete(any<PluginReleaseEntity>())
        assertThat(result.deleted).isEmpty()
        assertThat(result.skipped).containsExactly(ghostId)
    }

    @Test
    fun `deleteOrphanedReleases with empty input is a no-op`() {
        val result = service.deleteOrphanedReleases(emptyList())

        assertThat(result.deleted).isEmpty()
        assertThat(result.skipped).isEmpty()
        verify(releaseRepository, never()).delete(any<PluginReleaseEntity>())
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
    fun `deleteOrphanedRelease also deletes empty plugin shell after last release`() {
        val release = release(key = "ns:p:1.0.0:jar")
        whenever(releaseRepository.findById(release.id!!)).thenReturn(Optional.of(release))
        whenever(releaseRepository.existsByPlugin(release.plugin)).thenReturn(false)

        service.deleteOrphanedRelease(release.id!!)

        verify(releaseRepository).delete(release)
        verify(pluginRepository).delete(release.plugin)
    }

    @Test
    fun `deleteOrphanedRelease leaves plugin alone when other releases remain`() {
        val release = release(key = "ns:p:1.0.0:jar")
        whenever(releaseRepository.findById(release.id!!)).thenReturn(Optional.of(release))
        whenever(releaseRepository.existsByPlugin(release.plugin)).thenReturn(true)

        service.deleteOrphanedRelease(release.id!!)

        verify(releaseRepository).delete(release)
        verify(pluginRepository, never()).delete(any<PluginEntity>())
    }

    @Test
    fun `deleteOrphanedReleases bulk removes plugin only after its last release is gone`() {
        val release1 = release(key = "ns:p:1.0.0:jar", version = "1.0.0")
        val release2 = release(key = "ns:p:2.0.0:jar", version = "2.0.0")
        whenever(releaseRepository.findById(release1.id!!)).thenReturn(Optional.of(release1))
        whenever(releaseRepository.findById(release2.id!!)).thenReturn(Optional.of(release2))
        whenever(storage.exists(any())).thenReturn(false)
        // First flush: release1 deleted, release2 still alive → plugin stays.
        // Second flush: both deleted → plugin gets garbage-collected.
        whenever(releaseRepository.existsByPlugin(release1.plugin))
            .thenReturn(true)
            .thenReturn(false)

        service.deleteOrphanedReleases(listOf(release1.id!!, release2.id!!))

        verify(releaseRepository).delete(release1)
        verify(releaseRepository).delete(release2)
        verify(pluginRepository).delete(release1.plugin)
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

    // Plugin shared across `release()` calls within a single test so the
    // GC-shell assertions can target the same `PluginEntity` instance for
    // multiple releases of one plugin.
    private val sharedPlugin: PluginEntity = PluginEntity(
        namespace = NamespaceEntity(slug = "ns", name = "Namespace"),
        pluginId = "p",
        name = "P",
    )

    private fun release(key: String, version: String = "1.0.0"): PluginReleaseEntity = PluginReleaseEntity(
        id = UUID.randomUUID(),
        plugin = sharedPlugin,
        version = version,
        artifactSha256 = "sha",
        artifactKey = key,
    )
}
