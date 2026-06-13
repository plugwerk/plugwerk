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

/**
 * The lifecycle event a [TelemetryPayload] reports (DEV-23 / ADR-0038).
 *
 * Only the [wireValue] is serialized; the enum constant name never leaves the
 * process.
 */
enum class TelemetryEvent(val wireValue: String) {
    /** Emitted once per process on `ApplicationReadyEvent`. */
    SERVER_START("server_start"),

    /** Emitted ~once per 24h by the scheduled heartbeat job. */
    HEARTBEAT("heartbeat"),
}

/**
 * The complete, zero-PII telemetry payload (DEV-23 / ADR-0038).
 *
 * This data class **is** the allowlist: Jackson serializes exactly these four
 * camelCase fields and nothing else. There is deliberately no place to attach a
 * hostname, IP, namespace, user, or any other identifying data — privacy is
 * enforced by construction, not by a runtime filter. The only test that has to
 * stay green forever is "the serialized payload has exactly these keys".
 *
 * @property installId Random UUID v4 generated once per installation and
 *   persisted locally (see
 *   [io.plugwerk.server.service.telemetry.TelemetryInstallIdProvider]). Synthetic
 *   and unlinkable to any real-world identity.
 * @property version The running Plugwerk version (build info / `VERSION`).
 * @property installType One of the [InstallType.wireValue]s.
 * @property event One of the [TelemetryEvent.wireValue]s.
 */
data class TelemetryPayload(val installId: String, val version: String, val installType: String, val event: String)

/**
 * Pure builder for [TelemetryPayload]. Kept as a free function with no Spring
 * wiring so the allowlist contract can be unit-tested in isolation, and so the
 * orchestrating [io.plugwerk.server.service.telemetry.TelemetryBeacon] stays the
 * single place that resolves the (potentially side-effecting) install id.
 */
object TelemetryPayloadBuilder {
    fun build(installId: String, version: String, installType: InstallType, event: TelemetryEvent): TelemetryPayload =
        TelemetryPayload(
            installId = installId,
            version = version,
            installType = installType.wireValue,
            event = event.wireValue,
        )
}
