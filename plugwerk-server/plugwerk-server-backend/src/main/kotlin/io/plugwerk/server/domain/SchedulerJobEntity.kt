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
package io.plugwerk.server.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.PrePersist
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import java.time.OffsetDateTime

/**
 * Outcome of a single scheduled-job invocation (#516).
 *
 * Reported back to the admin dashboard so operators can distinguish a
 * silent skip (`SKIPPED_DISABLED` via the toggle, `SKIPPED_LOCK` when a
 * peer instance held the ShedLock) from a real run that succeeded or
 * crashed.
 */
enum class SchedulerJobOutcome {
    SUCCESS,
    FAILED,
    SKIPPED_DISABLED,
    SKIPPED_LOCK,
    ABORTED_LIMIT,
}

/**
 * Admin-controllable runtime state for a single `@Scheduled` job (#516).
 *
 * The yaml/annotation pair stays authoritative for cron and tuning
 * constants — this row only carries the toggles and bookkeeping fields
 * the admin dashboard needs. `name` matches the `@SchedulerLock` lock
 * name and the in-process `SchedulerJobRegistry` key.
 */
@Entity
@Table(name = "scheduler_job")
class SchedulerJobEntity(

    @Id
    @Column(name = "name", updatable = false, length = 64)
    var name: String = "",

    @Column(name = "enabled", nullable = false)
    var enabled: Boolean = true,

    /**
     * `null` = "this job has no dry-run concept". When non-null the value
     * overrides the yaml default (`plugwerk.storage.reaper.dry-run`).
     */
    @Column(name = "dry_run")
    var dryRun: Boolean? = null,

    @Column(name = "last_run_at")
    var lastRunAt: OffsetDateTime? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "last_run_outcome", length = 32)
    var lastRunOutcome: SchedulerJobOutcome? = null,

    @Column(name = "last_run_duration_ms")
    var lastRunDurationMs: Long? = null,

    @Column(name = "last_run_message", columnDefinition = "text")
    var lastRunMessage: String? = null,

    @Column(name = "run_count_total", nullable = false)
    var runCountTotal: Long = 0,

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: OffsetDateTime = OffsetDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: OffsetDateTime = OffsetDateTime.now(),
) {
    @PrePersist
    fun onPersist() {
        val now = OffsetDateTime.now()
        if (createdAt.isAfter(now)) createdAt = now
        updatedAt = now
    }

    @PreUpdate
    fun onUpdate() {
        updatedAt = OffsetDateTime.now()
    }
}
