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
import { useEffect } from "react";
import { RouterProvider } from "react-router-dom";
import { router } from "./router";
import { AuthHydrationBoundary } from "./components/auth/AuthHydrationBoundary";
import { useConfigStore } from "./stores/configStore";
import { useBranding } from "./hooks/useBranding";

export default function App() {
  // Keep document.title in sync with the operator-configured site name (#234).
  // The static <title> in index.html stays as the SEO/no-JS fallback; this
  // effect overrides it once /config has resolved. Footer already triggers
  // fetchConfig() — the loaded guard in the store prevents a duplicate
  // request, so reading siteName here is enough.
  const siteName = useConfigStore((s) => s.siteName);
  useEffect(() => {
    document.title = `${siteName} – Plugin Catalog`;
  }, [siteName]);

  // Swap the favicon to the operator-uploaded logomark when present
  // (#254). Falls back to the bundled SVG via useBranding's default.
  //
  // Browsers cache favicons in a separate, very aggressive cache that
  // ignores HTTP Cache-Control AND often ignores `link.href` mutations
  // on an existing element (Chrome in particular only re-checks the
  // favicon at specific lifecycle events, not on attribute changes).
  // The reliable cross-browser workaround is to remove every existing
  // icon link and append a fresh one — the browser then registers the
  // new element as a brand-new favicon target and refetches (#530).
  const logomark = useBranding("logomark");
  useEffect(() => {
    document
      .querySelectorAll(
        "link[rel='icon'], link[rel='shortcut icon'], link[rel='apple-touch-icon']",
      )
      .forEach((el) => el.remove());
    const link = document.createElement("link");
    link.rel = "icon";
    link.href = logomark;
    document.head.appendChild(link);
  }, [logomark]);

  return (
    <AuthHydrationBoundary>
      <RouterProvider router={router} />
    </AuthHydrationBoundary>
  );
}
