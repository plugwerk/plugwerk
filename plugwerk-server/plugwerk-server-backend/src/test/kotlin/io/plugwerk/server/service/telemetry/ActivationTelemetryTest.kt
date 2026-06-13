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

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.springframework.transaction.support.TransactionSynchronizationManager

/**
 * Unit tests for the transaction-awareness [ActivationTelemetry] adds on top of
 * [TelemetryBeacon] (DEV-24): activation events must fire **only after the
 * owning transaction commits**, and must never fire on the calling thread.
 *
 * The opt-out gate and fail-open behaviour are owned and tested by
 * [TelemetryBeaconTest]; here we only assert the deferral/dispatch wiring.
 */
class ActivationTelemetryTest {

    private val beacon: TelemetryBeacon = mock()
    private val activationTelemetry = ActivationTelemetry(beacon)

    @AfterEach
    fun tearDown() {
        // Never leak the thread-local synchronization state between tests.
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization()
        }
    }

    @Test
    fun `namespaceCreated dispatches NAMESPACE_CREATED immediately when no transaction is active`() {
        activationTelemetry.namespaceCreated()

        verify(beacon).emitAsync(TelemetryEvent.NAMESPACE_CREATED)
    }

    @Test
    fun `firstPluginPublished dispatches FIRST_PLUGIN_PUBLISH immediately when no transaction is active`() {
        activationTelemetry.firstPluginPublished()

        verify(beacon).emitAsync(TelemetryEvent.FIRST_PLUGIN_PUBLISH)
    }

    @Test
    fun `event is deferred until the transaction commits when a transaction is active`() {
        TransactionSynchronizationManager.initSynchronization()

        activationTelemetry.namespaceCreated()

        // Nothing sent yet — the work is still in-flight.
        verify(beacon, never()).emitAsync(TelemetryEvent.NAMESPACE_CREATED)

        // Simulate the commit: drive the registered afterCommit callbacks.
        TransactionSynchronizationManager.getSynchronizations().forEach { it.afterCommit() }

        verify(beacon).emitAsync(TelemetryEvent.NAMESPACE_CREATED)
    }

    @Test
    fun `event is not sent when the transaction rolls back (afterCommit never runs)`() {
        TransactionSynchronizationManager.initSynchronization()

        activationTelemetry.firstPluginPublished()

        // A rollback means afterCommit is never invoked; the synchronization is
        // simply discarded. No telemetry must escape.
        TransactionSynchronizationManager.clearSynchronization()

        verify(beacon, never()).emitAsync(TelemetryEvent.FIRST_PLUGIN_PUBLISH)
    }
}
