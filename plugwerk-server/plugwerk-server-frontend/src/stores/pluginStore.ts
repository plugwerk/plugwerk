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
import type { PluginFilters } from "../api/hooks/usePlugins";

/**
 * UI-only store for catalog filter form state.
 *
 * Server-authoritative data (plugin list, tags, pending-review counts) moved
 * to TanStack Query under `src/api/hooks/usePlugins.ts` — see ADR-0028 /
 * #328 (TS-007). Filter form state stays in Zustand because it is pure UI
 * state that multiple components bind to and mutate.
 */
interface PluginUiState {
  filters: PluginFilters;
  setFilters: (partial: Partial<PluginFilters>) => void;
  resetFilters: () => void;
}

const defaultFilters: PluginFilters = {
  search: "",
  tag: "",
  status: "",
  version: "",
  sort: "name,asc",
  page: 0,
  size: 24,
};

export const usePluginStore = create<PluginUiState>((set) => ({
  filters: { ...defaultFilters },

  setFilters(partial) {
    set((s) => ({
      filters: { ...s.filters, ...partial, page: partial.page ?? 0 },
    }));
  },

  resetFilters() {
    set({ filters: { ...defaultFilters } });
  },
}));
