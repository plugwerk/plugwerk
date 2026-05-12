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

  return (
    <AuthHydrationBoundary>
      <RouterProvider router={router} />
    </AuthHydrationBoundary>
  );
}
