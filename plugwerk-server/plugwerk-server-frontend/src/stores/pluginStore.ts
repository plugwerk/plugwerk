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
import type { PluginDto, PluginPagedResponse } from '../api/generated/model'
import type { ListPluginsStatusEnum } from '../api/generated/api/catalog-api'
import { catalogApi, axiosInstance } from '../api/config'

interface PluginFilters {
  search: string
  tag: string
  status: string
  version: string
  sort: string
  page: number
  size: number
}

interface PluginState {
  plugins: PluginDto[]
  totalElements: number
  totalPages: number
  pendingReviewPluginCount: number | null
  availableTags: string[]
  loading: boolean
  error: string | null
  filters: PluginFilters

  setFilters: (partial: Partial<PluginFilters>) => void
  resetFilters: () => void
  fetchPlugins: (namespace: string) => Promise<void>
  fetchTags: (namespace: string) => Promise<void>
}

const defaultFilters: PluginFilters = {
  search: '',
  tag: '',
  status: '',
  version: '',
  sort: 'name,asc',
  page: 0,
  size: 24,
}

export const usePluginStore = create<PluginState>((set, get) => ({
  plugins: [],
  totalElements: 0,
  totalPages: 0,
  pendingReviewPluginCount: null,
  availableTags: [],
  loading: false,
  error: null,
  filters: { ...defaultFilters },

  setFilters(partial) {
    set((s) => ({
      filters: { ...s.filters, ...partial, page: partial.page ?? 0 },
    }))
  },

  resetFilters() {
    set({ filters: { ...defaultFilters } })
  },

  async fetchPlugins(namespace: string) {
    set({ loading: true, error: null })
    try {
      const { filters } = get()
      const response = await catalogApi.listPlugins({
        ns: namespace,
        page: filters.page,
        size: filters.size,
        sort: filters.sort,
        q: filters.search || undefined,
        tag: filters.tag || undefined,
        status: (filters.status || undefined) as ListPluginsStatusEnum | undefined,
        version: filters.version || undefined,
      })
      const data: PluginPagedResponse = response.data
      set({
        plugins: data.content,
        totalElements: data.totalElements,
        totalPages: data.totalPages,
        pendingReviewPluginCount: data.pendingReviewPluginCount ?? null,
        loading: false,
      })
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Failed to load plugins'
      set({ loading: false, error: message })
    }
  },

  async fetchTags(namespace: string) {
    try {
      const response = await axiosInstance.get<string[]>(`/namespaces/${namespace}/tags`)
      set({ availableTags: response.data })
    } catch {
      set({ availableTags: [] })
    }
  },
}))
