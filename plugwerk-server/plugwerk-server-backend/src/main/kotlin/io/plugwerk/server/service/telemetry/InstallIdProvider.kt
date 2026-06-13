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

import io.plugwerk.server.service.settings.ApplicationSettingKey
import io.plugwerk.server.service.settings.ApplicationSettingsService
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Supplies the stable, anonymous install identifier used by the telemetry
 * beacon (DEV-23 / ADR-0038).
 *
 * Extracted as an interface so the orchestrating [TelemetryBeacon] can be
 * unit-tested with a recording fake — the opt-out test asserts that a disabled
 * beacon never calls [getOrCreate] (i.e. no UUID is generated when telemetry is
 * off).
 */
interface InstallIdProvider {
    /**
     * Returns the persisted install UUID, generating and storing one on first
     * call. Resolving this is the *only* place a UUID is ever created — callers
     * (the beacon) invoke it strictly after the opt-out gate, so an opted-out
     * installation never generates one.
     */
    fun getOrCreate(): String
}

/**
 * DB-backed [InstallIdProvider] that persists the install UUID via the existing
 * [ApplicationSettingsService] (no new persistence layer — DEV-23 reuse
 * mandate).
 *
 * Generation is **lazy**: the UUID is created and stored the first time a beacon
 * actually needs it, so toggling telemetry off before the first send means no
 * identifier is ever written. [getOrCreate] is `@Synchronized` so two concurrent
 * beacons on the same instance cannot both generate; the rare cross-instance
 * race on a brand-new cluster is harmless (analytics briefly sees two installs)
 * and is left to fail-open at the caller.
 */
@Component
class TelemetryInstallIdProvider(private val settings: ApplicationSettingsService) : InstallIdProvider {

    @Synchronized
    override fun getOrCreate(): String {
        val existing = settings.getRaw(ApplicationSettingKey.TELEMETRY_INSTALL_ID).trim()
        if (existing.isNotBlank()) return existing
        val generated = UUID.randomUUID().toString()
        settings.update(ApplicationSettingKey.TELEMETRY_INSTALL_ID, generated, updatedBy = UPDATED_BY)
        return generated
    }

    companion object {
        /** Audit `updated_by` marker so an operator can see the row was machine-written. */
        const val UPDATED_BY = "telemetry"
    }
}
