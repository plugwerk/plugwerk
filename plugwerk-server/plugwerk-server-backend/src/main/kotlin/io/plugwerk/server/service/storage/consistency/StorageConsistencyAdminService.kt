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

import io.plugwerk.server.repository.PluginReleaseRepository
import io.plugwerk.server.service.storage.ArtifactStorageService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Outcome of a bulk storage-key deletion (#190). Each input key is reported
 * as either successfully deleted or skipped (because it became referenced
 * by a `plugin_release` row between the scan and the delete call).
 */
data class BulkArtifactDeletionResult(val deleted: List<String>, val skipped: List<String>)

/**
 * Admin-triggered remediation for findings produced by
 * [StorageConsistencyService] (#190).
 *
 * Every call:
 *  1. Re-checks the relevant DB state inside its own transaction so the
 *     scan snapshot cannot drive a delete against state that has since
 *     changed. This narrows but does not eliminate the TOCTOU window;
 *     the admin is explicitly clicking "delete" so a tight race here is
 *     acceptable (the reaper in #496 carries the broader grace-period
 *     protection).
 *  2. Emits a structured audit log line so the action shows up in the
 *     operator log shipper regardless of whether downstream consumers
 *     subscribe to a real audit-log channel.
 *  3. Is idempotent — a second call on a vanished release succeeds
 *     without throwing.
 */
@Service
class StorageConsistencyAdminService(
    private val releaseRepository: PluginReleaseRepository,
    private val storage: ArtifactStorageService,
) {

    private val log = LoggerFactory.getLogger(StorageConsistencyAdminService::class.java)

    /**
     * Removes a `plugin_release` row whose artifact is missing from storage.
     * Idempotent — second call on a vanished row is a no-op.
     *
     * The storage `delete()` is invoked too — defensive cleanup in case the
     * scan was stale and the file did exist after all. S3/FS delete is
     * idempotent so a no-op file delete is harmless.
     */
    @Transactional
    fun deleteOrphanedRelease(releaseId: UUID) {
        val release = releaseRepository.findById(releaseId).orElse(null)
        if (release == null) {
            log.info(
                "audit storage-consistency action=delete-release releaseId={} outcome=already-gone",
                releaseId,
            )
            return
        }
        val key = release.artifactKey
        releaseRepository.delete(release)
        // Best-effort storage delete — see #481 for why this lives after the
        // DB delete and is allowed to throw without rolling back.
        runCatching { storage.delete(key) }.onFailure {
            log.warn(
                "audit storage-consistency action=delete-release releaseId={} key={} outcome=db-deleted-storage-failed: {}",
                releaseId,
                key,
                it.message,
            )
            return
        }
        log.info(
            "audit storage-consistency action=delete-release releaseId={} key={} outcome=deleted",
            releaseId,
            key,
        )
    }

    /**
     * Removes a batch of `plugin_release` rows whose artifacts are missing
     * from storage. Same semantics as [deleteOrphanedRelease] applied per
     * ID, but with one DB transaction and one round-trip. Each ID is also
     * re-checked against storage — if the file has reappeared since the
     * scan (e.g. operator just restored a backup) the row is left intact
     * and reported back as skipped so the admin UI can keep the entry on
     * screen.
     */
    @Transactional
    fun deleteOrphanedReleases(releaseIds: List<UUID>): BulkReleaseDeletionResult {
        if (releaseIds.isEmpty()) return BulkReleaseDeletionResult(deleted = emptyList(), skipped = emptyList())

        val deleted = mutableListOf<UUID>()
        val skipped = mutableListOf<UUID>()
        for (id in releaseIds) {
            val release = releaseRepository.findById(id).orElse(null)
            if (release == null) {
                log.info(
                    "audit storage-consistency action=delete-release releaseId={} outcome=already-gone",
                    id,
                )
                skipped.add(id)
                continue
            }
            val key = release.artifactKey
            if (storage.exists(key)) {
                // The artifact reappeared between scan and this delete —
                // refuse to amputate the DB row that now legitimately
                // references a real file.
                log.warn(
                    "audit storage-consistency action=delete-release releaseId={} key={} outcome=skipped-storage-reappeared",
                    id,
                    key,
                )
                skipped.add(id)
                continue
            }
            releaseRepository.delete(release)
            log.info(
                "audit storage-consistency action=delete-release releaseId={} key={} outcome=deleted",
                id,
                key,
            )
            deleted.add(id)
        }
        return BulkReleaseDeletionResult(deleted = deleted.toList(), skipped = skipped.toList())
    }

    /**
     * Removes orphaned storage keys (manual / admin-triggered — bypasses
     * the reaper's grace period). For each key we re-check that no DB row
     * references it inside this transaction; if a row appeared after the
     * scan, the key is skipped and reported in [BulkArtifactDeletionResult].
     */
    @Transactional
    fun deleteOrphanedArtifacts(keys: List<String>): BulkArtifactDeletionResult {
        if (keys.isEmpty()) return BulkArtifactDeletionResult(deleted = emptyList(), skipped = emptyList())

        val referenced: Set<String> = releaseRepository
            .findAllArtifactKeys()
            .filter { it in keys }
            .toHashSet()

        val deleted = mutableListOf<String>()
        val skipped = mutableListOf<String>()
        for (key in keys) {
            if (key in referenced) {
                log.warn(
                    "audit storage-consistency action=delete-artifact key={} outcome=skipped-now-referenced",
                    key,
                )
                skipped.add(key)
                continue
            }
            runCatching { storage.delete(key) }
                .onSuccess {
                    log.info(
                        "audit storage-consistency action=delete-artifact key={} outcome=deleted",
                        key,
                    )
                    deleted.add(key)
                }
                .onFailure {
                    log.warn(
                        "audit storage-consistency action=delete-artifact key={} outcome=storage-failed: {}",
                        key,
                        it.message,
                    )
                    skipped.add(key)
                }
        }
        return BulkArtifactDeletionResult(deleted = deleted.toList(), skipped = skipped.toList())
    }
}
