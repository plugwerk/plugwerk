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
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.Test

/**
 * Unit tests for the two hard constraints the beacon owns (DEV-23 / ADR-0038):
 * the **opt-out gate** (disabled ⇒ no UUID, no send) and **fail-open** (a
 * throwing sender never propagates). Hand-written fakes keep the seams explicit.
 */
class TelemetryBeaconTest {

    /** Records how often the install id was resolved — i.e. whether a UUID was generated. */
    private class RecordingInstallIdProvider(private val id: String = "fixed-install-id") : InstallIdProvider {
        var calls: Int = 0
            private set

        override fun getOrCreate(): String {
            calls++
            return id
        }
    }

    private class RecordingSender : TelemetrySender {
        val sent: MutableList<TelemetryPayload> = mutableListOf()

        override fun send(payload: TelemetryPayload) {
            sent += payload
        }
    }

    private class ThrowingSender : TelemetrySender {
        override fun send(payload: TelemetryPayload): Nothing =
            throw RuntimeException("simulated telemetry endpoint failure")
    }

    private fun detector(): TelemetryInstallTypeDetector = TelemetryInstallTypeDetector(PlugwerkProperties()).apply {
        envLookup = { null }
        dockerEnvMarkerPresent = { false }
    }

    private fun beacon(
        enabled: Boolean,
        installIdProvider: InstallIdProvider,
        sender: TelemetrySender,
    ): TelemetryBeacon = TelemetryBeacon(
        properties = PlugwerkProperties(
            telemetry = PlugwerkProperties.TelemetryProperties(enabled = enabled),
        ),
        installIdProvider = installIdProvider,
        versionProvider = VersionProvider(null),
        installTypeDetector = detector(),
        sender = sender,
    )

    @Test
    fun `opt-out disables everything — no UUID generated, no send`() {
        val provider = RecordingInstallIdProvider()
        val sender = RecordingSender()
        val beacon = beacon(enabled = false, installIdProvider = provider, sender = sender)

        beacon.emit(TelemetryEvent.SERVER_START)
        beacon.emit(TelemetryEvent.HEARTBEAT)

        assertThat(provider.calls).describedAs("no install UUID resolved when disabled").isZero()
        assertThat(sender.sent).describedAs("no HTTP send when disabled").isEmpty()
    }

    @Test
    fun `enabled beacon sends the payload built from the resolved inputs`() {
        val provider = RecordingInstallIdProvider(id = "the-install-id")
        val sender = RecordingSender()
        val beacon = beacon(enabled = true, installIdProvider = provider, sender = sender)

        beacon.emit(TelemetryEvent.HEARTBEAT)

        assertThat(provider.calls).isEqualTo(1)
        assertThat(sender.sent).hasSize(1)
        val payload = sender.sent.single()
        assertThat(payload.installId).isEqualTo("the-install-id")
        assertThat(payload.installType).isEqualTo("jar")
        assertThat(payload.event).isEqualTo("heartbeat")
    }

    @Test
    fun `fail-open — a throwing sender never propagates`() {
        val provider = RecordingInstallIdProvider()
        val beacon = beacon(enabled = true, installIdProvider = provider, sender = ThrowingSender())

        assertThatCode { beacon.emit(TelemetryEvent.SERVER_START) }
            .describedAs("telemetry failure must be swallowed")
            .doesNotThrowAnyException()
        // The id was resolved (the failure was downstream, at the send), proving
        // the swallow happens around the whole emit, not by skipping the build.
        assertThat(provider.calls).isEqualTo(1)
    }
}
