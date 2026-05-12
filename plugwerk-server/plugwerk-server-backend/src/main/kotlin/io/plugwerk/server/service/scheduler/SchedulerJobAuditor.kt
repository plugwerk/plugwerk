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

import io.plugwerk.server.domain.SchedulerJobOutcome
import io.plugwerk.server.repository.SchedulerJobRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime

/**
 * Records the outcome of a scheduled-job invocation back into the
 * `scheduler_job` table (#516).
 *
 * The persistence runs in its own `REQUIRES_NEW` transaction so that:
 *  - a rollback in the job body (e.g. the orphan reaper rolling back
 *    a delete) does not also undo the audit write that says "this
 *    just failed"
 *  - an audit-side failure (DB connection blip) does not propagate up
 *    into the scheduler thread and prevent future ticks — we catch
 *    and log so the actual job-status outcome is at most "we don't
 *    have a record", never "the scheduler is now broken"
 */
@Service
class SchedulerJobAuditor(private val repository: SchedulerJobRepository, private val service: SchedulerJobService) {
    private val log = LoggerFactory.getLogger(SchedulerJobAuditor::class.java)

    /**
     * Runs [block] under accounting. The recorded outcome is whatever
     * [block] returns; an uncaught exception becomes `FAILED` with the
     * exception's message captured in `last_run_message`.
     *
     * Returns the recorded outcome so the caller can react (e.g. the
     * run-now endpoint reports it back to the admin UI).
     */
    fun run(name: String, block: () -> SchedulerJobOutcome): SchedulerJobOutcome {
        val startedNanos = System.nanoTime()
        val outcome: SchedulerJobOutcome
        var message: String? = null
        outcome = try {
            block()
        } catch (ex: Exception) {
            message = ex.message?.take(2000) ?: ex.javaClass.simpleName
            log.error("scheduled job '{}' failed: {}", name, ex.message, ex)
            SchedulerJobOutcome.FAILED
        }
        val durationMs = (System.nanoTime() - startedNanos) / 1_000_000L
        persist(name, outcome, message, durationMs)
        return outcome
    }

    /**
     * Convenience for the typical `@Scheduled` body:
     *  1. skip-and-record if the admin toggle is off
     *  2. otherwise wrap [block] under [run]
     *
     * Returning the outcome lets the manual run-now endpoint propagate
     * "we skipped because you disabled this" back to the operator
     * instead of pretending the job ran.
     */
    fun gateAndRun(name: String, block: () -> Unit): SchedulerJobOutcome {
        if (!service.shouldRun(name)) {
            recordSkipped(name, SchedulerJobOutcome.SKIPPED_DISABLED)
            return SchedulerJobOutcome.SKIPPED_DISABLED
        }
        return run(name) {
            block()
            SchedulerJobOutcome.SUCCESS
        }
    }

    /**
     * Records a skipped tick (the toggle is off or a peer instance
     * holds the lock). Distinct path from [run] because skips do not
     * carry a duration that means anything to the operator — they are
     * book-kept so the dashboard can show "this job is alive even
     * though you just disabled it".
     */
    fun recordSkipped(name: String, outcome: SchedulerJobOutcome, message: String? = null) {
        require(
            outcome == SchedulerJobOutcome.SKIPPED_DISABLED ||
                outcome == SchedulerJobOutcome.SKIPPED_LOCK,
        ) { "recordSkipped requires a SKIPPED_* outcome, got $outcome" }
        persist(name, outcome, message, durationMs = null)
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    private fun persist(name: String, outcome: SchedulerJobOutcome, message: String?, durationMs: Long?) {
        try {
            val entity = repository.findById(name).orElse(null) ?: run {
                // Fail-open: a missing row means the bootstrap hasn't
                // run yet (fresh DB) or someone deleted the row by hand.
                // Either way, dropping the audit is preferable to making
                // the scheduler thread throw.
                log.warn(
                    "audit for scheduled job '{}' has no scheduler_job row — skipping persist",
                    name,
                )
                return
            }
            // Only mutate the run-tracking fields; never touch enabled /
            // dry-run, those belong to the admin endpoint.
            entity.lastRunAt = OffsetDateTime.now()
            entity.lastRunOutcome = outcome
            entity.lastRunDurationMs = durationMs
            entity.lastRunMessage = message
            // SKIPPED outcomes do not increment the total — the dashboard's
            // "Runs in last 24h" reflects actual work done.
            if (outcome != SchedulerJobOutcome.SKIPPED_DISABLED &&
                outcome != SchedulerJobOutcome.SKIPPED_LOCK
            ) {
                entity.runCountTotal += 1
            }
            repository.save(entity)
        } catch (ex: Exception) {
            log.warn(
                "failed to record scheduler audit for '{}' ({}): {}",
                name,
                outcome,
                ex.message,
            )
        }
        // Bust the cache so an operator who flipped a toggle moments ago
        // also sees the latest run snapshot on their next dashboard refresh.
        service.invalidate(name)
    }
}
