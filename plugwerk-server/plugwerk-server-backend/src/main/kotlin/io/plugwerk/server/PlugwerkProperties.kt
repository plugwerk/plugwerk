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
 * All Plugwerk-specific settings are grouped here. Each logical sub-section is represented
 * by a nested data class so that consumers can depend on only the slice they need.
 * Registered via `@EnableConfigurationProperties` on [PlugwerkApplication].
 *
 * Configuration is supplied through `application.yml` and can be overridden per environment
 * using environment variables (see each property for the corresponding variable name).
 *
 * @property storage Artifact storage configuration — selects the backend and provides
 *   backend-specific settings. See [StorageProperties].
 * @property server Server-level settings shared across services. See [ServerProperties].
 */
@ConfigurationProperties(prefix = "plugwerk")
data class PlugwerkProperties(
    val storage: StorageProperties = StorageProperties(),
    val server: ServerProperties = ServerProperties(),
    val auth: AuthProperties = AuthProperties(),
) {
    /**
     * Artifact storage configuration (`plugwerk.storage.*`).
     *
     * Controls which storage backend is used for plugin artefact files (JARs/ZIPs) and
     * provides the backend-specific settings. The active backend is selected by [type];
     * only the matching sub-section (e.g. [fs]) is used.
     *
     * @property type Selects the active storage backend.
     *   Supported values:
     *   - `fs` — local filesystem (default). Suitable for single-node deployments.
     *   - `s3` — S3-compatible object storage (planned, Phase 2). For multi-node or
     *     cloud deployments using AWS S3, MinIO, etc.
     *
     *   Environment variable: `PLUGWERK_STORAGE_TYPE`
     *
     *   ```yaml
     *   # Local dev / single-node:
     *   plugwerk.storage.type: fs
     *
     *   # Future: S3-compatible:
     *   plugwerk.storage.type: s3
     *   ```
     *
     * @property fs Settings for the filesystem backend. Only relevant when [type] is `fs`.
     */
    data class StorageProperties(val type: String = "fs", val fs: FsProperties = FsProperties()) {
        /**
         * Filesystem storage settings (`plugwerk.storage.fs.*`).
         *
         * Used when `plugwerk.storage.type=fs`. The directory is created automatically
         * on first use if it does not exist.
         *
         * @property root Absolute path to the root directory where plugin artefact files
         *   are stored. All artefact keys are resolved relative to this directory.
         *   Path traversal outside this root is rejected at runtime.
         *
         *   Environment variable: `PLUGWERK_STORAGE_ROOT`
         *
         *   ```yaml
         *   # Linux service installation:
         *   plugwerk.storage.fs.root: /var/plugwerk/artifacts
         *
         *   # Shared NFS mount (multi-read-node setup):
         *   plugwerk.storage.fs.root: /mnt/nfs/plugwerk/artifacts
         *
         *   # Local development (project-relative):
         *   plugwerk.storage.fs.root: /tmp/plugwerk-dev
         *   ```
         */
        data class FsProperties(val root: String = "/var/plugwerk/artifacts")
    }

    /**
     * Server-level settings (`plugwerk.server.*`).
     *
     * Contains settings that describe how this server instance is reachable from the outside.
     * These values are used when the server needs to construct absolute URLs in responses
     * (e.g. download links in `plugins.json`) that external clients will follow.
     *
     * @property baseUrl The externally reachable base URL of this server instance, without
     *   a trailing slash. This is the URL that pf4j clients and browsers use to reach the
     *   server — which is often different from the local listen address when the server
     *   runs behind a reverse proxy (nginx, AWS ALB, etc.) that handles TLS termination
     *   or path rewriting.
     *
     *   Used by [io.plugwerk.server.service.Pf4jCompatibilityService] to build absolute
     *   artefact download URLs embedded in `plugins.json`.
     *
     *   Environment variable: `PLUGWERK_BASE_URL`
     *
     *   ```yaml
     *   # Local development (no proxy):
     *   plugwerk.server.base-url: http://localhost:8080
     *
     *   # Production (behind TLS-terminating reverse proxy):
     *   plugwerk.server.base-url: https://plugins.example.com
     *
     *   # Production with a context path:
     *   plugwerk.server.base-url: https://example.com/plugwerk
     *   ```
     */
    data class ServerProperties(val baseUrl: String = "http://localhost:8080")

    /**
     * Authentication configuration (`plugwerk.auth.*`).
     *
     * Controls JWT issuance and provisional dev-user credentials.
     * In Phase 2+, dev-users are replaced by a database-backed [UserCredentialValidator]
     * or by an external OIDC provider (Keycloak, Google, GitHub, etc.).
     *
     * @property jwtSecret HMAC-SHA256 signing key for self-issued JWTs.
     *   Environment variable: `PLUGWERK_JWT_SECRET`
     * @property tokenValidityHours Lifetime of issued access tokens in hours.
     * @property devUsers Hardcoded credentials for the provisional auth phase.
     *   Replace with database users in Phase 2.
     */
    data class AuthProperties(
        val jwtSecret: String = "dev-secret-change-in-production-min32chars!!",
        val tokenValidityHours: Long = 8,
        val devUsers: List<DevUser> = listOf(DevUser("test", "test")),
    ) {
        data class DevUser(val username: String, val password: String)
    }
}
