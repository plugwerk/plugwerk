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
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.Optional
import kotlin.test.assertFailsWith

@ExtendWith(MockitoExtension::class)
class SchedulerJobAdminServiceBranchCoverageTest {

    @Mock lateinit var registry: SchedulerJobRegistry

    @Mock lateinit var repository: SchedulerJobRepository

    @Mock lateinit var service: SchedulerJobService

    @InjectMocks
    lateinit var adminService: SchedulerJobAdminService

    private var executorCalled = false

    private fun descriptor(
        name: String = "reaper",
        supportsDryRun: Boolean = true,
        executor: () -> Unit = { executorCalled = true },
    ) = SchedulerJobDescriptor(
        name = name,
        description = "desc",
        cronExpression = "0 0 * * * *",
        supportsDryRun = supportsDryRun,
        runNowExecutor = executor,
    )

    // -- listJobs ------------------------------------------------------------

    @Test
    fun `listJobs uses the seeded row when present`() {
        val seeded = SchedulerJobEntity(name = "reaper", enabled = false)
        whenever(repository.findAll()).thenReturn(listOf(seeded))
        whenever(registry.all()).thenReturn(listOf(descriptor("reaper")))

        val views = adminService.listJobs()

        assertThat(views).hasSize(1)
        // The row from the repository (enabled=false) is reused, not a default.
        assertThat(views.first().state).isSameAs(seeded)
        assertThat(views.first().state.enabled).isFalse()
    }

    @Test
    fun `listJobs falls back to a transient default when the row is missing`() {
        // No matching row → elvis-default branch on the right-hand side.
        whenever(repository.findAll()).thenReturn(emptyList())
        whenever(registry.all()).thenReturn(listOf(descriptor("orphaned")))

        val views = adminService.listJobs()

        assertThat(views).hasSize(1)
        assertThat(views.first().state.name).isEqualTo("orphaned")
        // Default entity is enabled=true.
        assertThat(views.first().state.enabled).isTrue()
    }

    // -- getJob --------------------------------------------------------------

    @Test
    fun `getJob throws when the descriptor is unknown`() {
        whenever(registry.find("ghost")).thenReturn(null)

        assertFailsWith<EntityNotFoundException> { adminService.getJob("ghost") }
        verify(repository, never()).findById(any())
    }

    @Test
    fun `getJob returns the persisted state when the row exists`() {
        val row = SchedulerJobEntity(name = "reaper", runCountTotal = 7)
        whenever(registry.find("reaper")).thenReturn(descriptor("reaper"))
        whenever(repository.findById("reaper")).thenReturn(Optional.of(row))

        val view = adminService.getJob("reaper")

        assertThat(view.state).isSameAs(row)
        assertThat(view.state.runCountTotal).isEqualTo(7)
    }

    @Test
    fun `getJob falls back to a transient default when the row is absent`() {
        whenever(registry.find("reaper")).thenReturn(descriptor("reaper"))
        whenever(repository.findById("reaper")).thenReturn(Optional.empty())

        val view = adminService.getJob("reaper")

        assertThat(view.state.name).isEqualTo("reaper")
        assertThat(view.state.runCountTotal).isEqualTo(0)
    }

    // -- update --------------------------------------------------------------

    @Test
    fun `update throws when the descriptor is unknown`() {
        whenever(registry.find("ghost")).thenReturn(null)

        assertFailsWith<EntityNotFoundException> { adminService.update("ghost", enabled = true, dryRun = null) }
        verify(repository, never()).save(any())
    }

    @Test
    fun `update applies enabled and dryRun when the job supports dry-run`() {
        val row = SchedulerJobEntity(name = "reaper", enabled = true, dryRun = null)
        whenever(registry.find("reaper")).thenReturn(descriptor("reaper", supportsDryRun = true))
        whenever(repository.findById("reaper")).thenReturn(Optional.of(row))
        whenever(repository.save(any<SchedulerJobEntity>())).thenAnswer { it.arguments[0] }

        val view = adminService.update("reaper", enabled = false, dryRun = true)

        // enabled?.let applied; supportsDryRun=true so dryRun kept verbatim.
        assertThat(view.state.enabled).isFalse()
        assertThat(view.state.dryRun).isTrue()
        verify(service).invalidate("reaper")
    }

    @Test
    fun `update clears dryRun to null when the job does not support dry-run and warns`() {
        val row = SchedulerJobEntity(name = "plain", enabled = false, dryRun = true)
        whenever(registry.find("plain")).thenReturn(descriptor("plain", supportsDryRun = false))
        whenever(repository.findById("plain")).thenReturn(Optional.of(row))
        whenever(repository.save(any<SchedulerJobEntity>())).thenAnswer { it.arguments[0] }

        // dryRun != null && !supportsDryRun → warn branch, then forced to null.
        val view = adminService.update("plain", enabled = true, dryRun = true)

        assertThat(view.state.enabled).isTrue()
        assertThat(view.state.dryRun).isNull()
    }

