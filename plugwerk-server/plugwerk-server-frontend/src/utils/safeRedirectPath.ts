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

const SAFE_FALLBACK = "/";

/**
 * Validates that an attacker-controllable redirect target (e.g. the `?from=…`
 * parameter on the login page) points back into the same origin. Returns the
 * canonical pathname-plus-search-plus-hash on success, or `/` on any rejection.
 *
 * Rejects:
 *   - null / undefined / empty / non-string inputs
 *   - protocol-relative URLs ("//evil.com") which react-router's navigate()
 *     normalises and may follow to an external origin (the TS-004 vector)
 *   - backslash variants ("/\\evil.com", "\\/evil.com") that some browsers
 *     treat the same as "//"
 *   - absolute URLs whose origin differs from window.location.origin
 *   - non-http(s) schemes (javascript:, data:, file:, …)
 *   - relative paths without a leading `/` that would resolve against /login
 *
 * Safe by construction: any thrown error inside the URL constructor (e.g.
 * malformed input) falls through to the same `/` fallback.
 */
export function safeRedirectPath(from: string | null | undefined): string {
  if (typeof from !== "string" || from.length === 0) return SAFE_FALLBACK;
  if (!from.startsWith("/")) return SAFE_FALLBACK;
  if (from.startsWith("//")) return SAFE_FALLBACK;
  if (from.startsWith("/\\")) return SAFE_FALLBACK;

  try {
    const target = new URL(from, window.location.origin);
    if (target.origin !== window.location.origin) return SAFE_FALLBACK;
    return target.pathname + target.search + target.hash;
  } catch {
    return SAFE_FALLBACK;
  }
}
