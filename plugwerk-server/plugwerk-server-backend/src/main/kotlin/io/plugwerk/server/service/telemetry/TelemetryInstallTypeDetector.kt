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
import org.springframework.stereotype.Component
import java.nio.file.Files
import java.nio.file.Path

/**
 * Resolves how this installation is being run, for the telemetry payload's
 * `installType` field (DEV-23 / ADR-0039).
 *
 * Resolution order:
 *  1. **Operator override** — `PLUGWERK_INSTALL_TYPE` (bound to
 *     `plugwerk.telemetry.install-type`). A recognized value wins outright; an
 *     unrecognized non-blank value resolves to [InstallType.UNKNOWN] rather than
 *     guessing.
 *  2. **Kubernetes** — the `KUBERNETES_SERVICE_HOST` env var is injected into
 *     every pod by the kubelet, so its presence is a reliable k8s signal.
 *  3. **Docker Compose** — the `/.dockerenv` marker file is present in every
 *     Docker container. We report `docker-compose` because that is Plugwerk's
 *     supported container-deployment shape; a bare `docker run` is
 *     indistinguishable and lumped in here intentionally.
 *  4. **Plain JAR** — the fallback for a process started directly on a host.
 *
 * The env-var and marker-file lookups are injectable so the resolution logic can
 * be unit-tested without touching the real environment or filesystem.
 */
@Component
class TelemetryInstallTypeDetector(private val properties: PlugwerkProperties) {

    /** Env-var accessor. Overridable in tests; production reads the real environment. */
    internal var envLookup: (String) -> String? = { System.getenv(it) }

    /** Docker marker-file probe. Overridable in tests; production checks the real path. */
    internal var dockerEnvMarkerPresent: () -> Boolean = { Files.exists(Path.of(DOCKER_ENV_MARKER)) }

    fun detect(): InstallType {
        val override = properties.telemetry.installType?.trim()?.takeIf { it.isNotBlank() }
        if (override != null) {
            return InstallType.fromWireValue(override) ?: InstallType.UNKNOWN
        }
        if (!envLookup(KUBERNETES_SERVICE_HOST_ENV).isNullOrBlank()) return InstallType.K8S
        if (dockerEnvMarkerPresent()) return InstallType.DOCKER_COMPOSE
        return InstallType.JAR
    }

    companion object {
        const val KUBERNETES_SERVICE_HOST_ENV = "KUBERNETES_SERVICE_HOST"
        const val DOCKER_ENV_MARKER = "/.dockerenv"
    }
}
