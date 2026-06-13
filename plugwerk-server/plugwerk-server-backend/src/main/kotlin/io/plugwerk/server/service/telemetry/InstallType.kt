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
 * How this Plugwerk installation is being run, as reported by the opt-out
 * telemetry beacon (DEV-23 / ADR-0038).
 *
 * The [wireValue] is the only form that ever leaves the process — the payload
 * carries the lowercase, hyphenated string, never the enum constant name. The
 * closed set is part of the zero-PII allowlist: it tells Plugwerk *how* the
 * software is deployed, never *where* or *by whom*.
 */
enum class InstallType(val wireValue: String) {
    DOCKER_COMPOSE("docker-compose"),
    JAR("jar"),
    K8S("k8s"),
    UNKNOWN("unknown"),
    ;

    companion object {
        /**
         * Resolves a [wireValue] (case-insensitive, trimmed) back to its enum
         * constant, or `null` if it is not one of the known install types. Used
         * to validate the `PLUGWERK_INSTALL_TYPE` operator override.
         */
        fun fromWireValue(value: String): InstallType? {
            val needle = value.trim()
            return entries.firstOrNull { it.wireValue.equals(needle, ignoreCase = true) }
        }
    }
}
