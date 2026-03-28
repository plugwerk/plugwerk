// SPDX-License-Identifier: AGPL-3.0
// Copyright (C) 2026 devtank42 GmbH
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { renderWithRouter } from '../test/renderWithTheme'
import { LoginPage } from './LoginPage'
import { useAuthStore } from '../stores/authStore'

describe('LoginPage', () => {
  beforeEach(() => {
    useAuthStore.setState({ accessToken: null, username: null, isAuthenticated: false })
    localStorage.clear()
    vi.restoreAllMocks()
  })

  it('renders the sign in heading', () => {
    renderWithRouter(<LoginPage />)
    expect(screen.getByText('Welcome back')).toBeInTheDocument()
  })

  it('renders username and password inputs', () => {
    renderWithRouter(<LoginPage />)
    expect(screen.getByLabelText(/username/i)).toBeInTheDocument()
    expect(screen.getByLabelText(/password/i)).toBeInTheDocument()
  })

  it('renders the sign in button', () => {
    renderWithRouter(<LoginPage />)
    expect(screen.getByRole('button', { name: /sign in/i })).toBeInTheDocument()
  })

  it('renders subtitle text', () => {
    renderWithRouter(<LoginPage />)
    expect(screen.getByText(/sign in to your account/i)).toBeInTheDocument()
  })

  it('does not show error alert initially', () => {
    renderWithRouter(<LoginPage />)
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('shows validation error when fields are empty and form is submitted', async () => {
    const user = userEvent.setup()
    renderWithRouter(<LoginPage />)
    await user.click(screen.getByRole('button', { name: /sign in/i }))
    expect(screen.getByRole('alert')).toBeInTheDocument()
    expect(screen.getByText(/please enter username and password/i)).toBeInTheDocument()
  })

  it('calls login with username and password when form is submitted', async () => {
    const mockLogin = vi.fn().mockResolvedValue(undefined)
    useAuthStore.setState({ login: mockLogin } as never)

    const user = userEvent.setup()
    renderWithRouter(<LoginPage />)
    await user.type(screen.getByLabelText(/username/i), 'alice')
    await user.type(screen.getByLabelText(/password/i), 'secret')
    await user.click(screen.getByRole('button', { name: /sign in/i }))

    await waitFor(() => {
      expect(mockLogin).toHaveBeenCalledWith('alice', 'secret')
    })
  })

  it('trims whitespace from username before submitting', async () => {
    const mockLogin = vi.fn().mockResolvedValue(undefined)
    useAuthStore.setState({ login: mockLogin } as never)

    const user = userEvent.setup()
    renderWithRouter(<LoginPage />)
    await user.type(screen.getByLabelText(/username/i), '  alice  ')
    await user.type(screen.getByLabelText(/password/i), 'secret')
    await user.click(screen.getByRole('button', { name: /sign in/i }))

    await waitFor(() => {
      expect(mockLogin).toHaveBeenCalledWith('alice', 'secret')
    })
  })

  it('shows error message when login fails', async () => {
    const mockLogin = vi.fn().mockRejectedValue(new Error('Unauthorized'))
    useAuthStore.setState({ login: mockLogin } as never)

    const user = userEvent.setup()
    renderWithRouter(<LoginPage />)
    await user.type(screen.getByLabelText(/username/i), 'wrong')
    await user.type(screen.getByLabelText(/password/i), 'wrong')
    await user.click(screen.getByRole('button', { name: /sign in/i }))

    await waitFor(() => {
      expect(screen.getByText(/invalid username or password/i)).toBeInTheDocument()
    })
  })

  it('clears validation error when close button is clicked', async () => {
    const user = userEvent.setup()
    renderWithRouter(<LoginPage />)
    await user.click(screen.getByRole('button', { name: /sign in/i }))
    expect(screen.getByRole('alert')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: /close/i }))
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })
})
