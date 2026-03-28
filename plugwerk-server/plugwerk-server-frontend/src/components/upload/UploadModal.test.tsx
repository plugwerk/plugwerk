// SPDX-License-Identifier: AGPL-3.0
// Copyright (C) 2026 devtank42 GmbH
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { renderWithRouter } from '../../test/renderWithTheme'
import { UploadModal } from './UploadModal'
import { useUiStore } from '../../stores/uiStore'
import { useAuthStore } from '../../stores/authStore'
import axios from 'axios'
import * as apiConfig from '../../api/config'

vi.mock('../../api/config', () => ({
  axiosInstance: { post: vi.fn(), get: vi.fn() },
  catalogApi: { listPlugins: vi.fn().mockResolvedValue({ data: { content: [], totalElements: 0, totalPages: 0, page: 0, size: 24 } }) },
}))

describe('UploadModal', () => {
  beforeEach(() => {
    useAuthStore.setState({ accessToken: 'token', username: 'alice', isAuthenticated: true, namespace: 'acme' })
    useUiStore.setState({ uploadModalOpen: false })
    vi.mocked(apiConfig.axiosInstance.post).mockReset()
  })

  it('is not visible when uploadModalOpen is false', () => {
    renderWithRouter(<UploadModal />)
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it('is visible when uploadModalOpen is true', () => {
    useUiStore.setState({ uploadModalOpen: true })
    renderWithRouter(<UploadModal />)
    expect(screen.getByRole('dialog')).toBeInTheDocument()
    expect(screen.getByText(/upload plugin release/i)).toBeInTheDocument()
  })

  it('closes when Cancel is clicked', async () => {
    useUiStore.setState({ uploadModalOpen: true })
    const user = userEvent.setup()
    renderWithRouter(<UploadModal />)
    await user.click(screen.getByRole('button', { name: /cancel/i }))
    expect(useUiStore.getState().uploadModalOpen).toBe(false)
  })

  it('closes when X button is clicked', async () => {
    useUiStore.setState({ uploadModalOpen: true })
    const user = userEvent.setup()
    renderWithRouter(<UploadModal />)
    await user.click(screen.getByRole('button', { name: /close upload dialog/i }))
    expect(useUiStore.getState().uploadModalOpen).toBe(false)
  })

  it('Upload button is disabled when no file is selected', () => {
    useUiStore.setState({ uploadModalOpen: true })
    renderWithRouter(<UploadModal />)
    expect(screen.getByRole('button', { name: /upload release/i })).toBeDisabled()
  })

  it('shows backend error message when upload returns 422', async () => {
    useUiStore.setState({ uploadModalOpen: true })
    const axiosError = new axios.AxiosError('Request failed with status code 422', '422', undefined, undefined, {
      status: 422,
      data: { message: 'No descriptor found in JAR (tried plugwerk.yml, MANIFEST.MF, plugin.properties)' },
      statusText: 'Unprocessable Entity',
      headers: {},
      config: {} as never,
    })
    vi.mocked(apiConfig.axiosInstance.post).mockRejectedValue(axiosError)

    const user = userEvent.setup()
    renderWithRouter(<UploadModal />)

    const file = new File(['fake-jar'], 'plugin.jar', { type: 'application/java-archive' })
    await user.upload(screen.getByLabelText(/select plugin jar or zip file/i), file)
    await user.click(screen.getByRole('button', { name: /upload release/i }))

    await waitFor(() => {
      expect(screen.getByRole('alert')).toBeInTheDocument()
      expect(screen.getByText(/no descriptor found in jar/i)).toBeInTheDocument()
    }, { timeout: 15000 })
  }, 20000)

  it('closes modal and shows toast on successful upload', async () => {
    useUiStore.setState({ uploadModalOpen: true })
    vi.mocked(apiConfig.axiosInstance.post).mockResolvedValue({ data: {} })

    const user = userEvent.setup()
    renderWithRouter(<UploadModal />)

    const file = new File(['fake-jar'], 'plugin.jar', { type: 'application/java-archive' })
    await user.upload(screen.getByLabelText(/select plugin jar or zip file/i), file)
    await user.click(screen.getByRole('button', { name: /upload release/i }))

    await waitFor(() => {
      expect(useUiStore.getState().uploadModalOpen).toBe(false)
    }, { timeout: 15000 })
    expect(useUiStore.getState().toasts.length).toBeGreaterThan(0)
  }, 20000)

  it('re-fetches catalog after successful upload', async () => {
    useUiStore.setState({ uploadModalOpen: true })
    vi.mocked(apiConfig.axiosInstance.post).mockResolvedValue({ data: {} })

    const user = userEvent.setup()
    renderWithRouter(<UploadModal />)

    const file = new File(['fake-jar'], 'plugin.jar', { type: 'application/java-archive' })
    await user.upload(screen.getByLabelText(/select plugin jar or zip file/i), file)
    await user.click(screen.getByRole('button', { name: /upload release/i }))

    await waitFor(() => {
      expect(vi.mocked(apiConfig.catalogApi.listPlugins)).toHaveBeenCalledWith(
        expect.objectContaining({ ns: 'acme' }),
      )
    }, { timeout: 15000 })
  }, 20000)

  it('does not re-fetch catalog when upload fails', async () => {
    useUiStore.setState({ uploadModalOpen: true })
    const axiosError = new axios.AxiosError('Request failed', '422', undefined, undefined, {
      status: 422,
      data: { message: 'Invalid descriptor' },
      statusText: 'Unprocessable Entity',
      headers: {},
      config: {} as never,
    })
    vi.mocked(apiConfig.axiosInstance.post).mockRejectedValue(axiosError)
    vi.mocked(apiConfig.catalogApi.listPlugins).mockClear()

    const user = userEvent.setup()
    renderWithRouter(<UploadModal />)

    const file = new File(['fake-jar'], 'plugin.jar', { type: 'application/java-archive' })
    await user.upload(screen.getByLabelText(/select plugin jar or zip file/i), file)
    await user.click(screen.getByRole('button', { name: /upload release/i }))

    await waitFor(() => {
      expect(screen.getByRole('alert')).toBeInTheDocument()
    }, { timeout: 15000 })
    expect(vi.mocked(apiConfig.catalogApi.listPlugins)).not.toHaveBeenCalled()
  }, 20000)
})
