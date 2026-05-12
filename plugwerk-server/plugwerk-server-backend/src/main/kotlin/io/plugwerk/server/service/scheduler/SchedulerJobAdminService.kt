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

import io.plugwerk.server.domain.SchedulerJobEntity
import io.plugwerk.server.domain.SchedulerJobOutcome
import io.plugwerk.server.repository.SchedulerJobRepository
import io.plugwerk.server.service.EntityNotFoundException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Read-write façade behind `AdminSchedulerController` (#516).
 *
 * Combines compile-time registry metadata (description, cron,
 * supportsDryRun) with admin-controlled runtime state (enabled, dryRun,
 * lastRun*, runCountTotal) into a single dashboard row, and gates the
 * mutation endpoints against unknown job names.
 */
@Service
class SchedulerJobAdminService(
    private val registry: SchedulerJobRegistry,
    private val repository: SchedulerJobRepository,
    private val service: SchedulerJobService,
) {
    private val log = LoggerFactory.getLogger(SchedulerJobAdminService::class.java)

    data class JobView(val descriptor: SchedulerJobDescriptor, val state: SchedulerJobEntity)

    @Transactional(readOnly = true)
    fun listJobs(): List<JobView> {
        val rows = repository.findAll().associateBy { it.name }
        return registry.all().map { descriptor ->
            JobView(
                descriptor = descriptor,
                // Bootstrap should have seeded everything; if a row is
                // missing we hand back a transient default so the
                // dashboard at least renders the job — the bootstrap
                // will write the real row on next boot.
                state = rows[descriptor.name] ?: SchedulerJobEntity(name = descriptor.name),
            )
        }
    }

    @Transactional(readOnly = true)
    fun getJob(name: String): JobView {
        val descriptor = registry.find(name)
            ?: throw EntityNotFoundException("SchedulerJob", name)
        val state = repository.findById(name)
            .orElse(SchedulerJobEntity(name = name))
        return JobView(descriptor, state)
    }

    /**
     * Patches the admin-controlled flags. `enabled = null` leaves the
     * current value alone (the UI sends the full intended state, so a
     * missing field is rare but harmless). `dryRun = null` clears the
     * override and falls back to the yaml default — there is no
     * "leave dry-run alone, change only enabled" path because the
     * UI sends both fields together.
     */
    @Transactional
    fun update(name: String, enabled: Boolean?, dryRun: Boolean?): JobView {
        val descriptor = registry.find(name)
            ?: throw EntityNotFoundException("SchedulerJob", name)
        if (dryRun != null && !descriptor.supportsDryRun) {
            log.warn(
                "scheduler admin: refused dryRun update on '{}' which does not support dry-run",
                name,
            )
        }
        val row = repository.findById(name).orElseGet { SchedulerJobEntity(name = name) }
        enabled?.let { row.enabled = it }
        row.dryRun = if (descriptor.supportsDryRun) dryRun else null
        val saved = repository.save(row)
        // Bust the cache so the next scheduler tick on this instance picks
        // up the change immediately. Peer instances see it after the TTL.
        service.invalidate(name)
        log.info(
            "scheduler admin: job '{}' updated (enabled={}, dryRun={})",
            name,
            saved.enabled,
            saved.dryRun,
        )
        return JobView(descriptor, saved)
    }

    data class RunOutcome(val outcome: SchedulerJobOutcome, val durationMs: Long?, val message: String?)

    /**
     * Off-schedule invocation triggered by the admin UI. Routes through
     * the registered executor, which itself is wrapped by the same
     * `@SchedulerLock` annotation as the cron tick — so a concurrent
     * tick rejects this call cleanly via the gate-and-run audit path.
     */
    fun runNow(name: String): RunOutcome {
        val descriptor = registry.find(name)
            ?: throw EntityNotFoundException("SchedulerJob", name)
        val startedNanos = System.nanoTime()
        return try {
            descriptor.runNowExecutor.invoke()
            val durationMs = (System.nanoTime() - startedNanos) / 1_000_000L
            // The executor itself went through gateAndRun, which persisted
            // the outcome. Read the canonical value back so the response
            // matches what the dashboard will show on its next refresh.
            val refreshed = repository.findById(name)
            RunOutcome(
                outcome = refreshed.map { it.lastRunOutcome ?: SchedulerJobOutcome.SUCCESS }
                    .orElse(SchedulerJobOutcome.SUCCESS),
                durationMs = refreshed.flatMap { java.util.Optional.ofNullable(it.lastRunDurationMs) }
                    .orElse(durationMs),
                message = refreshed.flatMap { java.util.Optional.ofNullable(it.lastRunMessage) }
                    .orElse(null),
            )
        } catch (ex: Exception) {
            val durationMs = (System.nanoTime() - startedNanos) / 1_000_000L
            log.error("scheduler run-now '{}' failed: {}", name, ex.message, ex)
            RunOutcome(
                outcome = SchedulerJobOutcome.FAILED,
                durationMs = durationMs,
                message = ex.message?.take(2000),
            )
        }
    }
}
