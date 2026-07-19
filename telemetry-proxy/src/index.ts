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

import { MAX_BODY_BYTES, RATE_LIMIT_PERIOD_SECONDS, TELEMETRY_PATH } from "./constants";
import { forwardToPostHog, type PostHogEnv } from "./posthog";
import { isWithinRateLimit, type RateLimitEnv } from "./ratelimit";
import { validatePayload } from "./validate";

/** Optional non-secret vars controlling the proxy itself (wrangler.toml `[vars]` or dashboard). */
export interface ProxyToggleEnv {
  /**
   * Operational kill switch. When set to `"true"` every request to the telemetry
   * path is refused with 503 before metering, body read, or PostHog forward.
   * Editable in the Cloudflare dashboard without a code deploy.
   */
  PROXY_DISABLED?: string;
}

export type Env = PostHogEnv & RateLimitEnv & ProxyToggleEnv;

const JSON_CONTENT_TYPE = "application/json";

function isProxyDisabled(env: Env): boolean {
  return (env.PROXY_DISABLED ?? "").trim().toLowerCase() === "true";
}

function emptyResponse(status: number, headers?: HeadersInit): Response {
  return new Response(null, { status, headers });
}

/**
 * True when the media type is exactly `application/json` (parameters like
 * `; charset=utf-8` are allowed). Matches the media-type token on a `;` boundary
 * so near-misses such as `application/json-patch+json` are rejected (415) rather
 * than slipping through a loose prefix check.
 */
function isJsonContentType(contentType: string): boolean {
  const mediaType = contentType.split(";", 1)[0]?.trim().toLowerCase() ?? "";
  return mediaType === JSON_CONTENT_TYPE;
}

/**
 * Read the request body while enforcing the byte cap as bytes arrive, so an
 * oversized (or `Content-Length`-less chunked) body is abandoned mid-stream
 * instead of being fully buffered before the size check. Returns the decoded
 * body, or `null` once the cap is exceeded (the caller maps that to 400).
 */
async function readBodyWithinLimit(request: Request, maxBytes: number): Promise<string | null> {
  const stream = request.body;
  if (stream === null) {
    return "";
  }

  const reader = stream.getReader();
  const chunks: Uint8Array[] = [];
  let total = 0;
  try {
    for (;;) {
      const { done, value } = await reader.read();
      if (done) {
        break;
      }
      if (value) {
        total += value.byteLength;
        if (total > maxBytes) {
          await reader.cancel();
          return null;
        }
        chunks.push(value);
      }
    }
  } finally {
    reader.releaseLock();
  }

  const merged = new Uint8Array(total);
  let offset = 0;
  for (const chunk of chunks) {
    merged.set(chunk, offset);
    offset += chunk.byteLength;
  }
  return new TextDecoder().decode(merged);
}

/**
 * Telemetry reverse proxy.
 *
 * Accepts `POST /v1/events` with a strict zero-PII JSON body, then forwards the
 * validated event to PostHog. Status contract:
 *   404 wrong path · 503 proxy disabled · 405 non-POST · 415 wrong content-type ·
 *   429 rate-limited · 400 invalid/oversized/unknown-field body · 204 forwarded ·
 *   502 forward failed.
 *
 * Request bodies and field values are never logged (defense-in-depth).
 */
const handler: ExportedHandler<Env> = {
  async fetch(request, env): Promise<Response> {
    const { pathname } = new URL(request.url);
    if (pathname !== TELEMETRY_PATH) {
      return emptyResponse(404);
    }

    // Operational kill switch: a deliberate 503 (not a silent 204) keeps the stop
    // observable in delivery metrics, while the fail-open client beacon (DEV-23)
    // guarantees no Plugwerk installation is ever affected by flipping it.
    if (isProxyDisabled(env)) {
      return emptyResponse(503);
    }

    if (request.method !== "POST") {
      return emptyResponse(405, { allow: "POST" });
    }

    const contentType = request.headers.get("content-type") ?? "";
    if (!isJsonContentType(contentType)) {
      return emptyResponse(415);
    }

    // Per-IP rate limit AFTER the cheap path/method/content-type guards, so only real
    // ingestion attempts are metered. Throttled requests get a 429 and we skip the body
    // read and the outbound PostHog forward entirely — this endpoint is public and
    // unauthenticated, so it must not become an amplifier (OWASP API4:2023). Best-effort
    // per-colo throttle; the authoritative global cap is the zone rule (see DEV-34).
    if (!(await isWithinRateLimit(request, env))) {
      return emptyResponse(429, { "retry-after": String(RATE_LIMIT_PERIOD_SECONDS) });
    }

    // Cheap guard: reject an oversized declared length before even reading the body.
    const declaredLength = Number(request.headers.get("content-length"));
    if (Number.isFinite(declaredLength) && declaredLength > MAX_BODY_BYTES) {
      return emptyResponse(400);
    }

    // Authoritative guard: enforce the byte cap as the body streams in, so a
    // chunked body that omits (or lies about) Content-Length is abandoned rather
    // than fully buffered.
    const rawBody = await readBodyWithinLimit(request, MAX_BODY_BYTES);
    if (rawBody === null) {
      return emptyResponse(400);
    }
    const byteLength = new TextEncoder().encode(rawBody).length;

    const result = validatePayload(rawBody, byteLength);
    if (!result.ok) {
      return emptyResponse(400);
    }

    const delivered = await forwardToPostHog(result.payload, env);
    return emptyResponse(delivered ? 204 : 502);
  },
};

export default handler;
