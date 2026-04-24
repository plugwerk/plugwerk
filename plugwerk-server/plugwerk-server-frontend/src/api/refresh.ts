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
  username: string;
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
 * needs to tell the client about it. On a fresh page reload the jar may be
 * empty of `XSRF-TOKEN` even though the session is otherwise valid, because
 * our login endpoint is not wired into Spring's auth filter chain (so the
 * `CsrfAuthenticationStrategy` never fires and never rotates a fresh token
 * into the response).
 *
 * Consequence: the very first `POST /auth/refresh` after reload has no
 * `X-XSRF-TOKEN` header, Spring's `CsrfFilter` rejects it with 401, and as a
 * side effect it writes a freshly-generated `XSRF-TOKEN` cookie to the 401
 * response. If we retry exactly once, the retry will have the cookie and
 * will pass the CSRF check.
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
  // we started without an XSRF-TOKEN cookie and the server has now set one
  // (as a side effect of its 401). Skip the retry if nothing changed.
  const nowHasXsrf = readCookie("XSRF-TOKEN") != null;
  if (!firstHadXsrf && nowHasXsrf && first.status === 401) {
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
  if (typeof data.accessToken !== "string") {
    return { auth: null, status: response.status };
  }
  // The server encodes username in the JWT subject; decode locally to avoid a
  // second round-trip. Only the `sub` claim is read and never trusted for
  // authorization — the server already validated the token.
  const subject = decodeJwtSubject(data.accessToken) ?? "";
  return {
    auth: {
      accessToken: data.accessToken,
      username: subject,
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

function decodeJwtSubject(jwt: string): string | null {
  const parts = jwt.split(".");
  if (parts.length < 2) return null;
  try {
    const payload = JSON.parse(
      atob(parts[1].replace(/-/g, "+").replace(/_/g, "/")),
    );
    return typeof payload.sub === "string" ? payload.sub : null;
  } catch {
    return null;
  }
}
