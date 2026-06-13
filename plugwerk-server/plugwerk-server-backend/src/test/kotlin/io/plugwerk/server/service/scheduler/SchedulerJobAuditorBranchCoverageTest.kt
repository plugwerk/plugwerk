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
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.Optional
import kotlin.test.assertFailsWith

@ExtendWith(MockitoExtension::class)
class SchedulerJobAuditorBranchCoverageTest {

    @Mock lateinit var repository: SchedulerJobRepository

    @Mock lateinit var service: SchedulerJobService

    @InjectMocks
    lateinit var auditor: SchedulerJobAuditor

    private fun rowFor(name: String) = SchedulerJobEntity(name = name, runCountTotal = 5)

    // -- run -----------------------------------------------------------------

    @Test
    fun `run records the outcome the block returns`() {
        val row = rowFor("job")
        whenever(repository.findById("job")).thenReturn(Optional.of(row))
        whenever(repository.save(any<SchedulerJobEntity>())).thenAnswer { it.arguments[0] }

        val outcome = auditor.run("job") { SchedulerJobOutcome.SUCCESS }

        assertThat(outcome).isEqualTo(SchedulerJobOutcome.SUCCESS)
        assertThat(row.lastRunOutcome).isEqualTo(SchedulerJobOutcome.SUCCESS)
        assertThat(row.lastRunMessage).isNull()
        // SUCCESS is real work → total incremented.
        assertThat(row.runCountTotal).isEqualTo(6)
        assertThat(row.lastRunDurationMs).isNotNull()
        verify(service).invalidate("job")
    }

    @Test
    fun `run maps a thrown exception to FAILED and captures the message`() {
        val row = rowFor("job")
        whenever(repository.findById("job")).thenReturn(Optional.of(row))
        whenever(repository.save(any<SchedulerJobEntity>())).thenAnswer { it.arguments[0] }

        val outcome = auditor.run("job") { throw IllegalStateException("boom") }

        assertThat(outcome).isEqualTo(SchedulerJobOutcome.FAILED)
        assertThat(row.lastRunOutcome).isEqualTo(SchedulerJobOutcome.FAILED)
        assertThat(row.lastRunMessage).isEqualTo("boom")
        assertThat(row.runCountTotal).isEqualTo(6)
    }

    @Test
    fun `run uses the exception class name when the message is null`() {
        val row = rowFor("job")
        whenever(repository.findById("job")).thenReturn(Optional.of(row))
        whenever(repository.save(any<SchedulerJobEntity>())).thenAnswer { it.arguments[0] }

        // message == null → elvis falls back to javaClass.simpleName.
        val outcome = auditor.run("job") { throw RuntimeException() }

        assertThat(outcome).isEqualTo(SchedulerJobOutcome.FAILED)
        assertThat(row.lastRunMessage).isEqualTo("RuntimeException")
    }

    @Test
    fun `run truncates a very long exception message to 2000 chars`() {
        val row = rowFor("job")
        whenever(repository.findById("job")).thenReturn(Optional.of(row))
        whenever(repository.save(any<SchedulerJobEntity>())).thenAnswer { it.arguments[0] }

        auditor.run("job") { throw IllegalStateException("y".repeat(5000)) }

        assertThat(row.lastRunMessage).hasSize(2000)
    }

    // -- gateAndRun ----------------------------------------------------------

    @Test
    fun `gateAndRun skips and records SKIPPED_DISABLED when the toggle is off`() {
        whenever(service.shouldRun("job")).thenReturn(false)
        val row = rowFor("job")
        whenever(repository.findById("job")).thenReturn(Optional.of(row))
        whenever(repository.save(any<SchedulerJobEntity>())).thenAnswer { it.arguments[0] }
        var blockRan = false

        val outcome = auditor.gateAndRun("job") { blockRan = true }

        assertThat(outcome).isEqualTo(SchedulerJobOutcome.SKIPPED_DISABLED)
        assertThat(blockRan).isFalse()
        // SKIPPED outcomes do not increment the total.
        assertThat(row.runCountTotal).isEqualTo(5)
    }

