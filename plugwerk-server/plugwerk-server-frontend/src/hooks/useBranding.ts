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
 * Returns the bundled fallback first so the page does not flash an
 * empty image, then probes the public endpoint and swaps to the
 * custom asset if one exists.
 *
 * Cache-busting is unnecessary here — the public endpoint sets
 * `Cache-Control: immutable` and the dashboard is the only place that
 * mutates it. After a re-upload the operator triggers a refresh
 * implicitly (next page load).
 */
export function useBranding(slot: BrandingSlot): string {
  const fallback = BUNDLED[slot];
  const [url, setUrl] = useState(fallback);

  useEffect(() => {
    let cancelled = false;
    fetch(`/api/v1/branding/${slot}`, { method: "HEAD" })
      .then((res) => {
        if (cancelled) return;
        if (res.ok) {
          setUrl(`/api/v1/branding/${slot}`);
        } else {
          setUrl(fallback);
        }
      })
      .catch(() => {
        if (!cancelled) setUrl(fallback);
      });
    return () => {
      cancelled = true;
    };
  }, [slot, fallback]);

  return url;
}
