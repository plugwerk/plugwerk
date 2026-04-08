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
import { describe, it, expect } from 'vitest'
import { screen } from '@testing-library/react'
import { renderWithRouter } from '../../test/renderWithTheme'
import { PluginListRow } from './PluginListRow'
import type { PluginDto } from '../../api/generated/model'

const plugin: PluginDto = {
  id: 'uuid-1',
  pluginId: 'cache-plugin',
  name: 'Cache Plugin',
  provider: 'Acme',
  status: 'active',
  latestRelease: {
    id: 'rel-1',
    pluginId: 'cache-plugin',
    version: '2.0.0',
    status: 'published',
  },
  downloadCount: 5000,
  updatedAt: '2026-02-01T00:00:00Z',
}

describe('PluginListRow', () => {
  it('renders plugin name', () => {
    renderWithRouter(<PluginListRow plugin={plugin} namespace="acme" />)
    expect(screen.getByText('Cache Plugin')).toBeInTheDocument()
  })

  it('renders provider', () => {
    renderWithRouter(<PluginListRow plugin={plugin} namespace="acme" />)
    expect(screen.getByText('by Acme')).toBeInTheDocument()
  })

  it('falls back to namespace when provider is absent', () => {
    renderWithRouter(<PluginListRow plugin={{ ...plugin, provider: undefined }} namespace="acme" />)
    expect(screen.queryByText('Acme')).not.toBeInTheDocument()
  })

  it('renders version badge', () => {
    renderWithRouter(<PluginListRow plugin={plugin} namespace="acme" />)
    expect(screen.getByText('v2.0.0')).toBeInTheDocument()
  })

  it('renders download count with thousand separator', () => {
    renderWithRouter(<PluginListRow plugin={plugin} namespace="acme" />)
    expect(screen.getByText('5,000')).toBeInTheDocument()
  })

  it('renders "—" when updatedAt is absent', () => {
    renderWithRouter(<PluginListRow plugin={{ ...plugin, updatedAt: undefined }} namespace="acme" />)
    expect(screen.getByText('—')).toBeInTheDocument()
  })

  it('renders as a list item', () => {
    renderWithRouter(<PluginListRow plugin={plugin} namespace="acme" />)
    expect(screen.getByRole('listitem')).toBeInTheDocument()
  })
})
