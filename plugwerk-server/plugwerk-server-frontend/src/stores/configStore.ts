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
import { create } from 'zustand'
import { axiosInstance } from '../api/config'

interface ConfigState {
  readonly version: string
  readonly maxFileSizeMb: number
  readonly loaded: boolean
  fetchConfig: () => Promise<void>
}

export const useConfigStore = create<ConfigState>((set, get) => ({
  version: '…',
  maxFileSizeMb: 100,
  loaded: false,

  async fetchConfig() {
    if (get().loaded) return
    try {
      const res = await axiosInstance.get('/config')
      set({
        version: res.data?.version ?? 'unknown',
        maxFileSizeMb: typeof res.data?.upload?.maxFileSizeMb === 'number'
          ? res.data.upload.maxFileSizeMb
          : 100,
        loaded: true,
      })
    } catch {
      set({ version: 'unknown', loaded: true })
    }
  },
}))
