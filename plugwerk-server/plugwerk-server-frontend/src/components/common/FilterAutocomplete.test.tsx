// SPDX-License-Identifier: AGPL-3.0
// Copyright (C) 2026 devtank42 GmbH
import { describe, it, expect, vi } from 'vitest'
import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { renderWithTheme } from '../../test/renderWithTheme'
import { FilterAutocomplete } from './FilterAutocomplete'

describe('FilterAutocomplete', () => {
  const tags = ['cli', 'demo', 'export', 'monitoring', 'security', 'ui']

  it('renders with placeholder when no value selected', () => {
    renderWithTheme(
      <FilterAutocomplete options={tags} value="" onChange={vi.fn()} placeholder="All Tags" />,
    )
    expect(screen.getByPlaceholderText('All Tags')).toBeInTheDocument()
  })

  it('shows options when focused', async () => {
    const user = userEvent.setup()
    renderWithTheme(
      <FilterAutocomplete options={tags} value="" onChange={vi.fn()} placeholder="All Tags" />,
    )

    await user.click(screen.getByPlaceholderText('All Tags'))

    const listbox = screen.getByRole('listbox')
    expect(within(listbox).getByText('cli')).toBeInTheDocument()
    expect(within(listbox).getByText('security')).toBeInTheDocument()
  })

  it('filters options when typing', async () => {
    const user = userEvent.setup()
    renderWithTheme(
      <FilterAutocomplete options={tags} value="" onChange={vi.fn()} placeholder="All Tags" />,
    )

    await user.click(screen.getByPlaceholderText('All Tags'))
    await user.type(screen.getByPlaceholderText('All Tags'), 'sec')

    const listbox = screen.getByRole('listbox')
    expect(within(listbox).getByText('security')).toBeInTheDocument()
    expect(within(listbox).queryByText('cli')).not.toBeInTheDocument()
  })

  it('calls onChange with selected value', async () => {
    const onChange = vi.fn()
    const user = userEvent.setup()
    renderWithTheme(
      <FilterAutocomplete options={tags} value="" onChange={onChange} placeholder="All Tags" />,
    )

    await user.click(screen.getByPlaceholderText('All Tags'))
    await user.click(screen.getByText('export'))

    expect(onChange).toHaveBeenCalledWith('export')
  })

  it('calls onChange with empty string when cleared', async () => {
    const onChange = vi.fn()
    const user = userEvent.setup()
    renderWithTheme(
      <FilterAutocomplete options={tags} value="cli" onChange={onChange} placeholder="All Tags" />,
    )

    const clearButton = screen.getByTitle('Clear')
    await user.click(clearButton)

    expect(onChange).toHaveBeenCalledWith('')
  })

  it('shows loading indicator', () => {
    renderWithTheme(
      <FilterAutocomplete options={[]} value="" onChange={vi.fn()} placeholder="All Tags" loading />,
    )

    expect(screen.getByRole('progressbar')).toBeInTheDocument()
  })
})
