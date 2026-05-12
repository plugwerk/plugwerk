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
 * `ApplicationSettingsService`. See [ADR-0016](../../../../../../../../docs/adrs/0016-application-settings-precedence.md).
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
    @field:Valid val server: ServerProperties = ServerProperties(),
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
     * @property s3 Settings for the S3-compatible backend. Only relevant when [type] is `s3`.
     */
    data class StorageProperties(
        val type: String = "fs",
        val fs: FsProperties = FsProperties(),
        @field:Valid val s3: S3Properties? = null,
    ) {
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

        /**
         * S3-compatible object-storage settings (`plugwerk.storage.s3.*`). Used when
         * `plugwerk.storage.type=s3` (#191).
         *
         * Supports any S3-compatible endpoint via [endpoint] override: AWS S3, MinIO,
         * Hetzner Object Storage, Cloudflare R2, etc.
         *
         * @property bucket REQUIRED. Bucket name that holds plugin artefacts. The
         *   bucket must already exist; Plugwerk does not create buckets. A startup
         *   `HeadBucket` probe verifies access — see [failFastOnBucketMissing].
         *
         *   Environment variable: `PLUGWERK_STORAGE_S3_BUCKET`
         *
         * @property region REQUIRED. AWS region or region-compatible identifier of
         *   the bucket. For non-AWS endpoints this is still required by the SDK but
         *   often a placeholder (`auto`, `us-east-1`).
         *
         *   Environment variable: `PLUGWERK_STORAGE_S3_REGION`
         *
         * @property endpoint Optional override URL for non-AWS S3-compatible endpoints.
         *   Leave blank for AWS S3 (the SDK derives the endpoint from [region]).
         *
         *   Environment variable: `PLUGWERK_STORAGE_S3_ENDPOINT`
         *
         *   ```yaml
         *   # MinIO local dev:
         *   plugwerk.storage.s3.endpoint: http://localhost:9000
         *
         *   # Cloudflare R2:
         *   plugwerk.storage.s3.endpoint: https://<account>.r2.cloudflarestorage.com
         *
         *   # Hetzner Object Storage (fsn1 region):
         *   plugwerk.storage.s3.endpoint: https://fsn1.your-objectstorage.com
         *   ```
         *
         * @property accessKey Optional. When [accessKey] AND [secretKey] are both set,
         *   the SDK uses static credentials. When BOTH are blank, the SDK falls back
         *   to the `DefaultCredentialsProvider` chain (env, instance profile, IRSA,
         *   ECS task role). Half-configured states are rejected at startup.
         *
         *   Environment variable: `PLUGWERK_STORAGE_S3_ACCESS_KEY`
         *
         * @property secretKey Optional. See [accessKey].
         *
         *   Environment variable: `PLUGWERK_STORAGE_S3_SECRET_KEY`
         *
         * @property keyPrefix Optional prefix prepended to every artefact key.
         *   Lets multiple Plugwerk installations share one bucket (`prod/plugwerk/`,
         *   `staging/plugwerk/`). Default empty. **Must not start with `/`.**
         *
         *   Environment variable: `PLUGWERK_STORAGE_S3_KEY_PREFIX`
         *
         * @property pathStyleAccess `true` for MinIO and other endpoints that do not
         *   support virtual-hosted–style URLs. Default `false` (DNS-style, the
         *   default for AWS S3 / R2 / Hetzner).
         *
         *   Environment variable: `PLUGWERK_STORAGE_S3_PATH_STYLE_ACCESS`
         *
         * @property failFastOnBucketMissing When `true`, the server refuses to start
         *   if the startup `HeadBucket` probe fails. Default `false` (probe failure
         *   logs ERROR and the server keeps running so the operator can fix the
         *   bucket without a restart loop). Set `true` in container orchestrators
         *   that have their own restart loop and prefer fail-closed semantics
         *   (mirrors #501).
         *
         *   Environment variable: `PLUGWERK_STORAGE_S3_FAIL_FAST_ON_BUCKET_MISSING`
         */
        data class S3Properties(
            @field:NotBlank val bucket: String = "",
            @field:NotBlank val region: String = "",
            val endpoint: String? = null,
            val accessKey: String? = null,
            val secretKey: String? = null,
            val keyPrefix: String = "",
            val pathStyleAccess: Boolean = false,
            val failFastOnBucketMissing: Boolean = false,
        ) {
            /**
             * Static credentials must be supplied as a pair: both set or both blank.
             * A half-configured state means the operator forgot one half — fail
             * fast at startup rather than silently using the default provider chain.
             */
            @AssertTrue(
                message = "S3 accessKey and secretKey must either both be set or both be blank " +
                    "(blank means fall back to the default AWS credentials chain).",
            )
            fun isCredentialPairConsistent(): Boolean {
                val accessBlank = accessKey.isNullOrBlank()
                val secretBlank = secretKey.isNullOrBlank()
                return accessBlank == secretBlank
            }

            /**
             * A leading slash in `key-prefix` becomes a key segment named `""` on
             * S3, which is valid but always wrong (no object browser will show it
             * as the operator expects). Reject it.
             */
            @AssertTrue(message = "S3 key-prefix must not start with '/' (use 'env/plugwerk/' not '/env/plugwerk/').")
            fun isKeyPrefixWellFormed(): Boolean = !keyPrefix.startsWith("/")

            /** Redact [secretKey] from any accidental log line. */
            override fun toString(): String = "S3Properties(bucket='$bucket', region='$region', endpoint=$endpoint, " +
                "accessKey=${accessKey?.let { "<set>" } ?: "<unset>"}, " +
                "secretKey=${secretKey?.let { "<set>" } ?: "<unset>"}, " +
                "keyPrefix='$keyPrefix', pathStyleAccess=$pathStyleAccess, " +
                "failFastOnBucketMissing=$failFastOnBucketMissing)"
        }

        /**
         * When [type] is `s3`, the [s3] sub-section must be present and have a non-blank
         * bucket. Without this, a half-configured deploy boots and silently falls back to
         * the filesystem default, which is exactly the surprise we want to avoid.
         */
        @AssertTrue(
            message = "plugwerk.storage.type=s3 requires plugwerk.storage.s3.bucket to be set.",
        )
        fun isS3ConfigPresentWhenS3Selected(): Boolean = type != "s3" || (s3 != null && s3.bucket.isNotBlank())
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
        val webBaseUrl: String? = null,
        @field:Valid val cors: CorsProperties = CorsProperties(),
    ) {
        /**
         * Base URL of the user-facing web frontend, without a trailing slash. Used when
         * the server emits links that the recipient is expected to open in a browser
         * (e.g. the verification link in self-registration emails for #420).
         *
         * In production deployments the SPA is bundled into the server JAR and served
         * from the same origin as the REST API, so the frontend lives at exactly
         * [baseUrl] — leave [webBaseUrl] unset and the getter falls back to it.
         *
         * In local development the Vite dev server serves the SPA on a different port
         * (typically `http://localhost:5173`) while the backend listens on `:8080`. Set
         * `plugwerk.server.web-base-url=http://localhost:5173` (or
         * `PLUGWERK_WEB_BASE_URL`) so emailed links point at the dev server, not the
         * backend's static-resource handler which doesn't know the React routes.
         */
        fun effectiveWebBaseUrl(): String = webBaseUrl?.takeIf { it.isNotBlank() } ?: baseUrl

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
     *   Environment variable: `PLUGWERK_AUTH_JWT_SECRET`
     *
     *   ```bash
     *   export PLUGWERK_AUTH_JWT_SECRET="$(openssl rand -base64 32)"
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
     * @property encryptionKey Password used to derive the AES-256 key that encrypts OIDC
     *   provider client secrets at rest in the `oidc_provider` table. Fed to PBKDF2 via
     *   Spring Security's `Encryptors.text()` — length controls the PBKDF2 *input
     *   entropy*, not the AES key size (which is fixed at 256 bits). Must be at least
     *   16 characters; **32+ is recommended**. **Never commit a real value to source
     *   control.**
     *
     *   **Rotating this value invalidates every existing `client_secret_encrypted` row**
     *   because PBKDF2 derives a different AES key from a new password. See ADR-0022
     *   for the manual re-encrypt procedure.
     *
     *   Environment variable: `PLUGWERK_AUTH_ENCRYPTION_KEY`
     *
     *   ```bash
     *   # Recommended: 32+ chars for higher PBKDF2 input entropy
     *   export PLUGWERK_AUTH_ENCRYPTION_KEY="$(openssl rand -base64 32)"
     *
     *   # Legacy minimum (still accepted):
     *   export PLUGWERK_AUTH_ENCRYPTION_KEY="$(openssl rand -hex 8)"
     *   ```
     */
    data class AuthProperties(
        @field:NotBlank(message = "plugwerk.auth.jwt-secret must not be blank — set PLUGWERK_AUTH_JWT_SECRET")
        @field:Size(min = 32, message = "plugwerk.auth.jwt-secret must be at least 32 characters")
        val jwtSecret: String = "",
        /**
         * @deprecated Superseded by [accessTokenValidityMinutes] (ADR-0027 / #294). Retained
         *   as a no-op for one release so existing deployments do not fail on startup — if
         *   set to a non-default value, a warning is emitted and the value is ignored.
         */
        @Deprecated("Use accessTokenValidityMinutes; see ADR-0027")
        val tokenValidityHours: Long = 8,
        @field:NotBlank(message = "plugwerk.auth.encryption-key must not be blank — set PLUGWERK_AUTH_ENCRYPTION_KEY")
        @field:Size(
            min = 16,
            max = 256,
            message = "plugwerk.auth.encryption-key must be at least 16 characters (32+ recommended)",
        )
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
        @field:Valid val actuator: ActuatorProperties = ActuatorProperties(),
        /**
         * Access-token validity in minutes (ADR-0027 / #294). Short-lived by design — the
         * token lives in frontend memory only, and session continuity across page reloads
         * comes from the httpOnly refresh cookie ([refreshTokenValidityHours]). Default 15
         * matches industry norm (Auth0, Okta, AWS Cognito).
         *
         * Environment variable: `PLUGWERK_AUTH_ACCESS_TOKEN_VALIDITY_MINUTES`
         */
        @field:Min(1)
        @field:Max(1440)
        val accessTokenValidityMinutes: Long = 15,
        /**
         * Refresh-cookie validity in hours (ADR-0027 / #294). Default 168 (7 days). The
         * cookie is rotated on every refresh, so a user who stays active is effectively
         * logged in forever; an idle session expires after this window.
         *
         * Environment variable: `PLUGWERK_AUTH_REFRESH_TOKEN_VALIDITY_HOURS`
         */
        @field:Min(1)
        @field:Max(8760)
        val refreshTokenValidityHours: Long = 168,
        /**
         * Sets `Secure` on the refresh cookie. Defaults to `true` so production deployments
         * behind HTTPS get it right by default. Override to `false` only for local
         * development on plain HTTP; browsers silently drop Secure cookies on HTTP origins.
         *
         * Environment variable: `PLUGWERK_AUTH_COOKIE_SECURE`
         */
        val cookieSecure: Boolean = true,
        /**
         * Extra hostnames the frontend `downloadArtifact` helper may attach the bearer to.
         * Same-origin is always allowed; everything else requires an explicit entry here.
         * Empty list (default) means strict same-origin-only — correct for the bundled
         * single-origin deployment. Populate when artifacts are served from a CDN/S3 bucket
         * on a different host and you want authenticated downloads against that host.
         *
         * Environment variable: `PLUGWERK_AUTH_DOWNLOAD_ALLOWED_HOSTS`
         *   (comma-separated hostnames — no scheme, no port).
         */
        val downloadAllowedHosts: List<String> = emptyList(),
        /**
         * CIDR ranges of reverse proxies whose `X-Forwarded-For` header is trusted
         * for client-IP resolution (SBS-006 / #265). When a request reaches the
         * server, the resolver checks whether `request.remoteAddr` matches any of
         * these ranges; only then is the leftmost `X-Forwarded-For` value honoured.
         *
         * **Empty default = no proxy is trusted, so X-Forwarded-For is ignored
         * entirely.** This is the secure choice for a server with no reverse
         * proxy in front of it (an attacker controls the header).
         *
         * **Operators behind a reverse proxy MUST configure this** with their
         * proxy's egress IPs/ranges — otherwise every client appears to come
         * from the proxy IP and per-IP rate-limiting collapses to a single
         * shared bucket for the whole user base.
         *
         * Each entry is a CIDR notation string (e.g. `10.0.0.0/8`,
         * `127.0.0.1/32`, `::1/128`). Validated at startup by
         * [io.plugwerk.server.config.PlugwerkPropertiesValidator] using
         * Spring Security's `IpAddressMatcher` — invalid syntax fails fast.
         *
         * Environment variable: `PLUGWERK_AUTH_TRUSTED_PROXY_CIDRS`
         *   (comma-separated CIDR ranges).
         */
        val trustedProxyCidrs: List<String> = emptyList(),
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
            val register: RegisterRateLimitProperties = RegisterRateLimitProperties(),
            val passwordReset: PasswordResetRateLimitProperties = PasswordResetRateLimitProperties(),
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

        /**
         * Two-bucket rate limiting for `POST /api/v1/auth/register` (#420).
         *
         * Self-registration is a public endpoint with two abuse vectors that
         * each warrant their own bucket: bulk account creation from a single
         * IP, and email-enumeration probes that systematically iterate
         * candidate addresses. The IP bucket sets a generous-but-firm ceiling
         * for the volume case; the email bucket is much stricter so an
         * attacker probing whether a specific address already exists can't
         * cycle quickly even from a botnet.
         *
         * @property ipMaxAttempts IP-keyed cap per [ipWindowSeconds].
         *   Environment variable: `PLUGWERK_AUTH_RATE_LIMIT_REGISTER_IP_MAX_ATTEMPTS`
         * @property ipWindowSeconds IP rate-limit window in seconds.
         *   Environment variable: `PLUGWERK_AUTH_RATE_LIMIT_REGISTER_IP_WINDOW_SECONDS`
         * @property emailMaxAttempts Email-keyed cap per [emailWindowSeconds],
         *   keyed off SHA-256(lowercase(email)) so the email itself is never
         *   stored in the bucket map.
         *   Environment variable: `PLUGWERK_AUTH_RATE_LIMIT_REGISTER_EMAIL_MAX_ATTEMPTS`
         * @property emailWindowSeconds Email rate-limit window in seconds.
         *   Environment variable: `PLUGWERK_AUTH_RATE_LIMIT_REGISTER_EMAIL_WINDOW_SECONDS`
         */
        data class RegisterRateLimitProperties(
            val ipMaxAttempts: Int = 10,
            val ipWindowSeconds: Long = 60,
            val emailMaxAttempts: Int = 5,
            val emailWindowSeconds: Long = 3600,
        )

        /**
         * Two-bucket rate limiting for the password-reset flow (#421).
         *
         * Two attack surfaces with different shapes:
         *   - `POST /auth/forgot-password` (IP-keyed): mail-bombing a known
         *     user by repeatedly requesting reset links. We deliberately do
         *     **not** key by username/email — that would create a timing
         *     oracle (existing accounts run out faster than non-existing
         *     ones, leaking enumeration).
         *   - `POST /auth/reset-password` (token-keyed): brute-forcing a
         *     leaked link before its TTL elapses. Keyed by SHA-256 of the
         *     submitted token so the cap follows the link, not the IP.
         *
         * @property ipMaxAttempts IP-keyed cap per [ipWindowSeconds] across
         *   both endpoints. Conservative by default — five attempts is
         *   plenty for a real user who fat-fingered their email.
         *   Environment variable: `PLUGWERK_AUTH_RATE_LIMIT_PASSWORD_RESET_IP_MAX_ATTEMPTS`
         * @property ipWindowSeconds IP rate-limit window in seconds.
         *   Environment variable: `PLUGWERK_AUTH_RATE_LIMIT_PASSWORD_RESET_IP_WINDOW_SECONDS`
         * @property tokenMaxAttempts Cap per token-hash per
         *   [tokenWindowSeconds]. The default of 10 means an attacker who
         *   somehow obtained a leaked link cannot iterate ten failed
         *   reset-password POSTs against it within the hour — long before
         *   the link's own expiry kicks in.
         *   Environment variable: `PLUGWERK_AUTH_RATE_LIMIT_PASSWORD_RESET_TOKEN_MAX_ATTEMPTS`
         * @property tokenWindowSeconds Token rate-limit window in seconds.
         *   Environment variable: `PLUGWERK_AUTH_RATE_LIMIT_PASSWORD_RESET_TOKEN_WINDOW_SECONDS`
         */
        data class PasswordResetRateLimitProperties(
            val ipMaxAttempts: Int = 5,
            val ipWindowSeconds: Long = 900,
            val tokenMaxAttempts: Int = 10,
            val tokenWindowSeconds: Long = 3600,
        )

        /**
         * Actuator scrape-account configuration (`plugwerk.auth.actuator.*`).
         * See [ADR-0025](../../../../../../../../docs/adrs/0025-actuator-endpoint-hardening.md).
         *
         * `/actuator/info` and `/actuator/prometheus` are gated behind either a superadmin
         * JWT **or** — when both fields below are set — a dedicated HTTP Basic scrape
         * account. `/actuator/health` remains public for container health probes.
         *
         * Feature is **opt-in**: when both fields are null or blank the scrape chain is
         * not installed, and the superadmin-only fallback applies. Set both values to
         * enable unattended Prometheus scraping via `basic_auth` — the cross-field
         * [isScrapeAccountConfigurationValid] validator rejects half-configured states.
         *
         * @property scrapeUsername Username for the Prometheus scrape account. Any
         *   non-blank string; conventionally `prometheus` or the scraper's service-account
         *   name. Must be set together with [scrapePassword] or left null/blank.
         *
         *   Environment variable: `PLUGWERK_AUTH_ACTUATOR_SCRAPE_USERNAME`
         *
         *   ```yaml
         *   plugwerk.auth.actuator.scrape-username: prometheus
         *   ```
         *
         * @property scrapePassword Plaintext password for the scrape account. Hashed with
         *   BCrypt once at bean creation and never logged. Minimum 16 characters when set;
         *   32+ recommended. Rotate by changing the env var and restarting the server.
         *
         *   Environment variable: `PLUGWERK_AUTH_ACTUATOR_SCRAPE_PASSWORD`
         *
         *   ```bash
         *   export PLUGWERK_AUTH_ACTUATOR_SCRAPE_PASSWORD="$(openssl rand -base64 32)"
         *   ```
         */
        data class ActuatorProperties(val scrapeUsername: String? = null, val scrapePassword: String? = null) {
            /** `true` when the scrape account is fully configured and the basic-auth chain should be installed. */
            fun isScrapeAccountEnabled(): Boolean = !scrapeUsername.isNullOrBlank() && !scrapePassword.isNullOrBlank()

            /**
             * Either both scrape-account fields are set, or both are unset. Half-configured
             * states (username without password, or vice versa) would silently fail at
             * login time and are rejected at startup instead. When both are set, the
             * password must also meet the minimum length (16 chars; 32+ recommended).
             */
            @AssertTrue(
                message = "plugwerk.auth.actuator: scrape-username + scrape-password must be set together " +
                    "(both blank, or both non-blank with password ≥ 16 chars)",
            )
            fun isScrapeAccountConfigurationValid(): Boolean {
                val userPresent = !scrapeUsername.isNullOrBlank()
                val passPresent = !scrapePassword.isNullOrBlank()
                if (userPresent != passPresent) return false
                if (!userPresent) return true
                val passLen = scrapePassword!!.length
                return passLen in 16..256
            }

            /** Redacts the password in logs / `toString()` / error messages. */
            override fun toString(): String =
                "ActuatorProperties(scrapeUsername=$scrapeUsername, scrapePassword=${if (scrapePassword.isNullOrBlank()) "<unset>" else "<redacted>"})"
        }
    }
}
