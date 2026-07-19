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

/** Shared constants and types for the telemetry reverse proxy. */

/** The single public ingestion path. Anything else 404s. */
export const TELEMETRY_PATH = "/v1/events";

/** Maximum accepted request body size in bytes (2 KB). Larger bodies are rejected with 400. */
export const MAX_BODY_BYTES = 2048;

/** Maximum accepted length of the `version` string. */
export const MAX_VERSION_LENGTH = 32;

/** PostHog EU Cloud capture endpoint (events are forwarded here). */
export const DEFAULT_POSTHOG_CAPTURE_URL = "https://eu.i.posthog.com/capture/";

/**
 * Required prefix of a PostHog **project** (ingestion / capture) API key.
 *
 * The go-live security gate (DEV-54, condition 2) requires the configured secret to
 * be the write-only `phc_…` project key — never a personal (`phx_…`) or admin key —
 * so a leak's blast radius stays capture-only, not data-read/admin. We enforce this
 * in code (fail-closed) so a misconfigured key is never used to forward events,
 * rather than relying on the operator getting `wrangler secret put` right.
 */
export const POSTHOG_PROJECT_KEY_PREFIX = "phc_";

/** Upstream forward timeout in milliseconds; a slower PostHog is treated as a delivery failure (502). */
export const POSTHOG_TIMEOUT_MS = 5000;

/**
 * Per-IP rate-limit window in seconds, surfaced in the `Retry-After` header on a 429.
 * MUST match `simple.period` of the `[[ratelimits]]` binding in wrangler.toml.
 */
export const RATE_LIMIT_PERIOD_SECONDS = 60;

/** Allowed `installType` values (mirrors the beacon contract in DEV-23). */
export const INSTALL_TYPES = ["docker-compose", "jar", "k8s", "unknown"] as const;
export type InstallType = (typeof INSTALL_TYPES)[number];

/** Allowed `event` values. Superset of the current beacon (DEV-23) to cover funnel events from DEV-22. */
export const EVENTS = ["server_start", "heartbeat", "namespace_created", "plugin_published"] as const;
export type TelemetryEvent = (typeof EVENTS)[number];

/**
 * The ONLY fields accepted on the wire. The validator rejects (does not strip)
 * any payload carrying a key outside this allowlist — strict zero-PII contract.
 */
export const ALLOWED_FIELDS = ["installId", "version", "installType", "event"] as const;

/** RFC 4122 UUID v4: version nibble = 4, variant nibble in [8,9,a,b]. */
export const UUID_V4 = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

/**
 * "semver-ish": must start with a digit, then dotted alphanumeric / build metadata.
 * Intentionally lenient — the beacon ships values like `1.1.0-SNAPSHOT` (see VERSION).
 */
export const VERSION_RE = /^[0-9][0-9A-Za-z.+-]*$/;
