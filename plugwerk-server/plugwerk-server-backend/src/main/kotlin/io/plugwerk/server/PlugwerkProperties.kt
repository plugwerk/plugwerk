/*
 * Plugwerk — Plugin Marketplace for the PF4J Ecosystem
 * Copyright (C) 2026 devtank42 GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package io.plugwerk.server

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Central configuration properties for the Plugwerk server, bound to the `plugwerk` prefix.
 *
 * All application-specific settings are grouped here. Each logical sub-section is represented
 * by a nested data class so consumers can depend on only the slice they need.
 *
 * Registered via [@EnableConfigurationProperties][org.springframework.boot.context.properties.EnableConfigurationProperties]
 * on [PlugwerkApplication].
 *
 * @property storage Artifact storage configuration (type selection and backend-specific settings).
 * @property server Server-level settings (e.g. the externally reachable base URL).
 */
@ConfigurationProperties(prefix = "plugwerk")
data class PlugwerkProperties(
    val storage: StorageProperties = StorageProperties(),
    val server: ServerProperties = ServerProperties(),
) {
    /**
     * Artifact storage configuration (`plugwerk.storage.*`).
     *
     * @property type Storage backend type. Supported values: `fs` (filesystem, default).
     *   Future values: `s3`.
     * @property fs Filesystem-specific settings, used when [type] is `fs`.
     */
    data class StorageProperties(val type: String = "fs", val fs: FsProperties = FsProperties()) {
        /**
         * Filesystem storage settings (`plugwerk.storage.fs.*`).
         *
         * @property root Absolute path to the directory where plugin artefacts are stored.
         */
        data class FsProperties(val root: String = "/var/plugwerk/artifacts")
    }

    /**
     * Server-level settings (`plugwerk.server.*`).
     *
     * @property baseUrl Externally reachable base URL of this server instance
     *   (e.g. `https://plugins.example.com`). Used to build absolute download URLs.
     */
    data class ServerProperties(val baseUrl: String = "http://localhost:8080")
}
