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
package io.plugwerk.server

import jakarta.validation.Valid
import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

/**
 * Central configuration properties for the Plugwerk server, bound to the `plugwerk` prefix.
 *
 * All infra/security/bootstrap Plugwerk settings are grouped here. Admin-manageable values
 * (default language, site name, upload size, tracking flags) are **not** here — they live
 * in the `application_setting` database table and are accessed via
 * `GeneralSettingsService`. See [ADR-0016](../../../../../../../../docs/adrs/0016-application-settings-precedence.md).
 *
 * Each logical sub-section is represented by a nested data class so that consumers can
 * depend on only the slice they need. Registered via `@EnableConfigurationProperties` on
 * [PlugwerkApplication]. Configuration is supplied through `application.yml` and can be
 * overridden per environment using environment variables (see each property for the
 * corresponding variable name).
 *
 * @property storage Artifact storage configuration — selects the backend and provides
 *   backend-specific settings. See [StorageProperties].
 * @property server Server-level settings shared across services. See [ServerProperties].
 */
@Validated
@ConfigurationProperties(prefix = "plugwerk")
data class PlugwerkProperties(
    val storage: StorageProperties = StorageProperties(),
    val server: ServerProperties = ServerProperties(),
    @field:Valid val auth: AuthProperties = AuthProperties(),
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
    data class ServerProperties(
        val baseUrl: String = "http://localhost:8080",
        @field:Valid val cors: CorsProperties = CorsProperties(),
    ) {
        /**
         * CORS configuration (`plugwerk.server.cors.*`). See [ADR-0021](../../../../../../../../docs/adrs/0021-cors-same-origin-default.md).
         *
         * Plugwerk's frontend is bundled into the server JAR and served from the same
         * origin as the REST API, so **no cross-origin requests are needed by default**.
         * [allowedOrigins] therefore defaults to an empty list, which preserves the
         * same-origin-only behaviour bit-for-bit but makes the intent explicit at
         * code-read time (vs. Spring Boot's implicit default).
         *
         * Add origins to [allowedOrigins] only if the frontend is deployed separately
         * from the backend (CDN, subdomain, etc.).
         *
         * @property allowedOrigins Exact browser-`Origin` values that may send
         *   cross-origin requests to API paths under `/api/v1`. Case-sensitive,
         *   scheme and host must match exactly. Wildcards are not supported here —
         *   use multiple explicit entries.
         *
         *   Environment variable: `PLUGWERK_SERVER_CORS_ALLOWED_ORIGINS`
         *   (comma-separated).
         *
         * @property allowedMethods HTTP methods browsers may use in cross-origin
         *   requests. Defaults to the standard REST set.
         *
         *   Environment variable: `PLUGWERK_SERVER_CORS_ALLOWED_METHODS`
         *
         * @property allowedHeaders Request headers browsers may send on cross-origin
         *   requests. Includes `Authorization` (JWT Bearer), `Content-Type`, and
         *   `X-Api-Key` (namespace access keys).
         *
         *   Environment variable: `PLUGWERK_SERVER_CORS_ALLOWED_HEADERS`
         *
         * @property allowCredentials Whether the browser may include credentials
         *   (cookies, `Authorization` header, client certs) on cross-origin requests.
         *   Required for JWT Bearer auth to reach the server from a different origin.
         *   **Must not be combined with `allowedOrigins: ["*"]`** — the combination is
         *   explicitly rejected by Spring Security at runtime, and by this property's
         *   [isWildcardCredentialsCombinationValid] validator at startup.
         *
         *   Environment variable: `PLUGWERK_SERVER_CORS_ALLOW_CREDENTIALS`
         *
         * @property maxAge Seconds a browser may cache the preflight response. Capped
         *   at 24 hours, which is also the effective ceiling most browsers enforce.
         *
         *   Environment variable: `PLUGWERK_SERVER_CORS_MAX_AGE`
         */
        data class CorsProperties(
            val allowedOrigins: List<String> = emptyList(),
            val allowedMethods: List<String> = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"),
            val allowedHeaders: List<String> = listOf("Authorization", "Content-Type", "X-Api-Key"),
            val allowCredentials: Boolean = true,
            @field:Min(0) @field:Max(86400) val maxAge: Long = 3600,
        ) {
            /**
             * `allowedOrigins: ["*"]` combined with `allowCredentials: true` is rejected
             * by Spring Security at runtime. Catch it at startup with a clear message.
             */
            @AssertTrue(
                message = "plugwerk.server.cors: allowed-origins must not contain '*' when allow-credentials=true",
            )
            fun isWildcardCredentialsCombinationValid(): Boolean = !(allowCredentials && allowedOrigins.contains("*"))
        }
    }

    /**
     * Authentication configuration (`plugwerk.auth.*`).
     *
     * Controls JWT issuance and secret encryption for OIDC provider credentials.
     *
     * @property jwtSecret HMAC-SHA256 signing key for self-issued JWTs. Must be at least
     *   32 characters. **Never commit a real secret to source control.**
     *
     *   Environment variable: `PLUGWERK_JWT_SECRET`
     *
     *   ```bash
     *   export PLUGWERK_JWT_SECRET="$(openssl rand -base64 32)"
     *   ```
     *
     * @property tokenValidityHours Lifetime of self-issued JWT access tokens in hours.
     *   Default: `8` (one working day). Reduce for higher-security deployments.
     *
     *   Environment variable: `PLUGWERK_TOKEN_VALIDITY_HOURS`
     *
     *   ```yaml
     *   plugwerk.auth.token-validity-hours: 8
     *   ```
     *
     * @property encryptionKey AES encryption key used to encrypt OIDC provider client
     *   secrets at rest in the `oidc_provider` table. Must be exactly 16 characters
     *   (AES-128). **Never commit a real key to source control.**
     *
     *   Environment variable: `PLUGWERK_ENCRYPTION_KEY`
     *
     *   ```bash
     *   export PLUGWERK_ENCRYPTION_KEY="$(openssl rand -hex 8)"
     *   ```
     */
    data class AuthProperties(
        @field:NotBlank(message = "plugwerk.auth.jwt-secret must not be blank — set PLUGWERK_JWT_SECRET")
        @field:Size(min = 32, message = "plugwerk.auth.jwt-secret must be at least 32 characters")
        val jwtSecret: String = "",
        val tokenValidityHours: Long = 8,
        @field:NotBlank(message = "plugwerk.auth.encryption-key must not be blank — set PLUGWERK_ENCRYPTION_KEY")
        @field:Size(min = 16, max = 16, message = "plugwerk.auth.encryption-key must be exactly 16 characters")
        val encryptionKey: String = "",
        /**
         * Optional fixed initial admin password. When set to a non-blank value the admin
         * user is created with this password and `passwordChangeRequired = false`. When
         * absent — or blank / whitespace-only — a random 16-char password is generated
         * and `passwordChangeRequired = true`.
         *
         * **Do not set this in production.** Use only for CI / smoke-test environments.
         * Environment variable: `PLUGWERK_AUTH_ADMIN_PASSWORD`
         */
        val adminPassword: String? = null,
        val rateLimit: RateLimitProperties = RateLimitProperties(),
    ) {
        /**
         * Rate limiting configuration (`plugwerk.auth.rate-limit.*`).
         *
         * Controls brute-force protection on authentication endpoints. Uses an in-memory
         * token-bucket algorithm (Bucket4j). Login is keyed by client IP; change-password
         * is keyed by the authenticated subject so attackers on a hijacked session cannot
         * drain other users' buckets.
         *
         * @property maxAttempts Maximum number of login attempts allowed per IP address within
         *   the configured time window. Requests exceeding this limit receive HTTP 429.
         *
         *   Environment variable: `PLUGWERK_AUTH_RATE_LIMIT_MAX_ATTEMPTS`
         *
         *   ```yaml
         *   plugwerk.auth.rate-limit.max-attempts: 10
         *   ```
         *
         * @property windowSeconds Duration of the rate limit window in seconds. After this
         *   period, the token bucket refills completely for the given IP.
         *
         *   Environment variable: `PLUGWERK_AUTH_RATE_LIMIT_WINDOW_SECONDS`
         *
         *   ```yaml
         *   plugwerk.auth.rate-limit.window-seconds: 60
         *   ```
         *
         * @property changePassword Rate limit for `POST /api/v1/auth/change-password`.
         *   Keyed by authenticated subject, with its own Bucket4j configuration so login
         *   and change-password cannot drain each other's buckets.
         */
        data class RateLimitProperties(
            val maxAttempts: Int = 10,
            val windowSeconds: Long = 60,
            val changePassword: ChangePasswordRateLimitProperties = ChangePasswordRateLimitProperties(),
        )

        /**
         * Subject-keyed rate limiting for `POST /api/v1/auth/change-password`.
         *
         * Defaults to 5 attempts per 5 minutes: tolerates legitimate password-rotation
         * UX glitches (retyped current password, paste errors) while stopping online
         * brute-force attempts against the current password.
         *
         * @property maxAttempts Maximum change-password attempts per authenticated subject
         *   within the configured time window. Exceeding this limit returns HTTP 429.
         *
         *   Environment variable: `PLUGWERK_AUTH_RATE_LIMIT_CHANGE_PASSWORD_MAX_ATTEMPTS`
         *
         * @property windowSeconds Duration of the change-password rate-limit window in
         *   seconds.
         *
         *   Environment variable: `PLUGWERK_AUTH_RATE_LIMIT_CHANGE_PASSWORD_WINDOW_SECONDS`
         */
        data class ChangePasswordRateLimitProperties(val maxAttempts: Int = 5, val windowSeconds: Long = 300)
    }
}
