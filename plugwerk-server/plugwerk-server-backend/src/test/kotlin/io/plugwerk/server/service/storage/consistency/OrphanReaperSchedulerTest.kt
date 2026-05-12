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

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.plugwerk.server.PlugwerkProperties
import io.plugwerk.server.service.scheduler.SchedulerJobAuditor
import io.plugwerk.server.service.scheduler.SchedulerJobRegistry
import io.plugwerk.server.service.scheduler.SchedulerJobService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant

class OrphanReaperSchedulerTest {

    private lateinit var consistencyService: StorageConsistencyService
    private lateinit var adminService: StorageConsistencyAdminService
    private lateinit var schedulerJobService: SchedulerJobService
    private lateinit var schedulerJobAuditor: SchedulerJobAuditor
    private lateinit var meterRegistry: SimpleMeterRegistry

    @BeforeEach
    fun setUp() {
        consistencyService = mock()
        adminService = mock()
        schedulerJobService = mock()
        schedulerJobAuditor = mock()
        meterRegistry = SimpleMeterRegistry()
        // Default: no admin override — the body falls back to the yaml
        // properties.dryRun the test sets up via `scheduler(...)`.
        whenever(schedulerJobService.getDryRunOverride(any())).thenReturn(null)
        // gateAndRun is the single entry point each scheduler uses — keep the
        // unit-test focus on the body by making the auditor a transparent pass-
        // through, mirroring the real implementation's exception handling
        // (uncaught throwables become FAILED, the scheduler thread never sees
        // them). The dedicated auditor tests live alongside SchedulerJobService.
        whenever(schedulerJobAuditor.gateAndRun(any(), any())).thenAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            val block = invocation.arguments[1] as () -> Unit
            try {
                block()
                io.plugwerk.server.domain.SchedulerJobOutcome.SUCCESS
            } catch (_: Exception) {
                io.plugwerk.server.domain.SchedulerJobOutcome.FAILED
            }
        }
    }

    @Test
    fun `dry-run logs candidates without calling delete`() {
        val scheduler = scheduler(dryRun = true, gracePeriodHours = 24)
        whenever(consistencyService.scan()).thenReturn(
            report(orphans = listOf(orphan("acme:gone:1.0.0:jar", ageHours = 72))),
        )

        scheduler.reap()

        verify(adminService, never()).deleteOrphanedArtifacts(any())
        assertThat(deletedCount()).isEqualTo(0.0)
    }

    @Test
    fun `grace period filters out fresh orphans`() {
        val scheduler = scheduler(dryRun = false, gracePeriodHours = 24)
        whenever(consistencyService.scan()).thenReturn(
            report(
                orphans = listOf(
                    orphan("acme:fresh:1.0.0:jar", ageHours = 1), // skipped
                    orphan("acme:stale:1.0.0:jar", ageHours = 48), // eligible
                ),
            ),
        )
        whenever(adminService.deleteOrphanedArtifacts(any())).thenReturn(
            BulkArtifactDeletionResult(
                deleted = listOf("acme:stale:1.0.0:jar"),
                skipped = emptyList(),
            ),
        )

        scheduler.reap()

        val keysCaptor = argumentCaptor<List<String>>()
        verify(adminService).deleteOrphanedArtifacts(keysCaptor.capture())
        assertThat(keysCaptor.firstValue).containsExactly("acme:stale:1.0.0:jar")
        // 1 grace-skip + 0 delete-skip = 1 skipped counter increment.
        assertThat(skippedCount()).isEqualTo(1.0)
        assertThat(deletedCount()).isEqualTo(1.0)
    }

    @Test
    fun `max-deletes-per-tick caps the eligible batch`() {
        val scheduler = scheduler(
            dryRun = false,
            gracePeriodHours = 24,
            maxDeletesPerTick = 2,
        )
        whenever(consistencyService.scan()).thenReturn(
            report(
                orphans = (1..5).map {
                    orphan("acme:orphan$it:1.0.0:jar", ageHours = 48)
                },
            ),
        )
        whenever(adminService.deleteOrphanedArtifacts(any())).thenAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            val keys = invocation.arguments[0] as List<String>
            BulkArtifactDeletionResult(deleted = keys, skipped = emptyList())
        }

        scheduler.reap()

        val keysCaptor = argumentCaptor<List<String>>()
        verify(adminService).deleteOrphanedArtifacts(keysCaptor.capture())
        assertThat(keysCaptor.firstValue).hasSize(2)
        assertThat(deletedCount()).isEqualTo(2.0)
    }

    @Test
    fun `scan-limit exception aborts the tick without throwing`() {
        val scheduler = scheduler(dryRun = false, gracePeriodHours = 24)
        whenever(consistencyService.scan())
            .thenThrow(StorageScanLimitExceededException(limit = 100, scannedSoFar = 101))

        scheduler.reap()

        verify(adminService, never()).deleteOrphanedArtifacts(any())
    }

    @Test
    fun `unexpected exception is swallowed so next tick can run`() {
        val scheduler = scheduler(dryRun = false, gracePeriodHours = 24)
        whenever(consistencyService.scan()).thenThrow(RuntimeException("boom"))

        scheduler.reap()

        verify(adminService, never()).deleteOrphanedArtifacts(any())
    }

    @Test
    fun `delete-side skips count towards the skipped meter`() {
        val scheduler = scheduler(dryRun = false, gracePeriodHours = 24)
        whenever(consistencyService.scan()).thenReturn(
            report(
                orphans = listOf(
                    orphan("acme:ok:1.0.0:jar", ageHours = 48),
                    orphan("acme:reborn:1.0.0:jar", ageHours = 72),
                ),
            ),
        )
        whenever(adminService.deleteOrphanedArtifacts(any())).thenReturn(
            BulkArtifactDeletionResult(
                deleted = listOf("acme:ok:1.0.0:jar"),
                skipped = listOf("acme:reborn:1.0.0:jar"),
            ),
        )

        scheduler.reap()

        assertThat(deletedCount()).isEqualTo(1.0)
        assertThat(skippedCount()).isEqualTo(1.0)
    }

    // ---- helpers ----

    private fun scheduler(
        dryRun: Boolean,
        gracePeriodHours: Int,
        maxDeletesPerTick: Int = 1_000,
    ): OrphanReaperScheduler {
        val props = PlugwerkProperties(
            storage = PlugwerkProperties.StorageProperties(
                reaper = PlugwerkProperties.StorageProperties.ReaperProperties(
                    enabled = true,
                    dryRun = dryRun,
                    gracePeriodHours = gracePeriodHours,
                    maxDeletesPerTick = maxDeletesPerTick,
                ),
            ),
        )
        return OrphanReaperScheduler(
            consistencyService,
            adminService,
            props,
            SchedulerJobRegistry(),
            schedulerJobService,
            schedulerJobAuditor,
            meterRegistry,
        )
    }

    private fun report(orphans: List<OrphanedArtifact>): ConsistencyReport = ConsistencyReport(
        missingArtifacts = emptyList(),
        orphanedArtifacts = orphans,
        scannedAt = Instant.parse("2026-05-12T12:00:00Z"),
        totalDbRows = 0,
        totalStorageObjects = orphans.size,
    )

    private fun orphan(key: String, ageHours: Long): OrphanedArtifact = OrphanedArtifact(
        key = key,
        lastModified = Instant.parse("2026-05-12T12:00:00Z").minusSeconds(ageHours * 3600),
        ageHours = ageHours,
        sizeBytes = 1_024L,
    )

    private fun deletedCount(): Double = meterRegistry.counter("plugwerk.storage.reaper.deleted").count()

    private fun skippedCount(): Double = meterRegistry.counter("plugwerk.storage.reaper.skipped").count()
}
