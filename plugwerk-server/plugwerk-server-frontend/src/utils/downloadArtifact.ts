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
import { useAuthStore } from "../stores/authStore";

/**
 * Module-scope allow-list of extra hostnames the bearer may be attached to
 * (ADR-0027 / #294 — TS-003). Same-origin is always allowed; everything else is
 * rejected unless explicitly listed. Populated once by the frontend bootstrap
 * from `GET /api/v1/config`; tests inject values via [setDownloadAllowedHosts].
 */
let allowedHosts: ReadonlySet<string> = new Set();

export function setDownloadAllowedHosts(hosts: readonly string[]): void {
  allowedHosts = new Set(
    hosts.map((h) => h.toLowerCase().trim()).filter(Boolean),
  );
}

export function getDownloadAllowedHosts(): ReadonlySet<string> {
  return allowedHosts;
}

/**
 * Result of an origin/allow-list decision. `attachBearer=true` means the target is
 * trusted; `false` means the fetch proceeds unauthenticated (e.g. a public CDN URL).
 */
export interface DownloadDecision {
  attachBearer: boolean;
  resolvedUrl: URL;
}

/**
 * Decide whether the caller-supplied `url` is safe to send the bearer to.
 *
 *   - Same-origin (host + scheme + port match `window.location.origin`) ⇒ attach.
 *   - Target hostname present in [allowedHosts] (case-insensitive) ⇒ attach.
 *   - Everything else ⇒ do **not** attach; fetch proceeds without credentials.
 *
 * Relative URLs (`/api/v1/...`) resolve to `window.location.origin` and therefore
 * always count as same-origin.
 */
export function decideDownload(url: string): DownloadDecision {
  const origin =
    typeof window !== "undefined" && window.location
      ? window.location.origin
      : "http://localhost";
  const resolvedUrl = new URL(url, origin);
  const isSameOrigin = resolvedUrl.origin === origin;
  const isAllowListed = allowedHosts.has(resolvedUrl.hostname.toLowerCase());
  return {
    attachBearer: isSameOrigin || isAllowListed,
    resolvedUrl,
  };
}

/**
 * Downloads a release artifact, attaching the bearer only when the target URL is
 * same-origin or explicitly allow-listed. Uses `fetch` + blob URL to trigger a
 * browser download with the correct filename.
 */
export async function downloadArtifact(
  url: string,
  filename: string,
): Promise<void> {
  const { attachBearer, resolvedUrl } = decideDownload(url);
  const headers: Record<string, string> = {
    Accept: "application/octet-stream",
  };
  if (attachBearer) {
    const token = useAuthStore.getState().accessToken;
    if (token) {
      headers["Authorization"] = `Bearer ${token}`;
    }
  }
  const response = await fetch(resolvedUrl.toString(), { headers });
  if (!response.ok) {
    let message = `Download failed (${response.status})`;
    try {
      const body = await response.json();
      if (body.message) message = body.message;
    } catch {
      /* response may not be JSON */
    }
    throw new Error(message);
  }
  const blob = await response.blob();
  const blobUrl = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = blobUrl;
  a.download = filename;
  a.style.display = "none";
  document.body.appendChild(a);
  a.click();
  // Delay cleanup so the browser has time to start the download
  setTimeout(() => {
    document.body.removeChild(a);
    URL.revokeObjectURL(blobUrl);
  }, 100);
}
