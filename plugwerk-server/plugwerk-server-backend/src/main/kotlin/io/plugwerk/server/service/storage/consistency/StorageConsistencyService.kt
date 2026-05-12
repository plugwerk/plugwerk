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
import io.plugwerk.server.repository.PluginReleaseRepository
import io.plugwerk.server.service.storage.ArtifactStorageService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * Bidirectional reconciliation between `plugin_release` DB rows and the
 * configured [ArtifactStorageService] (#190).
 *
 * Pure logic — no remediation here; see `StorageConsistencyAdminService`
 * for the admin-triggered DB/storage deletes. The reaper job in #496
 * reuses this service's [scan] output to drive orphan deletion under a
 * grace-period filter.
 *
 * Algorithm:
 *  1. Load every `artifactKey` from `plugin_release` into a hash set
 *     (one cheap string-projection query).
 *  2. Stream the storage backend via `listObjects("")`, applying the
 *     `max-keys-per-scan` circuit breaker.
 *  3. Partition: storage keys seen → if in DB-set, "known"; else "orphan".
 *  4. DB keys never seen in storage become "missing".
 *  5. Hydrate the missing-artifacts list with plugin id/version from the
 *     DB so the report renders meaningful identifiers in the UI.
 *
 * Memory: O(|DB keys| + |missing-artifact hydrate set|). Storage side
 * is streamed; we never materialise the full storage key list.
 */
@Service
class StorageConsistencyService(
    private val storage: ArtifactStorageService,
    private val releaseRepository: PluginReleaseRepository,
    private val properties: PlugwerkProperties,
    private val clock: Clock = Clock.systemUTC(),
) {

    private val log = LoggerFactory.getLogger(StorageConsistencyService::class.java)

    @Transactional(readOnly = true)
    fun scan(): ConsistencyReport {
        val maxKeys = properties.storage.consistency.maxKeysPerScan
        val scannedAt = Instant.now(clock)

        val dbKeys: Set<String> = releaseRepository.findAllArtifactKeys().toHashSet()
        val totalDbRows = dbKeys.size

        val orphanedArtifacts = mutableListOf<OrphanedArtifact>()
        val storageKeysSeen = HashSet<String>(maxOf(16, dbKeys.size))
        var totalStorageObjects = 0

        for (obj in storage.listObjects("")) {
            totalStorageObjects++
            if (totalStorageObjects > maxKeys) {
                throw StorageScanLimitExceededException(limit = maxKeys, scannedSoFar = totalStorageObjects)
            }
            storageKeysSeen.add(obj.key)
            if (obj.key !in dbKeys) {
                val ageHours = Duration.between(obj.lastModified, scannedAt).toHours().coerceAtLeast(0L)
                orphanedArtifacts.add(
                    OrphanedArtifact(
                        key = obj.key,
                        lastModified = obj.lastModified,
                        ageHours = ageHours,
                        sizeBytes = obj.sizeBytes,
                    ),
                )
            }
        }

        val missingKeys: Set<String> = dbKeys - storageKeysSeen
        val missingArtifacts: List<MissingArtifact> = if (missingKeys.isEmpty()) {
            emptyList()
        } else {
            releaseRepository
                .findAll()
                .asSequence()
                .filter { it.artifactKey in missingKeys }
                .map { release ->
                    MissingArtifact(
                        releaseId = release.id ?: error("PluginReleaseEntity is detached from persistence context"),
                        pluginId = release.plugin.pluginId,
                        version = release.version,
                        artifactKey = release.artifactKey,
                    )
                }
                .toList()
        }

        log.info(
            "Storage consistency scan completed: dbRows={}, storageObjects={}, missing={}, orphaned={}",
            totalDbRows,
            totalStorageObjects,
            missingArtifacts.size,
            orphanedArtifacts.size,
        )

        return ConsistencyReport(
            missingArtifacts = missingArtifacts,
            orphanedArtifacts = orphanedArtifacts.toList(),
            scannedAt = scannedAt,
            totalDbRows = totalDbRows,
            totalStorageObjects = totalStorageObjects,
        )
    }
}
