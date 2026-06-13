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
import io.plugwerk.server.service.VersionProvider
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Orchestrates the opt-out telemetry beacon (DEV-23 / ADR-0038): builds the
 * zero-PII payload and hands it to the [TelemetrySender], gated by the global
 * opt-out and wrapped fail-open.
 *
 * Two design points carry the issue's hard constraints:
 *
 *  - **Opt-out gate.** [emit] returns immediately when `plugwerk.telemetry.enabled`
 *    is false — *before* resolving the install id or touching the sender. This is
 *    the single, unit-tested source of truth for "telemetry is off": a disabled
 *    beacon generates no UUID and makes no HTTP call. The guard is reached on
 *    every startup via [onApplicationReady], so it is live code, not a dead
 *    branch. (The daily heartbeat is additionally suppressed at the bean level —
 *    [TelemetryHeartbeatScheduler] is `@ConditionalOnProperty`-gated — so a
 *    disabled install also registers no schedule.)
 *
 *  - **Fail-open.** Every emit wraps payload-build + send in [runCatching]; any
 *    exception (timeout, DNS, 5xx, serialization, a settings-write blip while
 *    generating the install id) is swallowed and logged at debug. Telemetry can
 *    never affect startup, health, or readiness.
 *
 * The first-start beacon is dispatched on a dedicated single-thread executor so a
 * slow or hung endpoint cannot delay `ApplicationReadyEvent` propagation. The
 * heartbeat path runs on the scheduler thread (already off the request/startup
 * path) and calls [emit] directly. The activation events (DEV-24) reuse the same
 * executor via [emitAsync] so a request thread (namespace create / plugin
 * publish) is never blocked on the telemetry HTTP send or its timeout.
 */
@Component
class TelemetryBeacon(
    private val properties: PlugwerkProperties,
    private val installIdProvider: InstallIdProvider,
    private val versionProvider: VersionProvider,
    private val installTypeDetector: TelemetryInstallTypeDetector,
    private val sender: TelemetrySender,
) {
    private val log = LoggerFactory.getLogger(TelemetryBeacon::class.java)

    /**
     * Off-request/startup-thread dispatcher for the first-start beacon and the
     * activation events. Single-threaded (telemetry volume is tiny and ordering
     * is irrelevant) and daemon so it never blocks JVM exit.
     */
    private val dispatchExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "telemetry-dispatch").apply { isDaemon = true }
    }

    /**
     * Sends a single beacon for [event]. No-op when telemetry is disabled.
     * Synchronous and fail-open: returns normally whether the send succeeded,
     * was skipped (no/insecure endpoint), or threw.
     */
    fun emit(event: TelemetryEvent) {
        if (!properties.telemetry.enabled) return
        runCatching {
            val payload = TelemetryPayloadBuilder.build(
                installId = installIdProvider.getOrCreate(),
                version = versionProvider.getVersion(),
                installType = installTypeDetector.detect(),
                event = event,
            )
            sender.send(payload)
        }.onFailure { ex ->
            log.debug("telemetry {} beacon failed (ignored, fail-open): {}", event.wireValue, ex.message)
        }
    }

    /**
     * Fire-and-forget variant of [emit] for callers on a latency-sensitive thread
     * (request handlers emitting activation events, DEV-24). Submits the [emit]
     * to the off-thread [dispatchExecutor] and returns immediately. Fail-open: a
     * rejected submission (executor shut down during app teardown) is swallowed
     * and logged at debug, exactly like a failed send.
     */
    fun emitAsync(event: TelemetryEvent) {
        runCatching { dispatchExecutor.execute { emit(event) } }
            .onFailure { ex ->
                log.debug("telemetry {} async dispatch rejected (ignored, fail-open): {}", event.wireValue, ex.message)
            }
    }

    /**
     * First-start beacon. Fired once when the application is ready; dispatched off
     * the event thread so the send (or its timeout) never delays startup.
     */
    @EventListener(ApplicationReadyEvent::class)
    fun onApplicationReady() {
        dispatchExecutor.execute { emit(TelemetryEvent.SERVER_START) }
    }

    @PreDestroy
    fun shutdown() {
        dispatchExecutor.shutdown()
        runCatching { dispatchExecutor.awaitTermination(2, TimeUnit.SECONDS) }
    }
}
