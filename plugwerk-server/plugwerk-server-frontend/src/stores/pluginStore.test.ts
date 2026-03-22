// SPDX-License-Identifier: AGPL-3.0
// Copyright (C) 2026 devtank42 GmbH
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { act } from 'react'

// Mock the catalogApi before importing the store
vi.mock('../api/config', () => ({
  catalogApi: {
    listPlugins: vi.fn(),
  },
  managementApi: {},
  reviewsApi: {},
  updatesApi: {},
}))

import { usePluginStore } from './pluginStore'
import { catalogApi } from '../api/config'

const mockCatalogApi = catalogApi as unknown as { listPlugins: ReturnType<typeof vi.fn> }

const defaultFilters = {
  search: '',
  category: '',
  tag: '',
  status: '',
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
      act(() => { usePluginStore.getState().setFilters({ category: 'analytics' }) })
      expect(usePluginStore.getState().filters.category).toBe('analytics')
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
        usePluginStore.getState().setFilters({ category: 'auth', sort: 'downloads,desc' })
      })
      const { filters } = usePluginStore.getState()
      expect(filters.category).toBe('auth')
      expect(filters.sort).toBe('downloads,desc')
    })
  })

  describe('resetFilters', () => {
    it('resets all filters to defaults', () => {
      usePluginStore.setState({
        filters: { search: 'foo', category: 'bar', tag: 'baz', status: 'active', sort: 'downloads,desc', page: 5, size: 12 },
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
        filters: { ...defaultFilters, search: 'cache', category: 'storage', page: 1, size: 12 },
      })

      await act(async () => {
        await usePluginStore.getState().fetchPlugins('acme')
      })

      expect(mockCatalogApi.listPlugins).toHaveBeenCalledWith(
        expect.objectContaining({
          ns: 'acme',
          q: 'cache',
          category: 'storage',
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
      expect(callArgs.category).toBeUndefined()
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
})