    @Test
    fun `update leaves enabled untouched when enabled is null and creates a row when absent`() {
        // findById empty → orElseGet creates a fresh SchedulerJobEntity (default enabled=true).
        whenever(registry.find("reaper")).thenReturn(descriptor("reaper", supportsDryRun = true))
        whenever(repository.findById("reaper")).thenReturn(Optional.empty())
        whenever(repository.save(any<SchedulerJobEntity>())).thenAnswer { it.arguments[0] }

        // enabled = null → the enabled?.let arm is skipped, default stays true.
        val view = adminService.update("reaper", enabled = null, dryRun = null)

        assertThat(view.state.enabled).isTrue()
        assertThat(view.state.dryRun).isNull()
    }

    // -- runNow --------------------------------------------------------------

    @Test
    fun `runNow throws when the descriptor is unknown`() {
        whenever(registry.find("ghost")).thenReturn(null)

        assertFailsWith<EntityNotFoundException> { adminService.runNow("ghost") }
    }

    @Test
    fun `runNow reads the canonical outcome back when the refreshed row is present`() {
        executorCalled = false
        val refreshed = SchedulerJobEntity(name = "reaper").apply {
            lastRunOutcome = SchedulerJobOutcome.SUCCESS
            lastRunDurationMs = 1234L
            lastRunMessage = "all good"
        }
        whenever(registry.find("reaper")).thenReturn(descriptor("reaper"))
        whenever(repository.findById("reaper")).thenReturn(Optional.of(refreshed))

        val outcome = adminService.runNow("reaper")

        assertThat(executorCalled).isTrue()
        assertThat(outcome.outcome).isEqualTo(SchedulerJobOutcome.SUCCESS)
        assertThat(outcome.durationMs).isEqualTo(1234L)
        assertThat(outcome.message).isEqualTo("all good")
    }

    @Test
    fun `runNow falls back to defaults when the refreshed row carries nulls`() {
        executorCalled = false
        // Row present but lastRun* all null → exercises the elvis / Optional.ofNullable
        // fallback arms (SUCCESS default, measured duration, null message).
        val refreshed = SchedulerJobEntity(name = "reaper")
        whenever(registry.find("reaper")).thenReturn(descriptor("reaper"))
        whenever(repository.findById("reaper")).thenReturn(Optional.of(refreshed))

        val outcome = adminService.runNow("reaper")

        assertThat(outcome.outcome).isEqualTo(SchedulerJobOutcome.SUCCESS)
        assertThat(outcome.durationMs).isNotNull()
        assertThat(outcome.message).isNull()
    }

    @Test
    fun `runNow falls back to measured duration when the refreshed row is absent`() {
        executorCalled = false
        // findById empty → every refreshed.map/flatMap takes the orElse arm.
        whenever(registry.find("reaper")).thenReturn(descriptor("reaper"))
        whenever(repository.findById("reaper")).thenReturn(Optional.empty())

        val outcome = adminService.runNow("reaper")

        assertThat(outcome.outcome).isEqualTo(SchedulerJobOutcome.SUCCESS)
        assertThat(outcome.durationMs).isNotNull()
        assertThat(outcome.message).isNull()
    }

    @Test
    fun `runNow reports FAILED with truncated message when the executor throws`() {
        val longMessage = "x".repeat(3000)
        whenever(registry.find("reaper"))
            .thenReturn(descriptor("reaper", executor = { throw IllegalStateException(longMessage) }))

        val outcome = adminService.runNow("reaper")

        assertThat(outcome.outcome).isEqualTo(SchedulerJobOutcome.FAILED)
        assertThat(outcome.durationMs).isNotNull()
        // ex.message?.take(2000) → capped at 2000 chars.
        assertThat(outcome.message).hasSize(2000)
        verify(repository, never()).findById(eq("reaper"))
    }

    @Test
    fun `runNow reports FAILED with null message when the exception has none`() {
        whenever(registry.find("reaper"))
            .thenReturn(descriptor("reaper", executor = { throw RuntimeException() }))

        val outcome = adminService.runNow("reaper")

        assertThat(outcome.outcome).isEqualTo(SchedulerJobOutcome.FAILED)
        // ex.message is null → take(2000) on null short-circuits to null.
        assertThat(outcome.message).isNull()
    }

    @Test
    fun `runNow surfaces a repository failure during the success readback as FAILED`() {
        // The executor succeeds, but the canonical readback throws → caught by
        // the outer try/catch, mapped to FAILED. Distinct from executor failure.
        whenever(registry.find("reaper")).thenReturn(descriptor("reaper"))
        doThrow(RuntimeException("db down")).whenever(repository).findById("reaper")

        val outcome = adminService.runNow("reaper")

        assertThat(outcome.outcome).isEqualTo(SchedulerJobOutcome.FAILED)
        assertThat(outcome.message).isEqualTo("db down")
    }
}
