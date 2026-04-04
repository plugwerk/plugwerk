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
