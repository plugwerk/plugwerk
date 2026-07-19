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
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID

/**
 * Unit tests for the lazy, DB-backed install-id provider (DEV-23 / ADR-0039).
 */
class TelemetryInstallIdProviderTest {

    @Test
    fun `generates and persists a UUID v4 when none is stored yet`() {
        val settings = mock<ApplicationSettingsService>()
        whenever(settings.getRaw(ApplicationSettingKey.TELEMETRY_INSTALL_ID)).thenReturn("")
        val provider = TelemetryInstallIdProvider(settings)

        val id = provider.getOrCreate()

        // Returned value is a syntactically valid UUID (v4 from UUID.randomUUID()).
        assertThat(UUID.fromString(id).version()).isEqualTo(4)
        // ...and it was persisted back through the settings store, marked machine-written.
        verify(settings).update(
            eq(ApplicationSettingKey.TELEMETRY_INSTALL_ID),
            eq(id),
            eq(TelemetryInstallIdProvider.UPDATED_BY),
        )
    }

    @Test
    fun `returns the stored id without regenerating when one already exists`() {
        val stored = "stored-install-id"
        val settings = mock<ApplicationSettingsService>()
        whenever(settings.getRaw(ApplicationSettingKey.TELEMETRY_INSTALL_ID)).thenReturn(stored)
        val provider = TelemetryInstallIdProvider(settings)

        val id = provider.getOrCreate()

        assertThat(id).isEqualTo(stored)
        verify(settings, never()).update(any(), any(), any())
    }
}
