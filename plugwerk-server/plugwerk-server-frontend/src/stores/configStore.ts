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

interface ConfigState {
  readonly version: string;
  readonly maxFileSizeMb: number;
  readonly defaultTimezone: string;
  readonly loaded: boolean;
  /**
   * Fetches `/api/v1/config`. Skips the request when the store is already
   * loaded unless `{ force: true }` is passed — call with `force` after any
   * mutation that changes `application_setting` so cached values
   * (defaultTimezone, maxFileSizeMb) stay in sync with the DB.
   */
  fetchConfig: (options?: { force?: boolean }) => Promise<void>;
}

export const useConfigStore = create<ConfigState>((set, get) => ({
  version: "…",
  maxFileSizeMb: 100,
  defaultTimezone: "UTC",
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
        loaded: true,
      });
    } catch {
      set({ version: "unknown", loaded: true });
    }
  },
}));
