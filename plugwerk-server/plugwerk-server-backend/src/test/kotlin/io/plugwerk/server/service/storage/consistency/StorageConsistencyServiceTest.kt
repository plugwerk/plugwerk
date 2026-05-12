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

import io.plugwerk.server.PlugwerkProperties
import io.plugwerk.server.domain.NamespaceEntity
import io.plugwerk.server.domain.PluginEntity
import io.plugwerk.server.domain.PluginReleaseEntity
import io.plugwerk.server.repository.PluginReleaseRepository
import io.plugwerk.server.service.storage.ArtifactStorageService
import io.plugwerk.server.service.storage.StorageObjectInfo
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class StorageConsistencyServiceTest {

    private val now = Instant.parse("2026-05-12T12:00:00Z")
    private val clock: Clock = Clock.fixed(now, ZoneOffset.UTC)

    @Test
    fun `empty DB and empty storage yield an empty report`() {
        val service = buildService(dbKeys = emptyList(), storageObjects = emptyList())

        val report = service.scan()

        assertThat(report.missingArtifacts).isEmpty()
        assertThat(report.orphanedArtifacts).isEmpty()
        assertThat(report.totalDbRows).isZero()
        assertThat(report.totalStorageObjects).isZero()
        assertThat(report.scannedAt).isEqualTo(now)
    }

    @Test
    fun `perfect match yields no inconsistencies`() {
        val key = "ns:plug:1.0.0:jar"
        val release = release(key = key, pluginId = "plug", version = "1.0.0")
        val service = buildService(
            dbKeys = listOf(key),
            storageObjects = listOf(storageObject(key, ageHours = 5)),
            releasesForHydration = listOf(release),
        )

        val report = service.scan()

        assertThat(report.missingArtifacts).isEmpty()
        assertThat(report.orphanedArtifacts).isEmpty()
        assertThat(report.totalDbRows).isEqualTo(1)
        assertThat(report.totalStorageObjects).isEqualTo(1)
    }

    @Test
    fun `DB row without storage file is reported as missing artifact`() {
        val release = release(key = "ns:gone:1.0.0:jar", pluginId = "gone", version = "1.0.0")
        val service = buildService(
            dbKeys = listOf(release.artifactKey),
            storageObjects = emptyList(),
            releasesForHydration = listOf(release),
        )

        val report = service.scan()

        assertThat(report.missingArtifacts).singleElement().satisfies({
            assertThat(it.releaseId).isEqualTo(release.id)
            assertThat(it.pluginId).isEqualTo("gone")
            assertThat(it.version).isEqualTo("1.0.0")
            assertThat(it.artifactKey).isEqualTo("ns:gone:1.0.0:jar")
        })
        assertThat(report.orphanedArtifacts).isEmpty()
    }

    @Test
    fun `storage file without DB row is reported as orphaned artifact with ageHours`() {
        val service = buildService(
            dbKeys = emptyList(),
            storageObjects = listOf(storageObject("ns:orphan:0.1.0:jar", ageHours = 48)),
        )

        val report = service.scan()

        assertThat(report.missingArtifacts).isEmpty()
        assertThat(report.orphanedArtifacts).singleElement().satisfies({
            assertThat(it.key).isEqualTo("ns:orphan:0.1.0:jar")
            assertThat(it.ageHours).isEqualTo(48L)
            assertThat(it.sizeBytes).isEqualTo(123L)
        })
    }

    @Test
    fun `partial overlap classifies known, missing, and orphan separately`() {
        val knownKey = "ns:known:1.0.0:jar"
        val missingKey = "ns:gone:2.0.0:jar"
        val orphanKey = "ns:orphan:0.1.0:jar"
        val knownRelease = release(key = knownKey, pluginId = "known", version = "1.0.0")
        val missingRelease = release(key = missingKey, pluginId = "gone", version = "2.0.0")

        val service = buildService(
            dbKeys = listOf(knownKey, missingKey),
            storageObjects = listOf(
                storageObject(knownKey, ageHours = 5),
                storageObject(orphanKey, ageHours = 100),
            ),
            releasesForHydration = listOf(knownRelease, missingRelease),
        )

        val report = service.scan()

        assertThat(report.missingArtifacts.map { it.artifactKey }).containsExactly(missingKey)
        assertThat(report.orphanedArtifacts.map { it.key }).containsExactly(orphanKey)
        assertThat(report.totalDbRows).isEqualTo(2)
        assertThat(report.totalStorageObjects).isEqualTo(2)
    }

    @Test
    fun `scan aborts with StorageScanLimitExceededException over maxKeysPerScan`() {
        val keys = (1..10).map { "ns:k$it:1.0.0:jar" }
        val service = buildService(
            dbKeys = emptyList(),
            storageObjects = keys.map { storageObject(it, ageHours = 1) },
            maxKeysPerScan = 5,
        )

        assertThatThrownBy { service.scan() }
            .isInstanceOf(StorageScanLimitExceededException::class.java)
            .hasMessageContaining("limit: 5")
    }

    private fun buildService(
        dbKeys: List<String>,
        storageObjects: List<StorageObjectInfo>,
        releasesForHydration: List<PluginReleaseEntity> = emptyList(),
        maxKeysPerScan: Int = 100,
    ): StorageConsistencyService {
        val repo = mock<PluginReleaseRepository>()
        whenever(repo.findAllArtifactKeys()).thenReturn(dbKeys)
        whenever(repo.findAll()).thenReturn(releasesForHydration)

        val storage = mock<ArtifactStorageService>()
        whenever(storage.listObjects("")).thenReturn(storageObjects.asSequence())

        val props = PlugwerkProperties(
            storage = PlugwerkProperties.StorageProperties(
                consistency = PlugwerkProperties.StorageProperties.ConsistencyProperties(
                    maxKeysPerScan = maxKeysPerScan,
                ),
            ),
            auth = PlugwerkProperties.AuthProperties(
                jwtSecret = "test-secret-at-least-32-chars-long!!",
            ),
        )

        return StorageConsistencyService(storage, repo, props, clock)
    }

    private fun storageObject(key: String, ageHours: Long): StorageObjectInfo =
        StorageObjectInfo(
            key = key,
            lastModified = now.minusSeconds(ageHours * 3600),
            sizeBytes = 123L,
        )

    private fun release(
        key: String,
        pluginId: String,
        version: String,
        id: UUID = UUID.randomUUID(),
    ): PluginReleaseEntity {
        val ns = NamespaceEntity(slug = "ns", name = "Namespace")
        val plugin = PluginEntity(namespace = ns, pluginId = pluginId, name = pluginId)
        return PluginReleaseEntity(
            id = id,
            plugin = plugin,
            version = version,
            artifactSha256 = "sha",
            artifactKey = key,
        )
    }
}
