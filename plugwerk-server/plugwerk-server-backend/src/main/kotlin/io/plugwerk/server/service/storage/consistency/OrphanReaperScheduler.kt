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

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import io.plugwerk.server.PlugwerkProperties
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Periodic orphan-artifact reaper (#496).
 *
 * Runs on the cron configured by `plugwerk.storage.reaper.cron`, scoped
 * cluster-wide by ShedLock so only one Plugwerk instance executes per
 * tick (see [io.plugwerk.server.config.SchedulerLockConfig] + migration
 * `0033_shedlock`). Two protective layers guard against TOCTOU with the
 * publish flow:
 *
 *  1. **Grace period** — only keys older than
 *     `plugwerk.storage.reaper.grace-period-hours` are considered. The
 *     publish flow writes storage first and inserts the DB row second,
 *     so an in-flight publish would otherwise look exactly like an
 *     orphan; the grace period is far longer than the longest plausible
 *     publish transaction (default 24h).
 *  2. **DB recheck-in-tx** — delegates the actual delete to
 *     [StorageConsistencyAdminService.deleteOrphanedArtifacts], which
 *     re-queries `findAllArtifactKeys()` inside the delete transaction
 *     and skips any key that has reappeared in the DB.
 *
 * Dry-run is the default. The reaper logs the keys it WOULD delete but
 * does not call `storage.delete`. Operators are expected to inspect the
 * eviction list for a release cycle before flipping `dry-run` to false.
 *
 * Bounded by `max-deletes-per-tick` so a misconfigured prefix cannot
 * silently wipe terabytes — the remainder rolls over to the next tick.
 */
@Component
@ConditionalOnProperty(
    prefix = "plugwerk.storage.reaper",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = true,
)
class OrphanReaperScheduler(
    private val consistencyService: StorageConsistencyService,
    private val adminService: StorageConsistencyAdminService,
    private val properties: PlugwerkProperties,
    meterRegistry: MeterRegistry,
) {
    private val log = LoggerFactory.getLogger(OrphanReaperScheduler::class.java)

    private val deletedCounter: Counter = Counter
        .builder("plugwerk.storage.reaper.deleted")
        .description("Total orphan storage objects deleted by the reaper")
        .register(meterRegistry)

    private val skippedCounter: Counter = Counter
        .builder("plugwerk.storage.reaper.skipped")
        .description(
            "Total orphans the reaper skipped (grace period, re-referenced, " +
                "or storage backend rejected the delete)",
        )
        .register(meterRegistry)

    private val runTimer: Timer = Timer
        .builder("plugwerk.storage.reaper.run")
        .description("Wall-clock time for a single reaper invocation")
        .register(meterRegistry)

    /**
     * Reaper tick. Cron taken from
     * `plugwerk.storage.reaper.cron` (default `0 15 3 * * *`). `lockAtMostFor`
     * is generous (`PT2H`) because a full-bucket scan on a large S3 backend
     * may legitimately take many minutes; `lockAtLeastFor` is short (`PT30S`)
     * so a fast tick on a quiet cluster does not block the next scheduled
     * run unnecessarily.
     */
    @Scheduled(cron = "\${plugwerk.storage.reaper.cron:0 15 3 * * *}")
    @SchedulerLock(
        name = "orphan-storage-reaper",
        lockAtMostFor = "PT2H",
        lockAtLeastFor = "PT30S",
    )
    fun reap() {
        val reaper = properties.storage.reaper
        val sample = Timer.start()
        try {
            val report = consistencyService.scan()
            val graceHours = reaper.gracePeriodHours.toLong()
            val eligible = report.orphanedArtifacts
                .filter { it.ageHours >= graceHours }
            val skippedByGrace = report.orphanedArtifacts.size - eligible.size
            if (skippedByGrace > 0) {
                skippedCounter.increment(skippedByGrace.toDouble())
            }

            if (eligible.isEmpty()) {
                log.info(
                    "reaper tick: orphans={}, skippedByGrace={}, eligible=0 — nothing to do",
                    report.orphanedArtifacts.size,
                    skippedByGrace,
                )
                return
            }

            val capped = eligible.take(reaper.maxDeletesPerTick)
            if (capped.size < eligible.size) {
                log.warn(
                    "reaper tick: max-deletes-per-tick={} hit, {} eligible orphans " +
                        "deferred to next run",
                    reaper.maxDeletesPerTick,
                    eligible.size - capped.size,
                )
            }

            if (reaper.dryRun) {
                log.info(
                    "reaper tick (DRY-RUN): would delete {} orphan(s); skippedByGrace={}",
                    capped.size,
                    skippedByGrace,
                )
                for (orphan in capped) {
                    log.info(
                        "reaper tick (DRY-RUN): would delete key={} sizeBytes={} ageHours={}",
                        orphan.key,
                        orphan.sizeBytes,
                        orphan.ageHours,
                    )
                }
                return
            }

            val result = adminService.deleteOrphanedArtifacts(capped.map { it.key })
            deletedCounter.increment(result.deleted.size.toDouble())
            skippedCounter.increment(result.skipped.size.toDouble())
            log.info(
                "reaper tick: deleted={}, skipped={}, skippedByGrace={}, " +
                    "totalOrphansSeen={}",
                result.deleted.size,
                result.skipped.size,
                skippedByGrace,
                report.orphanedArtifacts.size,
            )
        } catch (ex: StorageScanLimitExceededException) {
            // Bucket is bigger than the consistency-scan circuit breaker —
            // a partial scan would only delete a biased subset, so we skip
            // this tick entirely and let the operator decide via the admin UI.
            log.warn(
                "reaper tick aborted: scan limit exceeded (limit={}, scanned={}). " +
                    "Increase plugwerk.storage.consistency.max-keys-per-scan or " +
                    "trim the bucket before the reaper can run.",
                ex.limit,
                ex.scannedSoFar,
            )
        } catch (ex: Exception) {
            // Don't let one bad tick prevent future ones. ShedLock guarantees
            // the next tick gets a fresh lock; we just need to make sure this
            // method does not propagate the failure to the scheduler thread.
            log.error("reaper tick failed: {}", ex.message, ex)
        } finally {
            sample.stop(runTimer)
        }
    }
}
