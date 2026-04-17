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
import axios from "axios";
import { create } from "zustand";
import { adminSettingsApi } from "../api/config";
import type { ApplicationSettingDto } from "../api/generated/model/application-setting-dto";
import { useConfigStore } from "./configStore";

interface SettingsState {
  readonly settings: ApplicationSettingDto[];
  readonly loaded: boolean;
  readonly loading: boolean;
  readonly saving: boolean;
  readonly error: string | null;
  load: () => Promise<void>;
  update: (patch: Record<string, string>) => Promise<void>;
  reset: () => void;
}

function extractErrorMessage(error: unknown): string {
  if (axios.isAxiosError(error)) {
    const message = (error.response?.data as { message?: string } | undefined)
      ?.message;
    if (typeof message === "string" && message.length > 0) {
      return message;
    }
    return error.message;
  }
  if (error instanceof Error) {
    return error.message;
  }
  return "Unknown error";
}

export const useSettingsStore = create<SettingsState>((set) => ({
  settings: [],
  loaded: false,
  loading: false,
  saving: false,
  error: null,

  async load() {
    set({ loading: true, error: null });
    try {
      const response = await adminSettingsApi.listApplicationSettings();
      set({
        settings: response.data.settings,
        loaded: true,
        loading: false,
      });
    } catch (err) {
      set({
        loaded: true,
        loading: false,
        error: extractErrorMessage(err),
      });
      throw err;
    }
  },

  async update(patch) {
    set({ saving: true, error: null });
    try {
      const response = await adminSettingsApi.updateApplicationSettings({
        applicationSettingsUpdateRequest: { settings: patch },
      });
      set({ settings: response.data.settings, saving: false });
      // Values surfaced by the public /api/v1/config endpoint
      // (defaultTimezone, maxFileSizeMb) are cached in useConfigStore; refresh
      // them so the new admin settings take effect immediately across the UI
      // without a page reload.
      void useConfigStore.getState().fetchConfig({ force: true });
    } catch (err) {
      set({ saving: false, error: extractErrorMessage(err) });
      throw err;
    }
  },

  reset() {
    set({
      settings: [],
      loaded: false,
      loading: false,
      saving: false,
      error: null,
    });
  },
}));
