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

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

/**
 * Fail-open integration check (DEV-23 / ADR-0038, acceptance criterion 3).
 *
 * Telemetry is **enabled** and pointed at a deliberately broken HTTPS endpoint
 * (a closed local port). The first-start beacon fires on `ApplicationReadyEvent`
 * and the heartbeat job is scheduled — but because every send is wrapped
 * fail-open with short timeouts, the unreachable endpoint must not affect
 * startup or `/actuator/health`. If telemetry could break the app, this context
 * would fail to start or health would be non-200.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(
    properties = [
        "spring.datasource.url=jdbc:h2:mem:telemetry-failopen;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "plugwerk.telemetry.enabled=true",
        // Port 1 is never listening — connect fails fast and is swallowed.
        "plugwerk.telemetry.endpoint=https://127.0.0.1:1/telemetry",
    ],
)
class TelemetryBeaconIT {

    @Autowired private lateinit var mockMvc: MockMvc

    @Test
    fun `health stays green with telemetry enabled against a broken endpoint`() {
        mockMvc.get("/actuator/health")
            .andExpect { status { isOk() } }
    }
}
