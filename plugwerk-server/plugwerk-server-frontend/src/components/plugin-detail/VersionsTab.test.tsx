// SPDX-License-Identifier: AGPL-3.0
// Copyright (C) 2026 devtank42 GmbH
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { renderWithRouter } from '../../test/renderWithTheme'
import { VersionsTab } from './VersionsTab'
import type { PluginReleaseDto } from '../../api/generated/model'

vi.mock('../../api/config', () => ({
  reviewsApi: { approveRelease: vi.fn() },
  managementApi: { deleteRelease: vi.fn() },
}))

const publishedRelease: PluginReleaseDto = {
  id: '00000000-0000-0000-0000-000000000001',
  pluginId: 'my-plugin',
  version: '1.0.0',
  status: 'published',
  artifactSha256: 'abc',
  artifactSize: 1024,
  downloadCount: 10,
  createdAt: '2026-01-01T00:00:00Z',
}

const draftRelease: PluginReleaseDto = {
  id: '00000000-0000-0000-0000-000000000002',
  pluginId: 'my-plugin',
  version: '2.0.0',
  status: 'draft',
  artifactSha256: 'def',
  artifactSize: 2048,
  downloadCount: 0,
  createdAt: '2026-02-01T00:00:00Z',
}

const defaultProps = {
  releases: [publishedRelease, draftRelease],
  namespace: 'acme',
  pluginId: 'my-plugin',
  currentVersion: '1.0.0',
}

describe('VersionsTab', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('shows delete buttons when canApprove is true (ADMIN)', () => {
    renderWithRouter(<VersionsTab {...defaultProps} canApprove={true} />)

    const deleteButtons = screen.getAllByRole('button', { name: /delete release/i })
    expect(deleteButtons).toHaveLength(2)
  })

  it('hides delete buttons when canApprove is false (non-ADMIN)', () => {
    renderWithRouter(<VersionsTab {...defaultProps} canApprove={false} />)

    const deleteButtons = screen.queryAllByRole('button', { name: /delete release/i })
    expect(deleteButtons).toHaveLength(0)
  })

  it('hides delete buttons when canApprove is not set', () => {
    renderWithRouter(<VersionsTab {...defaultProps} />)

    const deleteButtons = screen.queryAllByRole('button', { name: /delete release/i })
    expect(deleteButtons).toHaveLength(0)
  })

  it('opens confirmation dialog on delete button click', async () => {
    const user = userEvent.setup()
    renderWithRouter(<VersionsTab {...defaultProps} canApprove={true} />)

    const deleteButtons = screen.getAllByRole('button', { name: /delete release/i })
    await user.click(deleteButtons[0])

    expect(screen.getByText(/are you sure you want to delete/i)).toBeInTheDocument()
    expect(screen.getByText(/v1.0.0/)).toBeInTheDocument()
  })

  it('calls managementApi.deleteRelease on confirm', async () => {
    const { managementApi } = await import('../../api/config')
    const mockDelete = vi.mocked(managementApi.deleteRelease).mockResolvedValue({} as never)
    const onDeleted = vi.fn()
    const user = userEvent.setup()

    renderWithRouter(<VersionsTab {...defaultProps} canApprove={true} onReleaseDeleted={onDeleted} />)

    const deleteButtons = screen.getAllByRole('button', { name: /delete release/i })
    await user.click(deleteButtons[0])
    await user.click(screen.getByRole('button', { name: /confirm-delete/i }))

    expect(mockDelete).toHaveBeenCalledWith({ ns: 'acme', pluginId: 'my-plugin', version: '1.0.0' })
    expect(onDeleted).toHaveBeenCalledWith('1.0.0')
  })

  it('renders release versions', () => {
    renderWithRouter(<VersionsTab {...defaultProps} />)

    expect(screen.getByText('v1.0.0')).toBeInTheDocument()
    expect(screen.getByText('v2.0.0')).toBeInTheDocument()
  })
})
