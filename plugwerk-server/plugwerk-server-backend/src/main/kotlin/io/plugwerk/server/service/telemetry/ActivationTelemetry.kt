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

import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager

/**
 * Emits the P1 server-side activation events (DEV-24): `namespace_created` and
 * `first_plugin_publish`. A thin seam over [TelemetryBeacon] that owns one extra
 * concern the beacon deliberately does not: **transaction awareness**.
 *
 * The activation events are raised from inside a business transaction (namespace
 * create, plugin publish). Two rules follow:
 *
 *  - **Fire only on commit.** The send is deferred to `afterCommit` so a
 *    rolled-back create/publish never produces a telemetry event for work that
 *    did not actually happen. This also keeps the `first_plugin_publish` gate
 *    honest: the `namespace.first_published_at` flag and the emitted event share
 *    the same commit, so the event fires exactly when the flag is durably set.
 *    (The gate itself — flipping the flag atomically only on the first publish —
 *    lives in the calling service via
 *    [io.plugwerk.server.repository.NamespaceRepository.markFirstPublishedIfAbsent];
 *    this class only carries the "if the gate opened, announce it" half.)
 *
 *  - **Never block the caller.** The actual send goes through
 *    [TelemetryBeacon.emitAsync], off the request thread, so neither the
 *    `afterCommit` callback nor a no-transaction direct call stalls on the
 *    telemetry HTTP timeout.
 *
 * The opt-out gate and fail-open guarantee are inherited unchanged from
 * [TelemetryBeacon.emit]; this class adds no new way for telemetry to affect the
 * request it rides on.
 */
@Service
class ActivationTelemetry(private val beacon: TelemetryBeacon) {

    /** Records a namespace creation. Call from within the create transaction. */
    fun namespaceCreated() = emitAfterCommit(TelemetryEvent.NAMESPACE_CREATED)

    /**
     * Records the first plugin publish in a namespace. Call only when the
     * first-publish gate actually opened (see
     * [io.plugwerk.server.repository.NamespaceRepository.markFirstPublishedIfAbsent]),
     * from within the publish transaction.
     */
    fun firstPluginPublished() = emitAfterCommit(TelemetryEvent.FIRST_PLUGIN_PUBLISH)

    /**
     * Defers [TelemetryBeacon.emitAsync] to after the current transaction
     * commits. When no transaction is active (defensive — the production callers
     * are always transactional) the event is dispatched immediately so the
     * behaviour degrades to "fire now" rather than "drop silently".
     */
    private fun emitAfterCommit(event: TelemetryEvent) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                object : TransactionSynchronization {
                    override fun afterCommit() = beacon.emitAsync(event)
                },
            )
        } else {
            beacon.emitAsync(event)
        }
    }
}
