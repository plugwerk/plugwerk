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
package io.plugwerk.server.service.scheduler

import com.github.benmanes.caffeine.cache.Caffeine
import io.plugwerk.server.repository.SchedulerJobRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration

/**
 * Snapshot of the admin-controlled toggles for a single job (#516).
 *
 * The two fields are read tens of thousands of times per day on the
 * scheduler thread but only change when an operator clicks a toggle in
 * the dashboard — they ride in the same record so the cache holds a
 * single immutable value per job and the gate read is one map lookup.
 */
data class SchedulerJobState(
    val enabled: Boolean,
    /** `null` = honour the yaml default; non-null overrides it. */
    val dryRunOverride: Boolean?,
)

/**
 * Read-and-write façade over the `scheduler_job` table for the gate +
 * toggle paths (#516).
 *
 * - Reads are cached with a 30 s TTL because the gate fires on every
 *   tick and a fresh SELECT per scheduled invocation is wasted work.
 * - Writes from the admin endpoints `evict()` the cached row so the
 *   next tick on the same instance sees the change immediately. Peer
 *   instances see it after at most the TTL — acceptable latency for
 *   hourly / daily jobs, and the alternative (LISTEN/NOTIFY) would add
 *   a noticeable layer of plumbing for a feature where seconds of stale
 *   read costs nothing.
 */
@Service
class SchedulerJobService(private val repository: SchedulerJobRepository) {
    private val cache = Caffeine.newBuilder()
        .expireAfterWrite(Duration.ofSeconds(30))
        .maximumSize(64)
        .build<String, SchedulerJobState>()

    /**
     * Should the scheduler invoke this job's body on the current tick?
     * Returns `true` if the row is missing (the bootstrap hasn't run
     * yet on a fresh DB) so we never silently disable a job because
     * the metadata is incomplete.
     */
    fun shouldRun(name: String): Boolean = load(name).enabled

    /**
     * Returns the operator-set dry-run override, or `null` if there is
     * none. Callers (currently only [io.plugwerk.server.service.storage.consistency.OrphanReaperScheduler])
     * fall back to their own yaml default when this is null.
     */
    fun getDryRunOverride(name: String): Boolean? = load(name).dryRunOverride

    /**
     * Drops the cached state for [name] so the next read goes to the DB.
     * Called from the toggle endpoint after a successful update.
     */
    fun invalidate(name: String) {
        cache.invalidate(name)
    }

    @Transactional(readOnly = true)
    private fun load(name: String): SchedulerJobState = cache.get(name) { key ->
        repository.findById(key)
            .map { SchedulerJobState(enabled = it.enabled, dryRunOverride = it.dryRun) }
            // Fail-open: a missing row should not silently disable a
            // job (would be invisible until the operator opens the
            // dashboard). Bootstrap will create the row on next boot.
            .orElse(SchedulerJobState(enabled = true, dryRunOverride = null))
    }
}
