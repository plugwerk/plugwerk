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
import { describe, it, expect, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { UploadProgressPanel } from './UploadProgressPanel'
import { useUploadStore } from '../../stores/uploadStore'

function createFile(name: string, size = 1024): File {
  return new File([new ArrayBuffer(size)], name, { type: 'application/java-archive' })
}

describe('UploadProgressPanel', () => {
  beforeEach(() => {
    useUploadStore.getState().reset()
  })

  it('does not render when panelVisible is false', () => {
    const { container } = render(<UploadProgressPanel />)
    expect(container.querySelector('[role="region"]')).toBeNull()
  })

  it('renders when files are added (panelVisible becomes true)', async () => {
    useUploadStore.getState().addFiles([createFile('plugin.jar')])
    render(<UploadProgressPanel />)
    await waitFor(() => {
      expect(screen.getByRole('region', { name: /upload progress/i })).toBeInTheDocument()
    })
  })

  it('shows file names in the entry list', () => {
    useUploadStore.getState().addFiles([createFile('alpha.jar'), createFile('beta.zip')])
    render(<UploadProgressPanel />)
    expect(screen.getByText('alpha.jar')).toBeInTheDocument()
    expect(screen.getByText('beta.zip')).toBeInTheDocument()
  })

  it('shows "Uploading N files" header while uploads are in progress', () => {
    useUploadStore.getState().addFiles([createFile('a.jar'), createFile('b.jar')])
    render(<UploadProgressPanel />)
    expect(screen.getByText('Uploading 2 files')).toBeInTheDocument()
  })

  it('shows completion header when all uploads finish', () => {
    useUploadStore.getState().addFiles([createFile('a.jar'), createFile('b.jar')])
    const [a, b] = useUploadStore.getState().entries
    useUploadStore.getState().updateEntry(a.id, { status: 'success', progress: 100 })
    useUploadStore.getState().updateEntry(b.id, { status: 'success', progress: 100 })

    render(<UploadProgressPanel />)
    expect(screen.getByText('Upload complete — 2 succeeded')).toBeInTheDocument()
  })

  it('shows failed count in header when some uploads fail', () => {
    useUploadStore.getState().addFiles([createFile('a.jar'), createFile('b.jar')])
    const [a, b] = useUploadStore.getState().entries
    useUploadStore.getState().updateEntry(a.id, { status: 'success', progress: 100 })
    useUploadStore.getState().updateEntry(b.id, { status: 'failed', errorMessage: 'Server error' })

    render(<UploadProgressPanel />)
    expect(screen.getByText('1 failed')).toBeInTheDocument()
  })

  it('shows error message for failed entries', () => {
    useUploadStore.getState().addFiles([createFile('bad.jar')])
    const id = useUploadStore.getState().entries[0].id
    useUploadStore.getState().updateEntry(id, { status: 'failed', errorMessage: 'Duplicate version' })

    render(<UploadProgressPanel />)
    expect(screen.getByText('Duplicate version')).toBeInTheDocument()
  })

  it('dismisses panel when close button is clicked', async () => {
    useUploadStore.getState().addFiles([createFile('plugin.jar')])
    render(<UploadProgressPanel />)

    const dismissBtn = screen.getByRole('button', { name: /dismiss/i })
    await userEvent.click(dismissBtn)

    const { entries, panelVisible } = useUploadStore.getState()
    expect(entries).toHaveLength(0)
    expect(panelVisible).toBe(false)
  })
})
