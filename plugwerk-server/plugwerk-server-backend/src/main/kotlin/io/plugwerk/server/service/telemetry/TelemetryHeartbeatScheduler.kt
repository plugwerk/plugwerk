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
package io.plugwerk.server.service.telemetry

import io.plugwerk.server.PlugwerkProperties
import io.plugwerk.server.service.scheduler.SchedulerJobAuditor
import io.plugwerk.server.service.scheduler.SchedulerJobDescriptor
import io.plugwerk.server.service.scheduler.SchedulerJobRegistry
import jakarta.annotation.PostConstruct
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Lazy
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Daily telemetry heartbeat (DEV-23 / ADR-0039).
 *
 * `@ConditionalOnProperty`-gated on `plugwerk.telemetry.enabled` so that an
 * opted-out installation registers **no** schedule at all (the strongest reading
 * of "no scheduling when disabled") — mirroring
 * [io.plugwerk.server.service.storage.consistency.OrphanReaperScheduler]. When
 * enabled, the job is registered with the scheduler control plane (ADR-0036), so
 * an operator can pause it from the admin dashboard and it is bootstrapped into
 * the `scheduler_job` table on startup.
 *
 * The tick runs through [SchedulerJobAuditor.gateAndRun] (control-plane
 * enable/disable + run accounting) and delegates to [TelemetryBeacon.emit], which
 * is itself fail-open — so a failing send is swallowed inside the beacon and the
 * job records `SUCCESS` for "dispatched the heartbeat". ShedLock scopes the tick
 * cluster-wide so only one instance emits per day. Dry-run is not meaningful for
 * a fire-and-forget beacon, hence `supportsDryRun = false`.
 */
@Component
@ConditionalOnProperty(
    prefix = "plugwerk.telemetry",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = true,
)
class TelemetryHeartbeatScheduler(
    private val beacon: TelemetryBeacon,
    private val properties: PlugwerkProperties,
    private val schedulerJobRegistry: SchedulerJobRegistry,
    private val schedulerJobAuditor: SchedulerJobAuditor,
) {

    /** See [io.plugwerk.server.service.storage.consistency.OrphanReaperScheduler.self] — same Spring-proxy reason. */
    @Autowired
    @Lazy
    private lateinit var self: TelemetryHeartbeatScheduler

    @PostConstruct
    fun registerScheduledJob() {
        schedulerJobRegistry.register(
            SchedulerJobDescriptor(
                name = JOB_NAME,
                description = "Sends the daily Plugwerk opt-out telemetry heartbeat " +
                    "(anonymous install id, version, install type — zero PII). " +
                    "Disable globally with PLUGWERK_TELEMETRY=false.",
                cronExpression = properties.telemetry.cron,
                supportsDryRun = false,
                runNowExecutor = { self.heartbeat() },
            ),
        )
    }

    /**
     * Heartbeat tick. Cron from `plugwerk.telemetry.cron` (default `0 30 3 * * *`).
     * Lock windows are tight because the body is a single bounded HTTP call:
     * `lockAtMostFor=PT2M` comfortably covers the short connect/read timeouts even
     * under retry-free worst case, and `lockAtLeastFor=PT10S` avoids a second
     * instance double-emitting on a fast tick.
     */
    @Scheduled(cron = "\${plugwerk.telemetry.cron:0 30 3 * * *}")
    @SchedulerLock(
        name = JOB_NAME,
        lockAtMostFor = "PT2M",
        lockAtLeastFor = "PT10S",
    )
    fun heartbeat() {
        schedulerJobAuditor.gateAndRun(JOB_NAME) { beacon.emit(TelemetryEvent.HEARTBEAT) }
    }

    companion object {
        private const val JOB_NAME = "telemetry-heartbeat"
    }
}
