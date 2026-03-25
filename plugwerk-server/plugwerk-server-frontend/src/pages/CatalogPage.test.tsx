// SPDX-License-Identifier: AGPL-3.0
// Copyright (C) 2026 devtank42 GmbH
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { screen } from '@testing-library/react'
import { renderWithRouterAt } from '../test/renderWithTheme'
import { CatalogPage } from './CatalogPage'
import { usePluginStore } from '../stores/pluginStore'
import { useAuthStore } from '../stores/authStore'
import { useUiStore } from '../stores/uiStore'
import type { PluginDto } from '../api/generated/model'

vi.mock('../api/config', () => ({
  catalogApi: {
    listPlugins: vi.fn().mockResolvedValue({ data: { content: [], totalElements: 0, totalPages: 0 } }),
  },
  managementApi: {},
  reviewsApi: {},
  updatesApi: {},
}))

const mockPlugin: PluginDto = {
  id: 'uuid-1',
  pluginId: 'auth-plugin',
  name: 'Auth Plugin',
  description: 'Authentication support.',
  author: 'ACME Corp',
  status: 'active',
  latestVersion: '1.0.0',
  downloadCount: 42,
  tags: ['auth'],
}

const defaultFilters = {
  search: '',
  category: '',
  tag: '',
  status: '',
  sort: 'name,asc',
  page: 0,
  size: 24,
}

describe('CatalogPage', () => {
  const noOpFetch = vi.fn().mockResolvedValue(undefined)

  const ROUTE_PATH = '/namespaces/:namespace/plugins'
  const INITIAL_PATH = '/namespaces/acme/plugins'

  function renderCatalog() {
    return renderWithRouterAt(<CatalogPage />, ROUTE_PATH, INITIAL_PATH)
  }

  beforeEach(() => {
    vi.clearAllMocks()
    useAuthStore.setState({ accessToken: null, namespace: 'acme' })
    useUiStore.setState({ searchQuery: '', toasts: [] })
    usePluginStore.setState({
      plugins: [],
      totalElements: 0,
      totalPages: 0,
      loading: false,
      error: null,
      filters: { ...defaultFilters },
      // Override fetchPlugins so useEffect calls don't reset our test state
      fetchPlugins: noOpFetch,
      setFilters: vi.fn(),
      resetFilters: vi.fn(),
    })
  })

  it('renders the page heading', () => {
    renderCatalog()
    expect(screen.getByText('Plugin Catalog')).toBeInTheDocument()
  })

  it('shows empty state when no plugins are available', () => {
    renderCatalog()
    expect(screen.getByText('No plugins found')).toBeInTheDocument()
  })

  it('shows loading skeleton when loading is true', () => {
    usePluginStore.setState({ loading: true })
    renderCatalog()
    expect(screen.getByLabelText('Loading plugins')).toBeInTheDocument()
  })

  it('does not show empty state while loading', () => {
    usePluginStore.setState({ loading: true })
    renderCatalog()
    expect(screen.queryByText('No plugins found')).not.toBeInTheDocument()
  })

  it('shows error alert when there is an error', () => {
    usePluginStore.setState({ error: 'Network error' })
    renderCatalog()
    expect(screen.getByText('Network error')).toBeInTheDocument()
  })

  it('does not show empty state when there is an error', () => {
    usePluginStore.setState({ error: 'Something failed', plugins: [] })
    renderCatalog()
    expect(screen.queryByText('No plugins found')).not.toBeInTheDocument()
  })

  it('renders plugin cards in card view', () => {
    usePluginStore.setState({ plugins: [mockPlugin], totalElements: 1, totalPages: 1 })
    renderCatalog()
    expect(screen.getByText('Auth Plugin')).toBeInTheDocument()
  })

  it('shows plugin count when not loading', () => {
    usePluginStore.setState({ plugins: [mockPlugin], totalElements: 42 })
    renderCatalog()
    expect(screen.getByText('42 plugins')).toBeInTheDocument()
  })

  it('does not show plugin count while loading', () => {
    usePluginStore.setState({ loading: true, totalElements: 42 })
    renderCatalog()
    expect(screen.queryByText('42 plugins')).not.toBeInTheDocument()
  })

  it('renders filter bar', () => {
    renderCatalog()
    expect(screen.getByRole('group', { name: /filter and sort options/i })).toBeInTheDocument()
  })

  it('shows list view when list toggle is clicked', async () => {
    const { default: userEvent } = await import('@testing-library/user-event')
    const user = userEvent.setup()
    usePluginStore.setState({ plugins: [mockPlugin], totalElements: 1, totalPages: 1 })
    renderCatalog()
    await user.click(screen.getByRole('button', { name: /list view/i }))
    expect(screen.getByRole('list', { name: /plugin list/i })).toBeInTheDocument()
  })

  it('syncs namespace from URL param into the auth store', async () => {
    renderWithRouterAt(<CatalogPage />, ROUTE_PATH, '/namespaces/other-ns/plugins')
    const { namespace } = useAuthStore.getState()
    expect(namespace).toBe('other-ns')
  })
})
