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
import { ApiReferenceReact } from "@scalar/api-reference-react";
import "@scalar/api-reference-react/style.css";
import { useEffect, useMemo } from "react";
import { useUiStore } from "../stores/uiStore";

export function ApiDocsPage() {
  const themeMode = useUiStore((s) => s.themeMode);
  const isDark = themeMode === "dark";

  // Scalar uses a module-level colorMode singleton and reads localStorage
  // on init, so the darkMode prop alone is not enough to control the mode
  // after the first render. The actual styling is driven by 'dark-mode' /
  // 'light-mode' CSS classes on document.body — we sync them here.
  useEffect(() => {
    localStorage.setItem("colorMode", isDark ? "dark" : "light");
    document.body.classList.toggle("dark-mode", isDark);
    document.body.classList.toggle("light-mode", !isDark);
    return () => {
      document.body.classList.remove("dark-mode", "light-mode");
    };
  }, [isDark]);

  const configuration = useMemo(
    () => ({
      url: "/api-docs/openapi.yaml",
      darkMode: isDark,
      hideDownloadButton: false,
      layout: "modern" as const,
    }),
    [isDark],
  );

  return <ApiReferenceReact key={themeMode} configuration={configuration} />;
}
