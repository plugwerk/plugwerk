// SPDX-License-Identifier: AGPL-3.0
// Copyright (C) 2026 devtank42 GmbH
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { renderWithRouter } from '../../test/renderWithTheme'
import { TopBar } from './TopBar'
import { useAuthStore } from '../../stores/authStore'
import { useNamespaceStore } from '../../stores/namespaceStore'
import { useUiStore } from '../../stores/uiStore'

const mockNavigate = vi.fn()

vi.mock('react-router-dom', async (importOriginal) => {
  const actual = await importOriginal<typeof import('react-router-dom')>()
  return { ...actual, useNavigate: () => mockNavigate }
})

vi.mock('../../api/config', () => ({
  axiosInstance: { get: vi.fn().mockResolvedValue({ data: [] }) },
  catalogApi: {},
  managementApi: {},
  reviewsApi: {},
  updatesApi: {},
}))

describe('TopBar', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    useAuthStore.setState({ accessToken: 'tok', username: 'alice', isAuthenticated: true, namespace: 'acme' })
    useNamespaceStore.setState({
      namespaces: [{ slug: 'acme', ownerOrg: 'ACME' }, { slug: 'beta', ownerOrg: 'Beta Inc' }],
      loading: false,
      error: null,
    })
    useUiStore.setState({ themeMode: 'light', toasts: [], searchQuery: '' })
  })

  it('renders the namespace dropdown', () => {
    renderWithRouter(<TopBar />)
    expect(screen.getByLabelText('Select namespace')).toBeInTheDocument()
  })

  it('navigates to catalog page of new namespace on namespace change', async () => {
    const user = userEvent.setup()
    renderWithRouter(<TopBar />)

    await user.click(screen.getByLabelText('Select namespace'))
    const option = await screen.findByRole('option', { name: 'beta' })
    await user.click(option)

    expect(mockNavigate).toHaveBeenCalledWith('/namespaces/beta/plugins')
  })

  it('updates auth store namespace on namespace change', async () => {
    const user = userEvent.setup()
    renderWithRouter(<TopBar />)

    await user.click(screen.getByLabelText('Select namespace'))
    const option = await screen.findByRole('option', { name: 'beta' })
    await user.click(option)

    expect(useAuthStore.getState().namespace).toBe('beta')
  })
})
