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

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import tools.jackson.databind.ObjectMapper

/**
 * Contract tests for the telemetry payload allowlist (DEV-23 / ADR-0038).
 *
 * The single invariant that must never regress: a serialized payload carries
 * **exactly** `installId`, `version`, `installType`, `event` — no PII field can
 * sneak in because the [TelemetryPayload] data class has nowhere to put one.
 */
class TelemetryPayloadBuilderTest {

    private val objectMapper = ObjectMapper()

    @Test
    fun `builds payload with exactly the allowlisted fields`() {
        val payload = TelemetryPayloadBuilder.build(
            installId = "11111111-2222-4333-8444-555555555555",
            version = "1.1.0-SNAPSHOT",
            installType = InstallType.DOCKER_COMPOSE,
            event = TelemetryEvent.SERVER_START,
        )

        assertThat(payload.installId).isEqualTo("11111111-2222-4333-8444-555555555555")
        assertThat(payload.version).isEqualTo("1.1.0-SNAPSHOT")
        assertThat(payload.installType).isEqualTo("docker-compose")
        assertThat(payload.event).isEqualTo("server_start")
    }

    @Test
    fun `serialized JSON contains only the four allowlisted keys`() {
        val payload = TelemetryPayloadBuilder.build(
            installId = "id",
            version = "v",
            installType = InstallType.K8S,
            event = TelemetryEvent.HEARTBEAT,
        )

        @Suppress("UNCHECKED_CAST")
        val asMap = objectMapper.readValue(objectMapper.writeValueAsString(payload), Map::class.java)
            as Map<String, Any?>

        assertThat(asMap.keys).containsExactlyInAnyOrder("installId", "version", "installType", "event")
        assertThat(asMap["installType"]).isEqualTo("k8s")
        assertThat(asMap["event"]).isEqualTo("heartbeat")
    }

    @Test
    fun `event and install type serialize to their wire values, never enum names`() {
        assertThat(TelemetryEvent.SERVER_START.wireValue).isEqualTo("server_start")
        assertThat(TelemetryEvent.HEARTBEAT.wireValue).isEqualTo("heartbeat")
        assertThat(InstallType.DOCKER_COMPOSE.wireValue).isEqualTo("docker-compose")
        assertThat(InstallType.JAR.wireValue).isEqualTo("jar")
        assertThat(InstallType.K8S.wireValue).isEqualTo("k8s")
        assertThat(InstallType.UNKNOWN.wireValue).isEqualTo("unknown")
    }

    // --- DEV-24: activation events reuse the same allowlisted payload ---

    @Test
    fun `activation events serialize to their wire values`() {
        assertThat(TelemetryEvent.NAMESPACE_CREATED.wireValue).isEqualTo("namespace_created")
        assertThat(TelemetryEvent.FIRST_PLUGIN_PUBLISH.wireValue).isEqualTo("first_plugin_publish")
    }

    @Test
    fun `activation event payloads carry exactly the four allowlisted keys and no PII`() {
        for (event in listOf(TelemetryEvent.NAMESPACE_CREATED, TelemetryEvent.FIRST_PLUGIN_PUBLISH)) {
            val payload = TelemetryPayloadBuilder.build(
                installId = "11111111-2222-4333-8444-555555555555",
                version = "1.1.0",
                installType = InstallType.DOCKER_COMPOSE,
                event = event,
            )

            @Suppress("UNCHECKED_CAST")
            val asMap = objectMapper.readValue(objectMapper.writeValueAsString(payload), Map::class.java)
                as Map<String, Any?>

            // Same hard allowlist as the P0 beacon: no place for a namespace name,
            // plugin name, user id, count, or free text to ride along.
            assertThat(asMap.keys).containsExactlyInAnyOrder("installId", "version", "installType", "event")
            assertThat(asMap["event"]).isEqualTo(event.wireValue)
        }
    }
}
