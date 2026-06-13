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

export type Env = PostHogEnv & RateLimitEnv;

const JSON_CONTENT_TYPE = "application/json";

function emptyResponse(status: number, headers?: HeadersInit): Response {
  return new Response(null, { status, headers });
}

/**
 * Telemetry reverse proxy.
 *
 * Accepts `POST /v1/events` with a strict zero-PII JSON body, then forwards the
 * validated event to PostHog. Status contract:
 *   404 wrong path · 405 non-POST · 415 wrong content-type · 429 rate-limited ·
 *   400 invalid/oversized/unknown-field body · 204 forwarded · 502 forward failed.
 *
 * Request bodies and field values are never logged (defense-in-depth).
 */
const handler: ExportedHandler<Env> = {
  async fetch(request, env): Promise<Response> {
    const { pathname } = new URL(request.url);
    if (pathname !== TELEMETRY_PATH) {
      return emptyResponse(404);
    }

    if (request.method !== "POST") {
      return emptyResponse(405, { allow: "POST" });
    }

    const contentType = request.headers.get("content-type") ?? "";
    if (!contentType.toLowerCase().trimStart().startsWith(JSON_CONTENT_TYPE)) {
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

    // Cheap guard: reject an oversized declared length before buffering the body.
    const declaredLength = Number(request.headers.get("content-length"));
    if (Number.isFinite(declaredLength) && declaredLength > MAX_BODY_BYTES) {
      return emptyResponse(400);
    }

    const rawBody = await request.text();
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
