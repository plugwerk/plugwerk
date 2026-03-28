// SPDX-License-Identifier: AGPL-3.0
// Copyright (C) 2026 devtank42 GmbH
import { describe, it, expect } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { renderWithRouter } from '../../test/renderWithTheme'
import { PluginCard } from './PluginCard'
import type { PluginDto } from '../../api/generated/model'

const basePlugin: PluginDto = {
  id: 'uuid-1',
  pluginId: 'auth-plugin',
  name: 'Auth Plugin',
  description: 'Handles authentication for your application.',
  author: 'ACME Corp',
  status: 'active',
  latestRelease: {
    id: 'rel-1',
    pluginId: 'auth-plugin',
    version: '1.2.3',
    status: 'published',
  },
  downloadCount: 1500,
  tags: ['auth', 'security', 'oauth'],
  updatedAt: '2026-01-15T10:00:00Z',
}

describe('PluginCard', () => {
  it('renders the plugin name', () => {
    renderWithRouter(<PluginCard plugin={basePlugin} namespace="acme" />)
    expect(screen.getByText('Auth Plugin')).toBeInTheDocument()
  })

  it('renders the version badge', () => {
    renderWithRouter(<PluginCard plugin={basePlugin} namespace="acme" />)
    expect(screen.getByText('v1.2.3')).toBeInTheDocument()
  })

  it('renders the description', () => {
    renderWithRouter(<PluginCard plugin={basePlugin} namespace="acme" />)
    expect(screen.getByText('Handles authentication for your application.')).toBeInTheDocument()
  })

  it('renders the author', () => {
    renderWithRouter(<PluginCard plugin={basePlugin} namespace="acme" />)
    expect(screen.getByText('ACME Corp')).toBeInTheDocument()
  })

  it('falls back to namespace when author is not set', () => {
    const plugin = { ...basePlugin, author: undefined }
    renderWithRouter(<PluginCard plugin={plugin} namespace="acme" />)
    expect(screen.getByText('acme')).toBeInTheDocument()
  })

  it('renders tags up to 4', () => {
    const plugin = { ...basePlugin, tags: ['a', 'b', 'c', 'd', 'e'] }
    renderWithRouter(<PluginCard plugin={plugin} namespace="acme" />)
    expect(screen.getByText('a')).toBeInTheDocument()
    expect(screen.getByText('d')).toBeInTheDocument()
    expect(screen.queryByText('e')).not.toBeInTheDocument()
  })

  it('does not render tags section when tags are empty', () => {
    const plugin = { ...basePlugin, tags: [] }
    renderWithRouter(<PluginCard plugin={plugin} namespace="acme" />)
    expect(screen.queryByLabelText('Tags')).not.toBeInTheDocument()
  })

  it('renders download count', () => {
    renderWithRouter(<PluginCard plugin={basePlugin} namespace="acme" />)
    expect(screen.getByText('1.5k')).toBeInTheDocument()
  })

  it('renders "0" when download count is missing', () => {
    const plugin = { ...basePlugin, downloadCount: undefined }
    renderWithRouter(<PluginCard plugin={plugin} namespace="acme" />)
    expect(screen.getByText('0')).toBeInTheDocument()
  })

  it('links to the plugin detail page', () => {
    renderWithRouter(<PluginCard plugin={basePlugin} namespace="acme" />)
    const link = screen.getByRole('listitem')
    expect(link).toBeInTheDocument()
  })

  it('shows deprecated badge for archived plugins', () => {
    const plugin = { ...basePlugin, status: 'archived' as const }
    renderWithRouter(<PluginCard plugin={plugin} namespace="acme" />)
    expect(screen.getByText('Deprecated')).toBeInTheDocument()
  })

  it('does not show deprecated badge for active plugins', () => {
    renderWithRouter(<PluginCard plugin={basePlugin} namespace="acme" />)
    expect(screen.queryByText('Deprecated')).not.toBeInTheDocument()
  })

  it('renders "—" when updatedAt is not set', () => {
    const plugin = { ...basePlugin, updatedAt: undefined }
    renderWithRouter(<PluginCard plugin={plugin} namespace="acme" />)
    expect(screen.getByText('—')).toBeInTheDocument()
  })

  it('does not render version badge when latestRelease is absent', () => {
    const plugin = { ...basePlugin, latestRelease: undefined }
    renderWithRouter(<PluginCard plugin={plugin} namespace="acme" />)
    expect(screen.queryByText(/^v/)).not.toBeInTheDocument()
  })

  it('shows tooltip with full name when name is overflowing', async () => {
    const user = userEvent.setup()
    renderWithRouter(<PluginCard plugin={basePlugin} namespace="acme" />)

    const nameEl = screen.getByText('Auth Plugin')
    Object.defineProperty(nameEl, 'scrollWidth', { configurable: true, value: 300 })
    Object.defineProperty(nameEl, 'clientWidth', { configurable: true, value: 100 })
    // Trigger ResizeObserver callback by dispatching a resize
    window.dispatchEvent(new Event('resize'))

    await user.hover(nameEl)
    await waitFor(() => {
      expect(screen.getAllByText('Auth Plugin').length).toBeGreaterThanOrEqual(1)
    })
  })

  it('shows tooltip with full description when description is overflowing', async () => {
    const user = userEvent.setup()
    renderWithRouter(<PluginCard plugin={basePlugin} namespace="acme" />)

    const descEl = screen.getByText('Handles authentication for your application.')
    Object.defineProperty(descEl, 'scrollHeight', { configurable: true, value: 200 })
    Object.defineProperty(descEl, 'clientHeight', { configurable: true, value: 50 })
    window.dispatchEvent(new Event('resize'))

    await user.hover(descEl)
    await waitFor(() => {
      expect(screen.getAllByText('Handles authentication for your application.').length).toBeGreaterThanOrEqual(1)
    })
  })
})
