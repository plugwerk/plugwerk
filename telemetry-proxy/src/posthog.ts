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

import { DEFAULT_POSTHOG_CAPTURE_URL, POSTHOG_TIMEOUT_MS } from "./constants";
import type { ValidPayload } from "./validate";

/** Worker environment bindings. `POSTHOG_PROJECT_KEY` is an encrypted secret. */
export interface PostHogEnv {
  /** Encrypted Worker secret — set via `wrangler secret put POSTHOG_PROJECT_KEY`. Never logged. */
  POSTHOG_PROJECT_KEY: string;
  /** Optional non-secret override of the capture endpoint (defaults to PostHog EU Cloud). */
  POSTHOG_CAPTURE_URL?: string;
}

/**
 * Forward a validated event to PostHog's capture API.
 *
 * `distinct_id = installId` so Growth funnels group by install, and
 * `$process_person_profile: false` keeps it event-only (no person profiles).
 *
 * Returns `true` only on a 2xx upstream response. Any non-2xx, network error,
 * or timeout returns `false`, which the caller maps to HTTP 502 so delivery
 * failures stay observable (the client beacon already fails open per DEV-23).
 *
 * The secret project key is read from the environment and is never logged or
 * echoed in errors.
 */
export async function forwardToPostHog(payload: ValidPayload, env: PostHogEnv): Promise<boolean> {
  const captureUrl = env.POSTHOG_CAPTURE_URL ?? DEFAULT_POSTHOG_CAPTURE_URL;

  const body = JSON.stringify({
    api_key: env.POSTHOG_PROJECT_KEY,
    event: payload.event,
    distinct_id: payload.installId,
    properties: {
      version: payload.version,
      installType: payload.installType,
      $process_person_profile: false,
    },
  });

  try {
    const response = await fetch(captureUrl, {
      method: "POST",
      headers: { "content-type": "application/json" },
      body,
      signal: AbortSignal.timeout(POSTHOG_TIMEOUT_MS),
    });
    return response.ok;
  } catch {
    // Network error or timeout. Do not log the body/key; the 502 is the signal.
    return false;
  }
}
