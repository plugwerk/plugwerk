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
 * Resolves the URL the UI should render for a branding slot (#254).
 *
 * Probes the public endpoint with an `Image()` element rather than a
 * `fetch()` HEAD — Chrome's HTTP cache holds onto HEAD 404 responses
 * long enough to break the "upload, see the new logo" happy path.
 * Image elements bypass that layer; on success we swap to the public
 * URL, on `onerror` we keep the bundled fallback.
 */
export function useBranding(slot: BrandingSlot): string {
  const fallback = BUNDLED[slot];
  const [url, setUrl] = useState(fallback);

  useEffect(() => {
    let cancelled = false;
    const candidate = `/api/v1/branding/${slot}`;
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
  }, [slot, fallback]);

  return url;
}
