// SPDX-License-Identifier: AGPL-3.0
// Copyright (C) 2026 devtank42 GmbH
import { describe, it, expect } from 'vitest'
import { screen } from '@testing-library/react'
import { renderWithRouter } from '../../test/renderWithTheme'
import { PluginListRow } from './PluginListRow'
import type { PluginDto } from '../../api/generated/model'

const plugin: PluginDto = {
  id: 'uuid-1',
  pluginId: 'cache-plugin',
  name: 'Cache Plugin',
  author: 'Acme',
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

  it('renders author', () => {
    renderWithRouter(<PluginListRow plugin={plugin} namespace="acme" />)
    expect(screen.getByText('Acme')).toBeInTheDocument()
  })

  it('falls back to namespace when author is absent', () => {
    renderWithRouter(<PluginListRow plugin={{ ...plugin, author: undefined }} namespace="acme" />)
    expect(screen.getByText('acme')).toBeInTheDocument()
  })

  it('renders version badge', () => {
    renderWithRouter(<PluginListRow plugin={plugin} namespace="acme" />)
    expect(screen.getByText('v2.0.0')).toBeInTheDocument()
  })

  it('renders download count with k suffix', () => {
    renderWithRouter(<PluginListRow plugin={plugin} namespace="acme" />)
    expect(screen.getByText('5.0k')).toBeInTheDocument()
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
