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
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { act } from 'react'

// Mock the catalogApi before importing the store
vi.mock('../api/config', () => ({
  catalogApi: {
    listPlugins: vi.fn(),
  },
  axiosInstance: {
    get: vi.fn(),
  },
  managementApi: {},
  reviewsApi: {},
  updatesApi: {},
}))

import { usePluginStore } from './pluginStore'
import { catalogApi, axiosInstance } from '../api/config'

const mockCatalogApi = catalogApi as unknown as { listPlugins: ReturnType<typeof vi.fn> }
const mockAxiosInstance = axiosInstance as unknown as { get: ReturnType<typeof vi.fn> }

const defaultFilters = {
  search: '',
  tag: '',
  status: '',
  version: '',
  sort: 'name,asc',
  page: 0,
  size: 24,
}

describe('usePluginStore', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    usePluginStore.setState({
      plugins: [],
      totalElements: 0,
      totalPages: 0,
      loading: false,
      error: null,
      filters: { ...defaultFilters },
    })
  })

  describe('initial state', () => {
    it('starts with empty plugins array', () => {
      expect(usePluginStore.getState().plugins).toHaveLength(0)
    })

    it('starts not loading', () => {
      expect(usePluginStore.getState().loading).toBe(false)
    })

    it('starts with no error', () => {
      expect(usePluginStore.getState().error).toBeNull()
    })

    it('starts with default filters', () => {
      expect(usePluginStore.getState().filters).toEqual(defaultFilters)
    })
  })

  describe('setFilters', () => {
    it('merges partial filters', () => {
      act(() => { usePluginStore.getState().setFilters({ tag: 'analytics' }) })
      expect(usePluginStore.getState().filters.tag).toBe('analytics')
      expect(usePluginStore.getState().filters.sort).toBe('name,asc')
    })

    it('resets page to 0 when other filters change', () => {
      usePluginStore.setState({ filters: { ...defaultFilters, page: 3 } })
      act(() => { usePluginStore.getState().setFilters({ search: 'auth' }) })
      expect(usePluginStore.getState().filters.page).toBe(0)
    })

    it('keeps explicit page value when provided', () => {
      act(() => { usePluginStore.getState().setFilters({ page: 2 }) })
      expect(usePluginStore.getState().filters.page).toBe(2)
    })

    it('can update multiple filters at once', () => {
      act(() => {
        usePluginStore.getState().setFilters({ tag: 'auth', sort: 'downloads,desc' })
      })
      const { filters } = usePluginStore.getState()
      expect(filters.tag).toBe('auth')
      expect(filters.sort).toBe('downloads,desc')
    })
  })

  describe('resetFilters', () => {
    it('resets all filters to defaults', () => {
      usePluginStore.setState({
        filters: { search: 'foo', tag: 'baz', status: 'active', version: '', sort: 'downloads,desc', page: 5, size: 12 },
      })
      act(() => { usePluginStore.getState().resetFilters() })
      expect(usePluginStore.getState().filters).toEqual(defaultFilters)
    })
  })

  describe('fetchPlugins', () => {
    const mockPlugins = [
      { pluginId: 'auth-plugin', name: 'Auth Plugin', description: 'Auth stuff' },
      { pluginId: 'cache-plugin', name: 'Cache Plugin', description: 'Cache stuff' },
    ]

    it('sets loading to true while fetching', async () => {
      mockCatalogApi.listPlugins.mockReturnValue(new Promise(() => {})) // never resolves
      let loadingDuringFetch = false

      const unsubscribe = usePluginStore.subscribe((state) => {
        if (state.loading) loadingDuringFetch = true
      })

      act(() => { void usePluginStore.getState().fetchPlugins('acme') })
      expect(loadingDuringFetch).toBe(true)
      unsubscribe()
    })

    it('populates plugins on success', async () => {
      mockCatalogApi.listPlugins.mockResolvedValue({
        data: { content: mockPlugins, totalElements: 2, totalPages: 1 },
      })

      await act(async () => {
        await usePluginStore.getState().fetchPlugins('acme')
      })

      const state = usePluginStore.getState()
      expect(state.plugins).toHaveLength(2)
      expect(state.totalElements).toBe(2)
      expect(state.totalPages).toBe(1)
      expect(state.loading).toBe(false)
      expect(state.error).toBeNull()
    })

    it('passes filters to the API call', async () => {
      mockCatalogApi.listPlugins.mockResolvedValue({
        data: { content: [], totalElements: 0, totalPages: 0 },
      })
      usePluginStore.setState({
        filters: { ...defaultFilters, search: 'cache', tag: 'storage', page: 1, size: 12 },
      })

      await act(async () => {
        await usePluginStore.getState().fetchPlugins('acme')
      })

      expect(mockCatalogApi.listPlugins).toHaveBeenCalledWith(
        expect.objectContaining({
          ns: 'acme',
          q: 'cache',
          tag: 'storage',
          page: 1,
          size: 12,
        }),
      )
    })

    it('omits empty filter values from API call', async () => {
      mockCatalogApi.listPlugins.mockResolvedValue({
        data: { content: [], totalElements: 0, totalPages: 0 },
      })

      await act(async () => {
        await usePluginStore.getState().fetchPlugins('acme')
      })

      const callArgs = mockCatalogApi.listPlugins.mock.calls[0][0]
      expect(callArgs.q).toBeUndefined()
      expect(callArgs.tag).toBeUndefined()
      expect(callArgs.status).toBeUndefined()
    })

    it('sets error on failure', async () => {
      mockCatalogApi.listPlugins.mockRejectedValue(new Error('Network error'))

      await act(async () => {
        await usePluginStore.getState().fetchPlugins('acme')
      })

      const state = usePluginStore.getState()
      expect(state.error).toBe('Network error')
      expect(state.loading).toBe(false)
      expect(state.plugins).toHaveLength(0)
    })

    it('handles non-Error rejections', async () => {
      mockCatalogApi.listPlugins.mockRejectedValue('Something went wrong')

      await act(async () => {
        await usePluginStore.getState().fetchPlugins('acme')
      })

      expect(usePluginStore.getState().error).toBe('Failed to load plugins')
    })
  })

  describe('fetchTags', () => {
    it('populates availableTags on success', async () => {
      mockAxiosInstance.get.mockResolvedValue({ data: ['auth', 'cache', 'security'] })

      await act(async () => {
        await usePluginStore.getState().fetchTags('acme')
      })

      expect(mockAxiosInstance.get).toHaveBeenCalledWith('/namespaces/acme/tags')
      expect(usePluginStore.getState().availableTags).toEqual(['auth', 'cache', 'security'])
    })

    it('sets empty array on failure', async () => {
      mockAxiosInstance.get.mockRejectedValue(new Error('Network error'))

      await act(async () => {
        await usePluginStore.getState().fetchTags('acme')
      })

      expect(usePluginStore.getState().availableTags).toEqual([])
    })
  })
})
