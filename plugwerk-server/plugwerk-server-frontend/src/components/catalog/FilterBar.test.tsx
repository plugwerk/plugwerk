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
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { renderWithRouter } from '../../test/renderWithTheme'
import { FilterBar } from './FilterBar'
import { usePluginStore } from '../../stores/pluginStore'

vi.mock('../../api/config', () => ({
  catalogApi: { listPlugins: vi.fn().mockResolvedValue({ data: { content: [], totalElements: 0, totalPages: 0 } }) },
  managementApi: {},
  reviewsApi: {},
  updatesApi: {},
}))

const defaultFilters = {
  search: '',
  tag: '',
  status: '',
  version: '',
  sort: 'name,asc',
  page: 0,
  size: 24,
}

describe('FilterBar', () => {
  beforeEach(() => {
    usePluginStore.setState({
      plugins: [],
      totalElements: 0,
      totalPages: 0,
      loading: false,
      error: null,
      filters: { ...defaultFilters },
    })
  })

  it('renders tag, status, compatibility and sort selects', () => {
    renderWithRouter(
      <FilterBar view="card" onViewChange={vi.fn()} namespace="acme" />,
    )
    expect(screen.getByPlaceholderText(/all tags/i)).toBeInTheDocument()
    expect(screen.getByRole('combobox', { name: /filter by status/i })).toBeInTheDocument()
    expect(screen.getByRole('combobox', { name: /filter by compatibility/i })).toBeInTheDocument()
    expect(screen.getByRole('combobox', { name: /sort order/i })).toBeInTheDocument()
    expect(screen.getAllByRole('combobox')).toHaveLength(4)
  })

  it('does not show reset button when no active filters', () => {
    renderWithRouter(
      <FilterBar view="card" onViewChange={vi.fn()} namespace="acme" />,
    )
    expect(screen.queryByRole('button', { name: /reset filters/i })).not.toBeInTheDocument()
  })

  it('shows reset button when tag filter is active', () => {
    usePluginStore.setState({ filters: { ...defaultFilters, tag: 'auth' } })
    renderWithRouter(
      <FilterBar view="card" onViewChange={vi.fn()} namespace="acme" />,
    )
    expect(screen.getByRole('button', { name: /reset filters/i })).toBeInTheDocument()
  })

  it('renders view toggle with card and list buttons', () => {
    renderWithRouter(
      <FilterBar view="card" onViewChange={vi.fn()} namespace="acme" />,
    )
    expect(screen.getByRole('button', { name: /card view/i })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /list view/i })).toBeInTheDocument()
  })

  it('calls onViewChange when list view is selected', async () => {
    const user = userEvent.setup()
    const onViewChange = vi.fn()
    renderWithRouter(
      <FilterBar view="card" onViewChange={onViewChange} namespace="acme" />,
    )
    await user.click(screen.getByRole('button', { name: /list view/i }))
    expect(onViewChange).toHaveBeenCalledWith('list')
  })

  it('calls onViewChange when card view is selected', async () => {
    const user = userEvent.setup()
    const onViewChange = vi.fn()
    renderWithRouter(
      <FilterBar view="list" onViewChange={onViewChange} namespace="acme" />,
    )
    await user.click(screen.getByRole('button', { name: /card view/i }))
    expect(onViewChange).toHaveBeenCalledWith('card')
  })

  it('renders the filter/sort group with accessible label', () => {
    renderWithRouter(
      <FilterBar view="card" onViewChange={vi.fn()} namespace="acme" />,
    )
    expect(screen.getByRole('group', { name: /filter and sort options/i })).toBeInTheDocument()
  })

  it('resets filters when reset button is clicked', async () => {
    const user = userEvent.setup()
    const resetFiltersMock = vi.fn()
    usePluginStore.setState({
      filters: { ...defaultFilters, tag: 'auth' },
      setFilters: vi.fn(),
      fetchPlugins: vi.fn().mockResolvedValue(undefined),
      resetFilters: resetFiltersMock,
    })
    renderWithRouter(
      <FilterBar view="card" onViewChange={vi.fn()} namespace="acme" />,
    )
    await user.click(screen.getByRole('button', { name: /reset filters/i }))
    expect(resetFiltersMock).not.toHaveBeenCalled() // FilterBar calls setFilters, not resetFilters
    // setFilters was called with empty values
    const setFiltersMock = usePluginStore.getState().setFilters as ReturnType<typeof vi.fn>
    expect(setFiltersMock).toHaveBeenCalled()
  })

  it('shows correct current sort value', () => {
    usePluginStore.setState({ filters: { ...defaultFilters, sort: 'downloadCount,desc' } })
    renderWithRouter(
      <FilterBar view="card" onViewChange={vi.fn()} namespace="acme" />,
    )
    // The selected value is rendered inside the combobox
    expect(screen.getByText('Most Downloads')).toBeInTheDocument()
  })
})
