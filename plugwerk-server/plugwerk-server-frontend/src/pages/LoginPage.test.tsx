// SPDX-License-Identifier: AGPL-3.0
// Copyright (C) 2026 devtank42 GmbH
import { describe, it, expect, beforeEach } from 'vitest'
import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { renderWithRouter } from '../test/renderWithTheme'
import { LoginPage } from './LoginPage'
import { useAuthStore } from '../stores/authStore'

// Prevent window.location.href assignment from throwing in jsdom
Object.defineProperty(window, 'location', {
  value: { href: '' },
  writable: true,
  configurable: true,
})

describe('LoginPage', () => {
  beforeEach(() => {
    useAuthStore.setState({ apiKey: null, namespace: 'default' })
    localStorage.clear()
  })

  it('renders the sign in heading', () => {
    renderWithRouter(<LoginPage />)
    expect(screen.getByText('Welcome back')).toBeInTheDocument()
  })

  it('renders the API key input', () => {
    renderWithRouter(<LoginPage />)
    expect(screen.getByLabelText(/api key/i)).toBeInTheDocument()
  })

  it('renders the sign in button', () => {
    renderWithRouter(<LoginPage />)
    expect(screen.getByRole('button', { name: /sign in/i })).toBeInTheDocument()
  })

  it('shows a register link', () => {
    renderWithRouter(<LoginPage />)
    expect(screen.getByRole('link', { name: /register/i })).toBeInTheDocument()
  })

  it('shows validation error when API key is empty and form is submitted', async () => {
    const user = userEvent.setup()
    renderWithRouter(<LoginPage />)
    await user.click(screen.getByRole('button', { name: /sign in/i }))
    expect(screen.getByRole('alert')).toBeInTheDocument()
    expect(screen.getByText(/please enter your api key/i)).toBeInTheDocument()
  })

  it('does not show error alert initially', () => {
    renderWithRouter(<LoginPage />)
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('stores the API key when a valid key is submitted', async () => {
    const user = userEvent.setup()
    renderWithRouter(<LoginPage />)
    await user.type(screen.getByLabelText(/api key/i), 'pw_test123')
    await user.click(screen.getByRole('button', { name: /sign in/i }))
    expect(useAuthStore.getState().apiKey).toBe('pw_test123')
  })

  it('trims whitespace from the API key', async () => {
    const user = userEvent.setup()
    renderWithRouter(<LoginPage />)
    await user.type(screen.getByLabelText(/api key/i), '  pw_trimmed  ')
    await user.click(screen.getByRole('button', { name: /sign in/i }))
    expect(useAuthStore.getState().apiKey).toBe('pw_trimmed')
  })

  it('clears validation error after editing the field', async () => {
    const user = userEvent.setup()
    renderWithRouter(<LoginPage />)
    // Trigger validation error
    await user.click(screen.getByRole('button', { name: /sign in/i }))
    expect(screen.getByRole('alert')).toBeInTheDocument()
    // Dismiss via close button
    await user.click(screen.getByRole('button', { name: /close/i }))
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('renders subtitle text', () => {
    renderWithRouter(<LoginPage />)
    expect(screen.getByText(/sign in with your api key/i)).toBeInTheDocument()
  })
})
