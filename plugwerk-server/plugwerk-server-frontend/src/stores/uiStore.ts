// SPDX-License-Identifier: AGPL-3.0
// Copyright (C) 2026 devtank42 GmbH
import { create } from 'zustand'
import type { PaletteMode } from '@mui/material'

export interface ToastItem {
  id: string
  type: 'info' | 'success' | 'warning' | 'error'
  title?: string
  message?: string
}

interface UiState {
  themeMode: PaletteMode
  searchQuery: string
  toasts: ToastItem[]
  uploadModalOpen: boolean

  toggleTheme: () => void
  setTheme: (mode: PaletteMode) => void
  setSearchQuery: (q: string) => void
  addToast: (toast: Omit<ToastItem, 'id'>) => void
  removeToast: (id: string) => void
  openUploadModal: () => void
  closeUploadModal: () => void
}

function resolveInitialTheme(): PaletteMode {
  const saved = localStorage.getItem('pw-theme')
  if (saved === 'dark' || saved === 'light') return saved
  return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'
}

export const useUiStore = create<UiState>((set, get) => ({
  themeMode: resolveInitialTheme(),
  searchQuery: '',
  toasts: [],
  uploadModalOpen: false,

  toggleTheme() {
    const next: PaletteMode = get().themeMode === 'dark' ? 'light' : 'dark'
    localStorage.setItem('pw-theme', next)
    set({ themeMode: next })
  },

  setTheme(mode) {
    localStorage.setItem('pw-theme', mode)
    set({ themeMode: mode })
  },

  setSearchQuery(q) {
    set({ searchQuery: q })
  },

  addToast(toast) {
    const id = crypto.randomUUID()
    const item: ToastItem = { ...toast, id }
    set((s) => ({ toasts: [...s.toasts, item] }))
    setTimeout(() => get().removeToast(id), 4000)
  },

  removeToast(id) {
    set((s) => ({ toasts: s.toasts.filter((t) => t.id !== id) }))
  },

  openUploadModal() {
    set({ uploadModalOpen: true })
  },

  closeUploadModal() {
    set({ uploadModalOpen: false })
  },
}))
