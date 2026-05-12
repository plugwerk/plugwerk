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

import io.plugwerk.server.SharedPostgresContainer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.time.Instant

/**
 * End-to-end wiring test for the orphan reaper (#496) against a real
 * PostgreSQL Testcontainer.
 *
 * What it proves:
 *  - Liquibase migration `0033_shedlock` creates the `shedlock` table
 *    in the production-equivalent schema (the same migration set runs
 *    here as on a real deploy).
 *  - `SchedulerLockConfig` wires a real `JdbcTemplateLockProvider`
 *    against that table (the `NoOpLockProvider` fallback would not
 *    write to `shedlock`).
 *  - `OrphanReaperScheduler` boots as a Spring bean with its
 *    `@SchedulerLock("orphan-storage-reaper")` advice applied — when
 *    we invoke `reap()` through the proxy the ShedLock advice acquires
 *    a row in `shedlock` named `orphan-storage-reaper`.
 *  - The reaper delegates eligible orphans to
 *    `deleteOrphanedArtifacts` and respects the grace-period filter
 *    end-to-end.
 *
 * We deliberately do NOT spin up two Spring contexts to assert
 * mutual-exclusion: that contract is owned by ShedLock itself and is
 * extensively tested upstream. Re-proving it here would amount to a
 * library smoke test, while the wiring above is the part that is ours
 * to break.
 */
@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(
    properties = [
        "plugwerk.scheduler.shedlock.enabled=true",
        "plugwerk.storage.reaper.enabled=true",
        "plugwerk.storage.reaper.dry-run=false",
        "plugwerk.storage.reaper.grace-period-hours=24",
    ],
)
@Tag("integration")
class OrphanReaperShedLockIT {

    companion object {
        @DynamicPropertySource
        @JvmStatic
        fun configureProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { SharedPostgresContainer.instance.jdbcUrl }
            registry.add("spring.datasource.username") { SharedPostgresContainer.instance.username }
            registry.add("spring.datasource.password") { SharedPostgresContainer.instance.password }
        }
    }

    @Autowired
    private lateinit var reaper: OrphanReaperScheduler

    @Autowired
    private lateinit var jdbc: JdbcTemplate

    @MockitoBean
    private lateinit var consistencyService: StorageConsistencyService

    @MockitoBean
    private lateinit var adminService: StorageConsistencyAdminService

    @Test
    fun `reaper boots wired with real ShedLock and delegates eligible orphans`() {
        val now = Instant.now()
        whenever(consistencyService.scan()).thenReturn(
            ConsistencyReport(
                missingArtifacts = emptyList(),
                orphanedArtifacts = listOf(
                    OrphanedArtifact(
                        key = "acme:eligible:1.0.0:jar",
                        lastModified = now.minusSeconds(48L * 3600),
                        ageHours = 48L,
                        sizeBytes = 1_024L,
                    ),
                    OrphanedArtifact(
                        key = "acme:fresh:1.0.0:jar",
                        lastModified = now.minusSeconds(3600L),
                        ageHours = 1L, // inside grace period — must be filtered out
                        sizeBytes = 512L,
                    ),
                ),
                scannedAt = now,
                totalDbRows = 0,
                totalStorageObjects = 2,
            ),
        )
        whenever(adminService.deleteOrphanedArtifacts(any())).thenReturn(
            BulkArtifactDeletionResult(
                deleted = listOf("acme:eligible:1.0.0:jar"),
                skipped = emptyList(),
            ),
        )

        reaper.reap()

        // The proxy-wrapped reap() should have asked ShedLock for a lock —
        // proven by the row in shedlock with our scheduler name.
        val lockRows = jdbc.queryForList(
            "SELECT name FROM shedlock WHERE name = ?",
            "orphan-storage-reaper",
        )
        assertThat(lockRows)
            .withFailMessage(
                "Expected ShedLock to have recorded a lock for 'orphan-storage-reaper' — " +
                    "either the @SchedulerLock advice did not fire or the JdbcTemplateLockProvider " +
                    "is not wired against the production shedlock table.",
            )
            .isNotEmpty

        // The body ran with only the eligible (>=24h) orphan; the fresh
        // one was filtered out by the grace period.
        verify(adminService).deleteOrphanedArtifacts(listOf("acme:eligible:1.0.0:jar"))
    }
}
