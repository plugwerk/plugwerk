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
import type { PaletteMode } from "@mui/material";

export interface ToastItem {
  id: string;
  type: "info" | "success" | "warning" | "error";
  title?: string;
  message?: string;
}

interface UiState {
  themeMode: PaletteMode;
  searchQuery: string;
  toasts: ToastItem[];
  uploadModalOpen: boolean;

  toggleTheme: () => void;
  setTheme: (mode: PaletteMode) => void;
  setSearchQuery: (q: string) => void;
  addToast: (toast: Omit<ToastItem, "id">) => void;
  removeToast: (id: string) => void;
  openUploadModal: () => void;
  closeUploadModal: () => void;
}

function resolveInitialTheme(): PaletteMode {
  const saved = localStorage.getItem("pw-theme");
  if (saved === "dark" || saved === "light") return saved;
  return window.matchMedia("(prefers-color-scheme: dark)").matches
    ? "dark"
    : "light";
}

export const useUiStore = create<UiState>((set, get) => ({
  themeMode: resolveInitialTheme(),
  searchQuery: "",
  toasts: [],
  uploadModalOpen: false,

  toggleTheme() {
    const next: PaletteMode = get().themeMode === "dark" ? "light" : "dark";
    localStorage.setItem("pw-theme", next);
    set({ themeMode: next });
  },

  setTheme(mode) {
    localStorage.setItem("pw-theme", mode);
    set({ themeMode: mode });
  },

  setSearchQuery(q) {
    set({ searchQuery: q });
  },

  addToast(toast) {
    const id = crypto.randomUUID();
    const item: ToastItem = { ...toast, id };
    set((s) => ({ toasts: [...s.toasts, item] }));
    setTimeout(() => get().removeToast(id), 4000);
  },

  removeToast(id) {
    set((s) => ({ toasts: s.toasts.filter((t) => t.id !== id) }));
  },

  openUploadModal() {
    set({ uploadModalOpen: true });
  },

  closeUploadModal() {
    set({ uploadModalOpen: false });
  },
}));