    @Test
    fun `gateAndRun runs the block and reports SUCCESS when enabled`() {
        whenever(service.shouldRun("job")).thenReturn(true)
        val row = rowFor("job")
        whenever(repository.findById("job")).thenReturn(Optional.of(row))
        whenever(repository.save(any<SchedulerJobEntity>())).thenAnswer { it.arguments[0] }
        var blockRan = false

        val outcome = auditor.gateAndRun("job") { blockRan = true }

        assertThat(outcome).isEqualTo(SchedulerJobOutcome.SUCCESS)
        assertThat(blockRan).isTrue()
        assertThat(row.runCountTotal).isEqualTo(6)
    }

    // -- recordSkipped -------------------------------------------------------

    @Test
    fun `recordSkipped accepts SKIPPED_DISABLED`() {
        val row = rowFor("job")
        whenever(repository.findById("job")).thenReturn(Optional.of(row))
        whenever(repository.save(any<SchedulerJobEntity>())).thenAnswer { it.arguments[0] }

        auditor.recordSkipped("job", SchedulerJobOutcome.SKIPPED_DISABLED)

        assertThat(row.lastRunOutcome).isEqualTo(SchedulerJobOutcome.SKIPPED_DISABLED)
        assertThat(row.lastRunDurationMs).isNull()
        assertThat(row.runCountTotal).isEqualTo(5)
    }

    @Test
    fun `recordSkipped accepts SKIPPED_LOCK with a message`() {
        val row = rowFor("job")
        whenever(repository.findById("job")).thenReturn(Optional.of(row))
        whenever(repository.save(any<SchedulerJobEntity>())).thenAnswer { it.arguments[0] }

        auditor.recordSkipped("job", SchedulerJobOutcome.SKIPPED_LOCK, message = "peer holds lock")

        assertThat(row.lastRunOutcome).isEqualTo(SchedulerJobOutcome.SKIPPED_LOCK)
        assertThat(row.lastRunMessage).isEqualTo("peer holds lock")
        assertThat(row.runCountTotal).isEqualTo(5)
    }

    @Test
    fun `recordSkipped rejects a non-skip outcome via require`() {
        // require(...) fails before any persist happens.
        assertFailsWith<IllegalArgumentException> {
            auditor.recordSkipped("job", SchedulerJobOutcome.SUCCESS)
        }
        verify(repository, never()).save(any())
    }

    // -- persist (via run) ---------------------------------------------------

    @Test
    fun `persist is a fail-open no-op when the scheduler_job row is missing`() {
        // findById empty → orElse(null) → run{} returns early, no save.
        whenever(repository.findById("job")).thenReturn(Optional.empty())

        val outcome = auditor.run("job") { SchedulerJobOutcome.SUCCESS }

        assertThat(outcome).isEqualTo(SchedulerJobOutcome.SUCCESS)
        verify(repository, never()).save(any())
        // The early `return` inside persist's try block exits the method
        // before the post-catch `service.invalidate(name)`, so a missing
        // row does not bust the cache.
        verify(service, never()).invalidate(any())
    }

    @Test
    fun `persist swallows a repository save failure and still invalidates the cache`() {
        val row = rowFor("job")
        whenever(repository.findById("job")).thenReturn(Optional.of(row))
        doThrow(RuntimeException("save failed")).whenever(repository).save(any<SchedulerJobEntity>())

        // The audit-side failure must NOT propagate out of run().
        val outcome = auditor.run("job") { SchedulerJobOutcome.SUCCESS }

        assertThat(outcome).isEqualTo(SchedulerJobOutcome.SUCCESS)
        verify(service).invalidate("job")
    }

    @Test
    fun `persist records the captured entity fields on save`() {
        val row = rowFor("job")
        whenever(repository.findById("job")).thenReturn(Optional.of(row))
        val captor = argumentCaptor<SchedulerJobEntity>()
        whenever(repository.save(captor.capture())).thenAnswer { it.arguments[0] }

        auditor.run("job") { SchedulerJobOutcome.SUCCESS }

        val saved = captor.firstValue
        assertThat(saved.lastRunOutcome).isEqualTo(SchedulerJobOutcome.SUCCESS)
        assertThat(saved.lastRunAt).isNotNull()
    }
}
