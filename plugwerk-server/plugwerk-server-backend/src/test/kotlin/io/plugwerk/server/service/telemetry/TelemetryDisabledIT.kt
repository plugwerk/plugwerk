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
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource

/**
 * Opt-out integration check (DEV-23 / ADR-0038, acceptance criterion 1).
 *
 * With `PLUGWERK_TELEMETRY=false` (here as `plugwerk.telemetry.enabled=false`):
 *  - **No schedule** — the `@ConditionalOnProperty`-gated heartbeat scheduler
 *    bean is absent from the context entirely.
 *  - **No UUID** — the first-start beacon's in-code gate short-circuits before
 *    resolving the install id, so the `telemetry.install_id` setting stays blank.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(
    properties = [
        "spring.datasource.url=jdbc:h2:mem:telemetry-disabled;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "plugwerk.telemetry.enabled=false",
    ],
)
class TelemetryDisabledIT {

    @Autowired private lateinit var applicationContext: ApplicationContext

    @Autowired private lateinit var applicationSettingsService: ApplicationSettingsService

    @Test
    fun `no heartbeat schedule is registered when telemetry is disabled`() {
        assertThat(applicationContext.getBeanNamesForType(TelemetryHeartbeatScheduler::class.java))
            .describedAs("disabled telemetry must register no scheduled heartbeat bean")
            .isEmpty()
    }

    @Test
    fun `no install UUID is generated when telemetry is disabled`() {
        assertThat(applicationSettingsService.getRaw(ApplicationSettingKey.TELEMETRY_INSTALL_ID))
            .describedAs("disabled telemetry must not generate or persist an install id")
            .isBlank()
    }
}
