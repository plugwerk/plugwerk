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
 * 2. Browser local timezone
 * 3. `"UTC"` as a last resort
 *
 * The server-wide `general.timezone` setting drives the initial default that is
 * seeded into the user's preference the first time they save their profile; it
 * is not consulted here because the admin settings store is not loaded for
 * non-admin users.
 */
export function useEffectiveTimezone(): string {
  const userTimezone = useUserSettingsStore((s) => s.settings.timezone);
  if (userTimezone && userTimezone.length > 0) {
    return userTimezone;
  }
  return browserLocalTimezone();
}
