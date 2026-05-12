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

/**
 * Thrown when the storage-side listing exceeds
 * `plugwerk.storage.consistency.max-keys-per-scan` (#190).
 *
 * Fail-fast circuit breaker against a runaway bucket scan — an operator
 * with 10M objects in S3 shouldn't get a 5-minute-spinning admin UI;
 * they should get a clear "too many keys; scan is sync-only for now"
 * error and a follow-up async-mode work item.
 */
class StorageScanLimitExceededException(val limit: Int, val scannedSoFar: Int) :
    RuntimeException(
        "Storage scan aborted after $scannedSoFar objects (limit: $limit). " +
            "Set plugwerk.storage.consistency.max-keys-per-scan higher or " +
            "narrow the scan (#190 supports only synchronous scans for now).",
    )
