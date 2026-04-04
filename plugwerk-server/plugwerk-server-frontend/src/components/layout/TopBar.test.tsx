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
