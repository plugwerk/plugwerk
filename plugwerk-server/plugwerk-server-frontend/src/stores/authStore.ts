// SPDX-License-Identifier: AGPL-3.0
// Copyright (C) 2026 devtank42 GmbH
import { create } from 'zustand'

interface AuthState {
  accessToken: string | null
  username: string | null
  namespace: string
  isAuthenticated: boolean

  login: (username: string, password: string) => Promise<void>
  logout: () => void
  setNamespace: (ns: string) => void

  // Legacy alias kept for ProfileSettingsPage compatibility
  apiKey: string | null
}

export const useAuthStore = create<AuthState>((set, get) => ({
  accessToken: localStorage.getItem('pw-access-token'),
  username: localStorage.getItem('pw-username'),
  namespace: localStorage.getItem('pw-namespace') ?? 'default',
  isAuthenticated: !!localStorage.getItem('pw-access-token'),

  get apiKey() {
    return get().accessToken
  },

  async login(username: string, password: string) {
    const response = await fetch('/api/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username, password }),
    })
    if (!response.ok) {
      throw new Error('Invalid credentials')
    }
    const data = await response.json()
    localStorage.setItem('pw-access-token', data.accessToken)
    localStorage.setItem('pw-username', username)
    set({ accessToken: data.accessToken, username, isAuthenticated: true })
  },

  logout() {
    localStorage.removeItem('pw-access-token')
    localStorage.removeItem('pw-username')
    set({ accessToken: null, username: null, isAuthenticated: false })
  },

  setNamespace(ns) {
    localStorage.setItem('pw-namespace', ns)
    set({ namespace: ns })
  },
}))
