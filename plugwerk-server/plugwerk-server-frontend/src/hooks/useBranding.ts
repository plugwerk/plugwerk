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
import { useEffect, useState } from "react";

export type BrandingSlot = "logo-light" | "logo-dark" | "logomark";

const BUNDLED: Record<BrandingSlot, string> = {
  "logo-light": "/logo-light.svg",
  "logo-dark": "/logo-dark.svg",
  logomark: "/logomark.svg",
};

/**
 * Custom DOM event name fired by the admin branding UI after a
 * successful upload / reset. `useBranding` listens for it and
 * re-probes the public endpoint so the top-bar logo and favicon
 * swap without a page reload.
 */
export const BRANDING_CHANGED_EVENT = "plugwerk:branding-changed";

/**
 * Notify every `useBranding` subscriber that an upload or reset just
 * happened. Called by the admin tile so consumers do not need to know
 * the event-name convention.
 */
export function notifyBrandingChanged(): void {
  if (typeof window !== "undefined") {
    window.dispatchEvent(new Event(BRANDING_CHANGED_EVENT));
  }
}

/**
 * Resolves the URL the UI should render for a branding slot (#254).
 *
 * Probes the public endpoint with an `Image()` element rather than a
 * `fetch()` HEAD — Chrome's HTTP cache holds onto HEAD 404 responses
 * long enough to break the "upload, see the new logo" happy path.
 * Image elements bypass that layer; on success we swap to the public
 * URL, on `onerror` we keep the bundled fallback.
 *
 * Listens for [BRANDING_CHANGED_EVENT] so a Reset or Upload performed
 * elsewhere on the page triggers a re-probe immediately. The probe URL
 * always carries a `?v=` query parameter so browsers that cached an
 * older response under the bare `/api/v1/branding/{slot}` URL (when
 * the endpoint still advertised `immutable, max-age=1y`) miss the
 * cache and revalidate against the server (#530).
 */
export function useBranding(slot: BrandingSlot): string {
  const fallback = BUNDLED[slot];
  const [url, setUrl] = useState(fallback);
  const [version, setVersion] = useState(0);

  // Re-probe whenever the admin UI tells us the slot changed.
  useEffect(() => {
    const handler = () => setVersion((v) => v + 1);
    window.addEventListener(BRANDING_CHANGED_EVENT, handler);
    return () => window.removeEventListener(BRANDING_CHANGED_EVENT, handler);
  }, []);

  useEffect(() => {
    let cancelled = false;
    const candidate = `/api/v1/branding/${slot}?v=${version}`;
    const probe = new Image();
    probe.onload = () => {
      if (!cancelled) setUrl(candidate);
    };
    probe.onerror = () => {
      if (!cancelled) setUrl(fallback);
    };
    probe.src = candidate;
    return () => {
      cancelled = true;
    };
  }, [slot, fallback, version]);

  return url;
}
