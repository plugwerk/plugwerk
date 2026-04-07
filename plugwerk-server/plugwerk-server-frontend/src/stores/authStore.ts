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
import { create } from 'zustand'
import { namespaceMembersApi, namespacesApi } from '../api/config'
import type { NamespaceRole } from '../api/generated/model'

interface AuthState {
  accessToken: string | null
  username: string | null
  namespace: string | null | undefined
  isAuthenticated: boolean
  passwordChangeRequired: boolean
  isSuperadmin: boolean
  namespaceRole: NamespaceRole | null

  login: (username: string, password: string) => Promise<void>
  logout: () => void
  setNamespace: (ns: string) => void
  initNamespace: () => Promise<void>
  clearPasswordChangeRequired: () => void
  fetchNamespaceRole: (ns: string) => Promise<void>

  // Legacy alias kept for ProfileSettingsPage compatibility
  apiKey: string | null
}

export const useAuthStore = create<AuthState>((set, get) => ({
  accessToken: localStorage.getItem('pw-access-token'),
  username: localStorage.getItem('pw-username'),
  namespace: undefined,
  isAuthenticated: !!localStorage.getItem('pw-access-token'),
  passwordChangeRequired: localStorage.getItem('pw-password-change-required') === 'true',
  isSuperadmin: localStorage.getItem('pw-is-superadmin') === 'true',
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
    const isSuperadmin = data.isSuperadmin === true
    localStorage.setItem('pw-access-token', data.accessToken)
    localStorage.setItem('pw-username', username)
    if (passwordChangeRequired) {
      localStorage.setItem('pw-password-change-required', 'true')
    } else {
      localStorage.removeItem('pw-password-change-required')
    }
    if (isSuperadmin) {
      localStorage.setItem('pw-is-superadmin', 'true')
    } else {
      localStorage.removeItem('pw-is-superadmin')
    }
    set({
      accessToken: data.accessToken,
      username,
      isAuthenticated: true,
      passwordChangeRequired,
      isSuperadmin,
    })
  },

  logout() {
    const token = localStorage.getItem('pw-access-token')
    if (token) {
      fetch('/api/v1/auth/logout', {
        method: 'POST',
        headers: { Authorization: `Bearer ${token}` },
      }).catch(() => {})
    }
    localStorage.removeItem('pw-access-token')
    localStorage.removeItem('pw-username')
    localStorage.removeItem('pw-password-change-required')
    localStorage.removeItem('pw-is-superadmin')
    localStorage.removeItem('pw-namespace')
    set({ accessToken: null, username: null, namespace: undefined, isAuthenticated: false, passwordChangeRequired: false, isSuperadmin: false, namespaceRole: null })
  },

  setNamespace(ns) {
    localStorage.setItem('pw-namespace', ns)
    set({ namespace: ns, namespaceRole: null })
  },

  async initNamespace() {
    try {
      const res = await namespacesApi.listNamespaces()
      const slugs = res.data.map((ns) => ns.slug)
      const stored = localStorage.getItem('pw-namespace')
      if (stored && slugs.includes(stored)) {
        set({ namespace: stored })
      } else if (slugs.length > 0) {
        localStorage.setItem('pw-namespace', slugs[0])
        set({ namespace: slugs[0] })
      } else {
        localStorage.removeItem('pw-namespace')
        set({ namespace: null })
      }
    } catch {
      localStorage.removeItem('pw-namespace')
      set({ namespace: null })
    }
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
