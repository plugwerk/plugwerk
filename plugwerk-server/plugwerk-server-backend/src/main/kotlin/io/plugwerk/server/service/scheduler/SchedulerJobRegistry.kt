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

import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

/**
 * Boot-time descriptor for a `@Scheduled` job exposed to the admin
 * dashboard (#516).
 *
 * @property name Stable identifier — must match the `@SchedulerLock`
 *   lock name on the corresponding @Scheduled method so the run-now
 *   path can reuse the same lock infrastructure (with a `-manual`
 *   suffix).
 * @property description Short human-readable summary for the dashboard.
 * @property cronExpression The cron pattern this job runs on. Recorded
 *   here purely so the UI can display it; the authoritative source is
 *   still the `@Scheduled(cron = …)` annotation on the method.
 * @property supportsDryRun When true, the dashboard exposes the dry-run
 *   toggle and the job's body honours it. Currently only the orphan-
 *   storage reaper.
 * @property runNowExecutor A side-effecting reference to the same method
 *   the scheduler will invoke on a regular tick. Wired by the owning
 *   service at registration time so the run-now endpoint can trigger an
 *   off-schedule execution that still goes through the gate + audit +
 *   ShedLock layers (under the `-manual` lock-name suffix).
 */
data class SchedulerJobDescriptor(
    val name: String,
    val description: String,
    val cronExpression: String,
    val supportsDryRun: Boolean,
    val runNowExecutor: () -> Unit,
)

/**
 * Singleton registry collecting metadata for every `@Scheduled` job
 * the admin dashboard can show (#516). Each scheduled service calls
 * [register] from its `@PostConstruct` so the registry is populated
 * before the bootstrap seeds the `scheduler_job` table.
 *
 * Thread-safe by virtue of the underlying [ConcurrentHashMap] — the
 * read paths (dashboard + run-now endpoint) are called long after the
 * bean lifecycle has settled, but a defensive choice makes test setup
 * order-insensitive.
 */
@Component
class SchedulerJobRegistry {

    private val byName: MutableMap<String, SchedulerJobDescriptor> = ConcurrentHashMap()

    fun register(descriptor: SchedulerJobDescriptor) {
        val previous = byName.putIfAbsent(descriptor.name, descriptor)
        require(previous == null) {
            "Scheduler job '${descriptor.name}' is already registered — names must be unique"
        }
    }

    fun all(): List<SchedulerJobDescriptor> = byName.values.sortedBy { it.name }

    fun find(name: String): SchedulerJobDescriptor? = byName[name]
}
