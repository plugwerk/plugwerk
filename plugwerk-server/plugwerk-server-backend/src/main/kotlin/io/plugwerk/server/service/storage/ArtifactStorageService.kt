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

import java.io.InputStream

interface ArtifactStorageService {

    fun store(key: String, content: InputStream, contentLength: Long): String

    fun retrieve(key: String): InputStream

    fun delete(key: String)

    fun exists(key: String): Boolean

    /**
     * Returns every key in the backend whose path starts with [prefix] (#191).
     *
     * Designed for the consistency-check use case in #190 (find orphaned artifacts).
     * The empty default returns every key; **do not call without a prefix on a
     * production bucket unless you mean it**.
     *
     * Backend semantics:
     *   - Filesystem: keyspace is bounded by the artifact directory; returns
     *     eagerly-materialised contents wrapped in a Sequence for API symmetry.
     *   - S3: lazy pagination via `ListObjectsV2`; callers MUST either fully
     *     consume the sequence or wrap their use in a try-finally so the
     *     underlying pager is released. Filtering should be done by passing a
     *     non-empty [prefix] so S3 can skip non-matching pages server-side.
     *
     * Returned keys are storage-relative (no bucket prefix, no root path) and
     * match the format passed to [store] / [retrieve] / [delete] / [exists].
     */
    fun listKeys(prefix: String = ""): Sequence<String> = listObjects(prefix).map { it.key }

    /**
     * Returns key + metadata for every object in the backend whose path
     * starts with [prefix] (#190 / #496).
     *
     * Same iteration semantics as [listKeys] — filesystem materialises
     * eagerly, S3 paginates lazily via `ListObjectsV2`. Callers MUST consume
     * the sequence fully or wrap iteration in try-finally so the underlying
     * pager is released.
     *
     * [lastModified] is the backend-reported modification timestamp and is
     * the load-bearing field for the orphan-reaper grace-period check (#496):
     * an artifact uploaded between `listObjects` and the reaper's
     * `delete(key)` cannot be reaped because its `lastModified` will not be
     * older than the configured grace.
     */
    fun listObjects(prefix: String = ""): Sequence<StorageObjectInfo>
}
