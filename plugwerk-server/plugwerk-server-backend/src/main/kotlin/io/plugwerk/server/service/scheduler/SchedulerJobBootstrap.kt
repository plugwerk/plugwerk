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
import io.plugwerk.server.repository.SchedulerJobRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * Seeds the `scheduler_job` table with one row per registered job on
 * application startup (#516). Idempotent — only INSERTs rows that are
 * not yet present, never updates existing rows, so an operator-changed
 * `enabled` or `dry_run` value is preserved across restarts.
 *
 * Multi-instance safe: two Plugwerk processes booting in parallel will
 * race on the `INSERT`, but the PK on `name` makes one of them fail
 * cleanly and we swallow the conflict — both end up with the same
 * row, which is exactly what we want.
 */
@Component
class SchedulerJobBootstrap(
    private val registry: SchedulerJobRegistry,
    private val repository: SchedulerJobRepository,
) {
    private val log = LoggerFactory.getLogger(SchedulerJobBootstrap::class.java)

    @EventListener(ApplicationReadyEvent::class)
    @Transactional
    fun seedRegisteredJobs() {
        val existing = repository.findAll().mapTo(HashSet()) { it.name }
        val missing = registry.all().filterNot { it.name in existing }
        if (missing.isEmpty()) {
            log.debug("scheduler bootstrap: all {} registered job(s) already seeded", existing.size)
            return
        }
        for (descriptor in missing) {
            try {
                repository.save(
                    SchedulerJobEntity(
                        name = descriptor.name,
                        enabled = true,
                        // Initial null = honour the yaml default; operators
                        // can override later via the admin UI.
                        dryRun = null,
                    ),
                )
                log.info(
                    "scheduler bootstrap: seeded job '{}' (cron='{}', supportsDryRun={})",
                    descriptor.name,
                    descriptor.cronExpression,
                    descriptor.supportsDryRun,
                )
            } catch (ex: org.springframework.dao.DataIntegrityViolationException) {
                // A peer instance won the race — its row is now in place,
                // which is the same outcome we wanted. Nothing to fix.
                log.debug(
                    "scheduler bootstrap: peer instance already seeded '{}' — continuing",
                    descriptor.name,
                )
            }
        }
    }
}
