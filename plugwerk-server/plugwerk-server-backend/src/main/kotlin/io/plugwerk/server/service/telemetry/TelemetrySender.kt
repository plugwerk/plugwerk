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
 * Transport seam for the telemetry beacon (DEV-23 / ADR-0038).
 *
 * Pulled out as an interface for one reason above all: the fail-open acceptance
 * criterion requires a test that injects a *failing* sender and proves the
 * exception never reaches startup/health. Production wires [HttpTelemetrySender];
 * tests wire a throwing or recording fake.
 *
 * Implementations may throw freely — the caller ([TelemetryBeacon]) wraps every
 * call so any failure is swallowed and logged at debug.
 */
fun interface TelemetrySender {
    fun send(payload: TelemetryPayload)
}
