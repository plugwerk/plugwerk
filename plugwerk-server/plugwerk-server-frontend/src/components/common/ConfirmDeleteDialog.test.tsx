// SPDX-License-Identifier: AGPL-3.0
// Copyright (C) 2026 devtank42 GmbH
import { describe, it, expect, vi } from 'vitest'
import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { renderWithTheme } from '../../test/renderWithTheme'
import { ConfirmDeleteDialog } from './ConfirmDeleteDialog'

describe('ConfirmDeleteDialog', () => {
  const defaultProps = {
    open: true,
    title: 'Delete Plugin',
    message: 'Are you sure?',
    onConfirm: vi.fn(),
    onCancel: vi.fn(),
  }

  it('renders title and message', () => {
    renderWithTheme(<ConfirmDeleteDialog {...defaultProps} />)
    expect(screen.getByText('Delete Plugin')).toBeInTheDocument()
    expect(screen.getByText('Are you sure?')).toBeInTheDocument()
  })

  it('calls onConfirm when delete button is clicked', async () => {
    const onConfirm = vi.fn()
    const user = userEvent.setup()
    renderWithTheme(<ConfirmDeleteDialog {...defaultProps} onConfirm={onConfirm} />)

    await user.click(screen.getByRole('button', { name: /confirm-delete/i }))
    expect(onConfirm).toHaveBeenCalledOnce()
  })

  it('calls onCancel when cancel button is clicked', async () => {
    const onCancel = vi.fn()
    const user = userEvent.setup()
    renderWithTheme(<ConfirmDeleteDialog {...defaultProps} onCancel={onCancel} />)

    await user.click(screen.getByRole('button', { name: /cancel/i }))
    expect(onCancel).toHaveBeenCalledOnce()
  })

  it('disables buttons when loading', () => {
    renderWithTheme(<ConfirmDeleteDialog {...defaultProps} loading />)

    expect(screen.getByRole('button', { name: /cancel/i })).toBeDisabled()
    expect(screen.getByRole('button', { name: /confirm-delete/i })).toBeDisabled()
    expect(screen.getByText('Deleting\u2026')).toBeInTheDocument()
  })

  it('does not render when open is false', () => {
    renderWithTheme(<ConfirmDeleteDialog {...defaultProps} open={false} />)
    expect(screen.queryByText('Delete Plugin')).not.toBeInTheDocument()
  })
})
