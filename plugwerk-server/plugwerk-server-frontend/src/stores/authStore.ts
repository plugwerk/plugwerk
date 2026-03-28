// SPDX-License-Identifier: AGPL-3.0
// Copyright (C) 2026 devtank42 GmbH
import { create } from 'zustand'
import { namespaceMembersApi } from '../api/config'
import type { NamespaceRole } from '../api/generated/model'

interface AuthState {
  accessToken: string | null
  username: string | null
  namespace: string
  isAuthenticated: boolean
  passwordChangeRequired: boolean
  namespaceRole: NamespaceRole | null

  login: (username: string, password: string) => Promise<void>
  logout: () => void
  setNamespace: (ns: string) => void
  clearPasswordChangeRequired: () => void
  fetchNamespaceRole: (ns: string) => Promise<void>

  // Legacy alias kept for ProfileSettingsPage compatibility
  apiKey: string | null
}

export const useAuthStore = create<AuthState>((set, get) => ({
  accessToken: localStorage.getItem('pw-access-token'),
  username: localStorage.getItem('pw-username'),
  namespace: localStorage.getItem('pw-namespace') ?? 'default',
  isAuthenticated: !!localStorage.getItem('pw-access-token'),
  passwordChangeRequired: localStorage.getItem('pw-password-change-required') === 'true',
  namespaceRole: null,

  get apiKey() {
    return get().accessToken
  },

  async login(username: string, password: string) {
    const response = await fetch('/api/v1/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username, password }),
    })
    if (!response.ok) {
      throw new Error('Invalid credentials')
    }
    const data = await response.json()
    const passwordChangeRequired = data.passwordChangeRequired === true
    localStorage.setItem('pw-access-token', data.accessToken)
    localStorage.setItem('pw-username', username)
    if (passwordChangeRequired) {
      localStorage.setItem('pw-password-change-required', 'true')
    } else {
      localStorage.removeItem('pw-password-change-required')
    }
    set({
      accessToken: data.accessToken,
      username,
      isAuthenticated: true,
      passwordChangeRequired,
    })
  },

  logout() {
    localStorage.removeItem('pw-access-token')
    localStorage.removeItem('pw-username')
    localStorage.removeItem('pw-password-change-required')
    set({ accessToken: null, username: null, isAuthenticated: false, passwordChangeRequired: false, namespaceRole: null })
  },

  setNamespace(ns) {
    localStorage.setItem('pw-namespace', ns)
    set({ namespace: ns, namespaceRole: null })
  },

  clearPasswordChangeRequired() {
    localStorage.removeItem('pw-password-change-required')
    set({ passwordChangeRequired: false })
  },

  async fetchNamespaceRole(ns: string) {
    if (!get().isAuthenticated) {
      set({ namespaceRole: null })
      return
    }
    try {
      const response = await namespaceMembersApi.getMyMembership({ ns })
      set({ namespaceRole: response.data.role })
    } catch {
      set({ namespaceRole: null })
    }
  },
}))
