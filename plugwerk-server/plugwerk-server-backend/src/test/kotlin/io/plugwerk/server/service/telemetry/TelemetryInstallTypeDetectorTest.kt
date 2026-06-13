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
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Unit tests for install-type resolution (DEV-23 / ADR-0038). The env-var and
 * docker-marker probes are stubbed so the four-step resolution order can be
 * exercised deterministically without touching the real environment.
 */
class TelemetryInstallTypeDetectorTest {

    private fun detector(
        installTypeOverride: String? = null,
        env: Map<String, String> = emptyMap(),
        dockerMarker: Boolean = false,
    ): TelemetryInstallTypeDetector {
        val properties = PlugwerkProperties(
            telemetry = PlugwerkProperties.TelemetryProperties(installType = installTypeOverride),
        )
        return TelemetryInstallTypeDetector(properties).apply {
            envLookup = { env[it] }
            dockerEnvMarkerPresent = { dockerMarker }
        }
    }

    @Test
    fun `operator override wins over auto-detection`() {
        val detector = detector(
            installTypeOverride = "jar",
            env = mapOf(TelemetryInstallTypeDetector.KUBERNETES_SERVICE_HOST_ENV to "10.0.0.1"),
            dockerMarker = true,
        )
        assertThat(detector.detect()).isEqualTo(InstallType.JAR)
    }

    @Test
    fun `override is case-insensitive and trimmed`() {
        assertThat(detector(installTypeOverride = "  K8S ").detect()).isEqualTo(InstallType.K8S)
    }

    @Test
    fun `unrecognized override resolves to unknown rather than guessing`() {
        assertThat(detector(installTypeOverride = "heroku").detect()).isEqualTo(InstallType.UNKNOWN)
    }

    @Test
    fun `detects kubernetes from the service-host env var`() {
        val detector = detector(
            env = mapOf(TelemetryInstallTypeDetector.KUBERNETES_SERVICE_HOST_ENV to "10.0.0.1"),
            dockerMarker = true,
        )
        assertThat(detector.detect()).isEqualTo(InstallType.K8S)
    }

    @Test
    fun `detects docker-compose from the dockerenv marker when not on kubernetes`() {
        assertThat(detector(dockerMarker = true).detect()).isEqualTo(InstallType.DOCKER_COMPOSE)
    }

    @Test
    fun `falls back to jar with no signals`() {
        assertThat(detector().detect()).isEqualTo(InstallType.JAR)
    }

    @Test
    fun `blank kubernetes env var is not treated as a kubernetes signal`() {
        val detector = detector(
            env = mapOf(TelemetryInstallTypeDetector.KUBERNETES_SERVICE_HOST_ENV to "  "),
        )
        assertThat(detector.detect()).isEqualTo(InstallType.JAR)
    }
}
