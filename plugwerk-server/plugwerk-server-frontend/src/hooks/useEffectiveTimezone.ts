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
import { useConfigStore } from "../stores/configStore";
import { useUserSettingsStore } from "../stores/userSettingsStore";

function browserLocalTimezone(): string {
  try {
    const resolved = Intl.DateTimeFormat().resolvedOptions().timeZone;
    if (resolved && resolved.length > 0) return resolved;
  } catch {
    // fallthrough
  }
  return "UTC";
}

/**
 * Resolves the effective display timezone for the current user.
 *
 * Fallback chain:
 * 1. User preference (`userSettings.timezone` when non-empty)
 * 2. Server-wide default (`general.default_timezone` from the public
 *    `/api/v1/config` endpoint, cached in `useConfigStore`)
 * 3. Browser local timezone
 * 4. `"UTC"` as a last resort
 *
 * The hook triggers a `fetchConfig` on first use so rendering does not depend
 * on another component having populated the public config first. Subsequent
 * lookups are served from the cached store.
 */
export function useEffectiveTimezone(): string {
  const userTimezone = useUserSettingsStore((s) => s.settings.timezone);
  const systemTimezone = useConfigStore((s) => s.defaultTimezone);
  const configLoaded = useConfigStore((s) => s.loaded);
  const fetchConfig = useConfigStore((s) => s.fetchConfig);

  useEffect(() => {
    if (!configLoaded) void fetchConfig();
  }, [configLoaded, fetchConfig]);

  if (userTimezone && userTimezone.length > 0) {
    return userTimezone;
  }
  if (configLoaded && systemTimezone.length > 0) {
    return systemTimezone;
  }
  return browserLocalTimezone();
}
