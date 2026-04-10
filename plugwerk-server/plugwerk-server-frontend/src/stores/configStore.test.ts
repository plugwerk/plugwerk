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
import { useConfigStore } from './configStore'
import * as apiConfig from '../api/config'

vi.mock('../api/config', () => ({
  axiosInstance: {
    get: vi.fn(),
  },
}))

describe('configStore', () => {
  beforeEach(() => {
    // Reset store to initial state
    useConfigStore.setState({ version: '…', maxFileSizeMb: 100, loaded: false })
    vi.mocked(apiConfig.axiosInstance.get).mockReset()
  })

  it('has correct initial state', () => {
    const { version, maxFileSizeMb, loaded } = useConfigStore.getState()
    expect(version).toBe('…')
    expect(maxFileSizeMb).toBe(100)
    expect(loaded).toBe(false)
  })

  it('fetches config and sets version and maxFileSizeMb', async () => {
    vi.mocked(apiConfig.axiosInstance.get).mockResolvedValue({
      data: { version: '0.1.0-SNAPSHOT', upload: { maxFileSizeMb: 200 } },
    })

    await useConfigStore.getState().fetchConfig()

    const { version, maxFileSizeMb, loaded } = useConfigStore.getState()
    expect(version).toBe('0.1.0-SNAPSHOT')
    expect(maxFileSizeMb).toBe(200)
    expect(loaded).toBe(true)
  })

  it('sets version to unknown on fetch error', async () => {
    vi.mocked(apiConfig.axiosInstance.get).mockRejectedValue(new Error('Network error'))

    await useConfigStore.getState().fetchConfig()

    const { version, loaded } = useConfigStore.getState()
    expect(version).toBe('unknown')
    expect(loaded).toBe(true)
  })

  it('does not fetch again when already loaded', async () => {
    vi.mocked(apiConfig.axiosInstance.get).mockResolvedValue({
      data: { version: '1.0.0', upload: { maxFileSizeMb: 50 } },
    })

    await useConfigStore.getState().fetchConfig()
    await useConfigStore.getState().fetchConfig()

    expect(vi.mocked(apiConfig.axiosInstance.get)).toHaveBeenCalledTimes(1)
  })

  it('falls back to unknown when version is missing from response', async () => {
    vi.mocked(apiConfig.axiosInstance.get).mockResolvedValue({
      data: { upload: { maxFileSizeMb: 100 } },
    })

    await useConfigStore.getState().fetchConfig()

    expect(useConfigStore.getState().version).toBe('unknown')
  })
})
