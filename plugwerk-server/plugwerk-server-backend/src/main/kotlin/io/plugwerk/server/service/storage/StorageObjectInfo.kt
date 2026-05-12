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
package io.plugwerk.server.service.storage

import java.time.Instant

/**
 * Metadata-carrying counterpart to a bare storage key, returned by
 * [ArtifactStorageService.listObjects] (#190).
 *
 * Used by:
 *  - the consistency-check UI to show artifact age + size to the admin
 *  - the orphan-reaper (#496) to enforce a `lastModified < now - graceHours`
 *    filter that prevents TOCTOU deletion of freshly-uploaded artifacts
 *
 * @property key Storage-relative key (no bucket prefix, no root path) —
 *   matches the format passed to [ArtifactStorageService.store] /
 *   [ArtifactStorageService.retrieve] / [ArtifactStorageService.delete].
 * @property lastModified Backend-reported modification timestamp.
 *   - Filesystem: `Files.getLastModifiedTime(path).toInstant()`.
 *   - S3: `S3Object.lastModified()`.
 * @property sizeBytes Object size as reported by the backend.
 */
data class StorageObjectInfo(val key: String, val lastModified: Instant, val sizeBytes: Long)
