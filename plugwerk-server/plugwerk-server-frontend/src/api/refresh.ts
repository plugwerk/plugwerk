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

/**
 * Exchange the httpOnly refresh cookie for a new access token (ADR-0027 / #294).
 *
 * Server-side CSRF (double-submit) is enforced on `/api/v1/auth/refresh`: Spring sets
 * an `XSRF-TOKEN` cookie on a prior request, and we echo its value in the
 * `X-XSRF-TOKEN` header. The refresh cookie itself is httpOnly and rides along
 * automatically because of `credentials: "include"`.
 *
 * Single-flight: concurrent 401 retries share one in-flight promise so a burst of
 * failing requests triggers one refresh call, not N.
 */
export interface RefreshedAuth {
  accessToken: string;
  /** Stable Plugwerk user identifier (`plugwerk_user.id`). Issue #351. */
  userId: string;
  /** Human-readable label for the user (display in profile / member lists). */
  displayName: string;
  /** Local-login username; null for OIDC-sourced sessions. */
  username: string | null;
  email: string;
  source: "LOCAL" | "OIDC";
  passwordChangeRequired: boolean;
  isSuperadmin: boolean;
}

let inFlight: Promise<RefreshedAuth | null> | null = null;

export function refreshAccessToken(): Promise<RefreshedAuth | null> {
  if (inFlight) return inFlight;
  inFlight = doRefreshWithCsrfBootstrap().finally(() => {
    inFlight = null;
  });
  return inFlight;
}

/**
 * Refresh with CSRF bootstrap retry.
 *
 * Spring's `CookieCsrfTokenRepository` issues the `XSRF-TOKEN` cookie lazily —
 * it only appears in the browser's cookie jar after the first response that
 * needs to tell the client about it. The jar can be empty in two scenarios
 * we both have to recover from:
 *
 *   1. **Fresh reload of a logged-in tab.** Our local `/auth/login` endpoint
 *      is not wired into Spring's auth filter chain, so the
 *      `CsrfAuthenticationStrategy` never fires and never rotates a token
 *      into the response. Spring's `CsrfFilter` then short-circuits the
 *      first `POST /auth/refresh` with **401** (HttpStatusEntryPoint kicks
 *      in because the request was unauthenticated) and writes a fresh
 *      XSRF-TOKEN cookie as a side effect.
 *
 *   2. **Right after an OAuth2 browser-flow callback (#79).** Spring
 *      *does* run its full authenticated-login pipeline here, and as the
 *      standard CSRF rotation step it actively *deletes* the existing
 *      XSRF-TOKEN cookie (`Set-Cookie: XSRF-TOKEN=; Expires=epoch`) so the
 *      next request will pick up a freshly-rotated value. The first
 *      `POST /auth/refresh` then has no header, but this time the request
 *      *is* authenticated (the session cookie is set), so Spring's
 *      `CsrfFilter` rejects it with **403** instead of 401.
 *
 * In both cases the recovery is identical: the rejection wrote a fresh
 * XSRF-TOKEN cookie, so a single retry will carry the new header value
 * through and succeed. We accept either status code as the trigger.
 *
 * Retrying is safe because the first attempt never reached our controller
 * (it was short-circuited by the CSRF filter); no refresh-token rotation
 * happened, so no token reuse is possible on the retry.
 */
async function doRefreshWithCsrfBootstrap(): Promise<RefreshedAuth | null> {
  const firstHadXsrf = readCookie("XSRF-TOKEN") != null;
  const first = await doRefresh();
  if (first.auth) return first.auth;
  // Retry exactly once when the first attempt plausibly failed on CSRF:
  // we either started without an XSRF-TOKEN cookie or the server actively
  // expired ours, AND the server has now (re-)set one. Both 401 and 403
  // are valid bootstrap triggers — see the Kotlindoc above for the two
  // scenarios that produce each.
  const nowHasXsrf = readCookie("XSRF-TOKEN") != null;
  const looksLikeCsrfBootstrap = first.status === 401 || first.status === 403;
  if (!firstHadXsrf && nowHasXsrf && looksLikeCsrfBootstrap) {
    const second = await doRefresh();
    return second.auth;
  }
  return null;
}

interface RefreshAttempt {
  auth: RefreshedAuth | null;
  status: number;
}

async function doRefresh(): Promise<RefreshAttempt> {
  const headers: Record<string, string> = {
    "Content-Type": "application/json",
  };
  const xsrf = readCookie("XSRF-TOKEN");
  if (xsrf) {
    headers["X-XSRF-TOKEN"] = xsrf;
  }
  const response = await fetch("/api/v1/auth/refresh", {
    method: "POST",
    credentials: "include",
    headers,
  });
  if (!response.ok) {
    return { auth: null, status: response.status };
  }
  const data = await response.json();
  if (typeof data.accessToken !== "string" || typeof data.userId !== "string") {
    return { auth: null, status: response.status };
  }
  return {
    auth: {
      accessToken: data.accessToken,
      userId: data.userId,
      displayName:
        typeof data.displayName === "string" && data.displayName
          ? data.displayName
          : typeof data.username === "string"
            ? data.username
            : data.userId,
      username: typeof data.username === "string" ? data.username : null,
      email: typeof data.email === "string" ? data.email : "",
      source: data.source === "OIDC" ? "OIDC" : "LOCAL",
      passwordChangeRequired: data.passwordChangeRequired === true,
      isSuperadmin: data.isSuperadmin === true,
    },
    status: response.status,
  };
}

function readCookie(name: string): string | null {
  const prefix = `${name}=`;
  for (const part of document.cookie.split("; ")) {
    if (part.startsWith(prefix)) {
      return decodeURIComponent(part.slice(prefix.length));
    }
  }
  return null;
}
