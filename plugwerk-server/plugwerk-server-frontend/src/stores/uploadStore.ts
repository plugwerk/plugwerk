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

export type FileUploadStatus = "pending" | "uploading" | "success" | "failed";

export interface FileUploadEntry {
  readonly id: string;
  readonly file: File;
  readonly fileName: string;
  readonly status: FileUploadStatus;
  readonly progress: number;
  readonly errorMessage: string | null;
}

interface UploadState {
  readonly entries: readonly FileUploadEntry[];
  readonly panelVisible: boolean;
  readonly maxFileSizeMb: number;

  addFiles: (files: readonly File[]) => void;
  updateEntry: (
    id: string,
    patch: Partial<
      Pick<FileUploadEntry, "status" | "progress" | "errorMessage">
    >,
  ) => void;
  clearCompleted: () => void;
  dismissPanel: () => void;
  showPanel: () => void;
  setMaxFileSizeMb: (mb: number) => void;
  reset: () => void;
}

const VALID_EXTENSIONS = [".jar", ".zip"];

function isValidPluginFile(file: File): boolean {
  const name = file.name.toLowerCase();
  return VALID_EXTENSIONS.some((ext) => name.endsWith(ext));
}

export const useUploadStore = create<UploadState>((set) => ({
  entries: [],
  panelVisible: false,
  maxFileSizeMb: 100,

  addFiles(files) {
    const newEntries: FileUploadEntry[] = files
      .filter(isValidPluginFile)
      .map((file) => ({
        id: crypto.randomUUID(),
        file,
        fileName: file.name,
        status: "pending" as const,
        progress: 0,
        errorMessage: null,
      }));

    if (newEntries.length === 0) return;

    set((state) => ({
      entries: [...state.entries, ...newEntries],
      panelVisible: true,
    }));
  },

  updateEntry(id, patch) {
    set((state) => ({
      entries: state.entries.map((entry) =>
        entry.id === id ? { ...entry, ...patch } : entry,
      ),
    }));
  },

  clearCompleted() {
    set((state) => {
      const remaining = state.entries.filter(
        (e) => e.status !== "success" && e.status !== "failed",
      );
      return {
        entries: remaining,
        panelVisible: remaining.length > 0,
      };
    });
  },

  dismissPanel() {
    set({ panelVisible: false, entries: [] });
  },

  showPanel() {
    set({ panelVisible: true });
  },

  setMaxFileSizeMb(mb) {
    set({ maxFileSizeMb: mb });
  },

  reset() {
    set({ entries: [], panelVisible: false });
  },
}));
