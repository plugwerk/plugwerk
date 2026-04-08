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
import { VersionsTab } from './VersionsTab'
import type { PluginReleaseDto } from '../../api/generated/model'

const mockNavigate = vi.fn()
vi.mock('react-router-dom', async (importOriginal) => {
  const actual = await importOriginal<typeof import('react-router-dom')>()
  return { ...actual, useNavigate: () => mockNavigate }
})

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
  fileFormat: 'jar',
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
  fileFormat: 'zip',
  downloadCount: 0,
  createdAt: '2026-02-01T00:00:00Z',
}

const defaultProps = {
  releases: [publishedRelease, draftRelease],
  namespace: 'acme',
  pluginId: 'my-plugin',

}

describe('VersionsTab', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockNavigate.mockReset()
  })

  it('shows delete buttons when canApprove is true (ADMIN)', () => {
    renderWithRouter(<VersionsTab {...defaultProps} canApprove={true} />)

    const deleteButtons = screen.getAllByRole('button', { name: /delete/i })
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

    const deleteButtons = screen.getAllByRole('button', { name: /delete/i })
    await user.click(deleteButtons[0])

    expect(screen.getByText(/are you sure you want to delete v1\.0\.0\?/i)).toBeInTheDocument()
  })

  it('calls managementApi.deleteRelease on confirm', async () => {
    const { managementApi } = await import('../../api/config')
    const mockDelete = vi.mocked(managementApi.deleteRelease).mockResolvedValue({
      data: undefined, status: 204, statusText: 'No Content',
      headers: { 'x-plugin-deleted': 'false' }, config: {} as never,
    })
    const onDeleted = vi.fn()
    const user = userEvent.setup()

    renderWithRouter(<VersionsTab {...defaultProps} canApprove={true} onReleaseDeleted={onDeleted} />)

    const deleteButtons = screen.getAllByRole('button', { name: /delete/i })
    await user.click(deleteButtons[0])
    await user.click(screen.getByRole('button', { name: /^delete$/i }))

    expect(mockDelete).toHaveBeenCalledWith({ ns: 'acme', pluginId: 'my-plugin', version: '1.0.0' })
    expect(onDeleted).toHaveBeenCalledWith('1.0.0')
  })

  it('renders release versions', () => {
    renderWithRouter(<VersionsTab {...defaultProps} />)

    expect(screen.getByText('v1.0.0')).toBeInTheDocument()
    expect(screen.getByText('v2.0.0')).toBeInTheDocument()
  })

  it('shows plugin deletion warning when deleting the last release', async () => {
    const user = userEvent.setup()
    renderWithRouter(
      <VersionsTab
        releases={[publishedRelease]}
        namespace="acme"
        pluginId="my-plugin"

        canApprove={true}
      />,
    )

    const deleteButton = screen.getByRole('button', { name: /delete/i })
    await user.click(deleteButton)

    expect(screen.getByText(/the entire plugin will also be removed/i)).toBeInTheDocument()
  })

  it('navigates to catalog page when X-Plugin-Deleted header is true', async () => {
    const { managementApi } = await import('../../api/config')
    vi.mocked(managementApi.deleteRelease).mockResolvedValue({
      data: undefined,
      status: 204,
      statusText: 'No Content',
      headers: { 'x-plugin-deleted': 'true' },
      config: {} as never,
    })
    const onDeleted = vi.fn()
    const user = userEvent.setup()

    renderWithRouter(
      <VersionsTab
        releases={[publishedRelease]}
        namespace="acme"
        pluginId="my-plugin"

        canApprove={true}
        onReleaseDeleted={onDeleted}
      />,
    )

    const deleteButton = screen.getByRole('button', { name: /delete/i })
    await user.click(deleteButton)
    await user.click(screen.getByRole('button', { name: /^delete$/i }))

    expect(mockNavigate).toHaveBeenCalledWith('/namespaces/acme/plugins')
    expect(onDeleted).not.toHaveBeenCalled()
  })

  it('renders download icon button for published releases', () => {
    renderWithRouter(<VersionsTab {...defaultProps} />)

    const downloadButtons = screen.getAllByRole('button', { name: /download/i })
    expect(downloadButtons.length).toBeGreaterThanOrEqual(1)
  })

  it('shows Format column with file format', () => {
    renderWithRouter(<VersionsTab {...defaultProps} />)

    expect(screen.getByText('Format')).toBeInTheDocument()
    expect(screen.getByText('.jar')).toBeInTheDocument()
    expect(screen.getByText('.zip')).toBeInTheDocument()
  })

  it('shows SHA-256 column with truncated hash', () => {
    renderWithRouter(<VersionsTab {...defaultProps} />)

    expect(screen.getByText('SHA-256')).toBeInTheDocument()
    expect(screen.getByText('abc…')).toBeInTheDocument()
    expect(screen.getByText('def…')).toBeInTheDocument()
  })

  it('shows Downloads column with download count', () => {
    renderWithRouter(<VersionsTab {...defaultProps} />)

    expect(screen.getByText('Downloads')).toBeInTheDocument()
    expect(screen.getByText('10')).toBeInTheDocument()
    expect(screen.getByText('0')).toBeInTheDocument()
  })

  it('shows createdAt for draft releases and publishedAt for published', () => {
    renderWithRouter(<VersionsTab {...defaultProps} />)

    // Published release should show publishedAt formatted as dd.MM.yyyy HH:mm:ss
    const cells = screen.getAllByText(/\d{2}\.\d{2}\.\d{4} \d{2}:\d{2}:\d{2}/)
    expect(cells.length).toBeGreaterThanOrEqual(1)
  })
})
