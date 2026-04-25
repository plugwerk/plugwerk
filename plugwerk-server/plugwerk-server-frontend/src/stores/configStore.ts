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
import { create } from "zustand";
import { axiosInstance } from "../api/config";

/**
 * One OIDC provider button shown on the login page (issue #79).
 *
 * Mirrors the `OidcProviderLoginInfo` schema from the backend OpenAPI spec.
 * `loginUrl` is always a same-origin relative path of the form
 * `/oauth2/authorization/{id}` — Spring Security's OAuth2 client filter
 * intercepts it and starts the Authorization Code Flow with PKCE. The
 * frontend just navigates the browser there.
 */
export interface OidcProviderLoginInfo {
  readonly id: string;
  readonly name: string;
  readonly loginUrl: string;
}

interface ConfigState {
  readonly version: string;
  readonly maxFileSizeMb: number;
  readonly defaultTimezone: string;
  /** OIDC providers currently enabled by the operator. Empty when none. */
  readonly oidcProviders: readonly OidcProviderLoginInfo[];
  readonly loaded: boolean;
  /**
   * Fetches `/api/v1/config`. Skips the request when the store is already
   * loaded unless `{ force: true }` is passed — call with `force` after any
   * mutation that changes `application_setting` so cached values
   * (defaultTimezone, maxFileSizeMb) stay in sync with the DB. Also call
   * with `force` after an admin enables/disables an OIDC provider so the
   * login-page button list reflects the change.
   */
  fetchConfig: (options?: { force?: boolean }) => Promise<void>;
}

export const useConfigStore = create<ConfigState>((set, get) => ({
  version: "…",
  maxFileSizeMb: 100,
  defaultTimezone: "UTC",
  oidcProviders: [],
  loaded: false,

  async fetchConfig(options) {
    if (get().loaded && !options?.force) return;
    try {
      const res = await axiosInstance.get("/config");
      set({
        version: res.data?.version ?? "unknown",
        maxFileSizeMb:
          typeof res.data?.upload?.maxFileSizeMb === "number"
            ? res.data.upload.maxFileSizeMb
            : 100,
        defaultTimezone:
          typeof res.data?.general?.defaultTimezone === "string" &&
          res.data.general.defaultTimezone.length > 0
            ? res.data.general.defaultTimezone
            : "UTC",
        oidcProviders: parseOidcProviders(res.data?.auth?.oidcProviders),
        loaded: true,
      });
    } catch {
      set({ version: "unknown", oidcProviders: [], loaded: true });
    }
  },
}));

/**
 * Defensive parser — `/config` is public and the response shape is owned by
 * the backend, but keeping the runtime narrowing local prevents one bad
 * response from rendering broken `<a href={undefined}>` buttons.
 */
function parseOidcProviders(raw: unknown): readonly OidcProviderLoginInfo[] {
  if (!Array.isArray(raw)) return [];
  return raw.flatMap((entry: unknown) => {
    if (typeof entry !== "object" || entry === null) return [];
    const e = entry as Record<string, unknown>;
    if (
      typeof e.id !== "string" ||
      typeof e.name !== "string" ||
      typeof e.loginUrl !== "string"
    ) {
      return [];
    }
    return [{ id: e.id, name: e.name, loginUrl: e.loginUrl }];
  });
}
