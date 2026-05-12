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

import java.time.Instant
import java.util.UUID

/**
 * Snapshot of the bidirectional reconciliation between `plugin_release`
 * rows and `ArtifactStorageService` contents (#190).
 *
 * Two failure modes are distinguished:
 *  - [missingArtifacts]: a DB row points at a key that does not exist in
 *    storage. Cause: storage outage during upload, accidental deletion via
 *    the storage console, or a `fs↔s3` migration that did not copy every
 *    file.
 *  - [orphanedArtifacts]: a key exists in storage with no matching DB row.
 *    Cause: best-effort storage cleanup in
 *    `PluginService.delete` / `NamespaceService.delete` (#481) failed
 *    after the DB transaction committed.
 *
 * The report is immutable and self-describing — UI consumers do not need a
 * separate call to learn how many objects were scanned.
 *
 * @property missingArtifacts DB rows whose `artifactKey` is missing in storage.
 * @property orphanedArtifacts Storage objects with no DB row pointing at them.
 *   `ageHours` is computed against [scannedAt] for stable rendering.
 * @property scannedAt Wall-clock timestamp the scan started.
 * @property totalDbRows Sanity counter — distinct keys across `plugin_release`.
 * @property totalStorageObjects Sanity counter — objects walked in storage.
 */
data class ConsistencyReport(
    val missingArtifacts: List<MissingArtifact>,
    val orphanedArtifacts: List<OrphanedArtifact>,
    val scannedAt: Instant,
    val totalDbRows: Int,
    val totalStorageObjects: Int,
)

/**
 * DB row whose `artifactKey` does not exist in storage. The release-side
 * fields are loaded on demand by the scan service so the report can render
 * a useful "plugin X version Y" identifier in the admin UI without forcing
 * the consumer to re-query.
 */
data class MissingArtifact(val releaseId: UUID, val pluginId: String, val version: String, val artifactKey: String)

/**
 * Storage object with no DB row pointing at it. [ageHours] is computed at
 * scan time for stable rendering across UI re-renders; the reaper (#496)
 * uses it to enforce the grace-period guard against TOCTOU.
 */
data class OrphanedArtifact(val key: String, val lastModified: Instant, val ageHours: Long, val sizeBytes: Long)
