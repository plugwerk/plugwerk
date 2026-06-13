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

/** Header Cloudflare's edge sets to the original client IP. */
const CLIENT_IP_HEADER = "cf-connecting-ip";

/**
 * Fallback rate-limit key when the client IP header is absent (e.g. `wrangler dev`
 * locally). The production edge always sets `cf-connecting-ip`, so this bucket is
 * only ever hit off the edge; sharing one bucket there is acceptable.
 */
const UNKNOWN_KEY = "unknown";

/** Worker environment binding for the per-IP rate limiter. */
export interface RateLimitEnv {
  /**
   * Workers Rate Limiting binding, keyed by client IP (configured in wrangler.toml
   * as the `[[ratelimits]]` binding `RATE_LIMITER`).
   *
   * CAVEAT: this binding counts per Cloudflare colo (data center), NOT globally, so
   * it is best-effort throttling — not an authoritative global cap. The authoritative
   * edge control is a Cloudflare zone rate-limiting rule on the route, which blocks
   * before the Worker even spins up and is global (provisioned in the DEV-34 deploy
   * runbook). Both layers are required; this one travels with the code.
   */
  RATE_LIMITER: RateLimit;
}

/**
 * Returns `true` when the request is within the per-IP rate limit (allow), `false`
 * when the limit is exceeded and the caller should reject with HTTP 429.
 *
 * Keyed by `cf-connecting-ip` (the original client IP, set by Cloudflare's edge).
 * Pure metering: no logging, no mutation of the request.
 */
export async function isWithinRateLimit(request: Request, env: RateLimitEnv): Promise<boolean> {
  const key = request.headers.get(CLIENT_IP_HEADER) ?? UNKNOWN_KEY;
  const { success } = await env.RATE_LIMITER.limit({ key });
  return success;
}
