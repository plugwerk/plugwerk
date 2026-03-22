// SPDX-License-Identifier: AGPL-3.0
// Copyright (C) 2026 devtank42 GmbH
import { create } from 'zustand'
import { axiosInstance } from '../api/config'

interface NamespaceSummary {
  slug: string
  ownerOrg: string
}

interface NamespaceState {
  namespaces: NamespaceSummary[]
  loading: boolean
  error: string | null
  fetchNamespaces: () => Promise<void>
}

export const useNamespaceStore = create<NamespaceState>((set) => ({
  namespaces: [],
  loading: false,
  error: null,

  async fetchNamespaces() {
    set({ loading: true, error: null })
    try {
      const response = await axiosInstance.get<NamespaceSummary[]>('/namespaces')
      set({ namespaces: response.data, loading: false })
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Failed to load namespaces'
      set({ loading: false, error: message })
    }
  },
}))
