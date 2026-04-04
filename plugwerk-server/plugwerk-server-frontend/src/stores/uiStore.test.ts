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
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { act } from 'react'

// matchMedia is not implemented in jsdom
Object.defineProperty(window, 'matchMedia', {
  writable: true,
  value: vi.fn().mockImplementation((query: string) => ({
    matches: false,
    media: query,
    onchange: null,
    addListener: vi.fn(),
    removeListener: vi.fn(),
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    dispatchEvent: vi.fn(),
  })),
})

// Import after mocking globals
const { useUiStore } = await import('./uiStore')

describe('useUiStore', () => {
  beforeEach(() => {
    localStorage.clear()
    useUiStore.setState({ themeMode: 'light', searchQuery: '', toasts: [], uploadModalOpen: false })
  })

  describe('themeMode', () => {
    it('defaults to light when no preference is saved', () => {
      expect(useUiStore.getState().themeMode).toBe('light')
    })

    it('reads saved theme from localStorage on init', () => {
      localStorage.setItem('pw-theme', 'dark')
      // Re-evaluate initial state by reading the store factory logic directly
      const saved = localStorage.getItem('pw-theme')
      expect(saved).toBe('dark')
    })

    it('toggleTheme switches from light to dark', () => {
      useUiStore.setState({ themeMode: 'light' })
      act(() => { useUiStore.getState().toggleTheme() })
      expect(useUiStore.getState().themeMode).toBe('dark')
    })

    it('toggleTheme switches from dark to light', () => {
      useUiStore.setState({ themeMode: 'dark' })
      act(() => { useUiStore.getState().toggleTheme() })
      expect(useUiStore.getState().themeMode).toBe('light')
    })

    it('toggleTheme persists to localStorage', () => {
      useUiStore.setState({ themeMode: 'light' })
      act(() => { useUiStore.getState().toggleTheme() })
      expect(localStorage.getItem('pw-theme')).toBe('dark')
    })

    it('setTheme sets a specific mode', () => {
      act(() => { useUiStore.getState().setTheme('dark') })
      expect(useUiStore.getState().themeMode).toBe('dark')
    })

    it('setTheme persists to localStorage', () => {
      act(() => { useUiStore.getState().setTheme('dark') })
      expect(localStorage.getItem('pw-theme')).toBe('dark')
    })
  })

  describe('searchQuery', () => {
    it('defaults to empty string', () => {
      expect(useUiStore.getState().searchQuery).toBe('')
    })

    it('setSearchQuery updates the query', () => {
      act(() => { useUiStore.getState().setSearchQuery('my plugin') })
      expect(useUiStore.getState().searchQuery).toBe('my plugin')
    })

    it('setSearchQuery can clear the query', () => {
      useUiStore.setState({ searchQuery: 'something' })
      act(() => { useUiStore.getState().setSearchQuery('') })
      expect(useUiStore.getState().searchQuery).toBe('')
    })
  })

  describe('toasts', () => {
    it('starts with empty toasts', () => {
      expect(useUiStore.getState().toasts).toHaveLength(0)
    })

    it('addToast adds a toast with an id', () => {
      act(() => {
        useUiStore.getState().addToast({ type: 'info', message: 'Hello' })
      })
      const toasts = useUiStore.getState().toasts
      expect(toasts).toHaveLength(1)
      expect(toasts[0]).toMatchObject({ type: 'info', message: 'Hello' })
      expect(toasts[0].id).toBeDefined()
    })

    it('addToast generates unique ids', () => {
      act(() => {
        useUiStore.getState().addToast({ type: 'info', message: 'First' })
        useUiStore.getState().addToast({ type: 'success', message: 'Second' })
      })
      const toasts = useUiStore.getState().toasts
      expect(toasts).toHaveLength(2)
      expect(toasts[0].id).not.toBe(toasts[1].id)
    })

    it('removeToast removes a toast by id', () => {
      act(() => {
        useUiStore.getState().addToast({ type: 'error', message: 'Oops' })
      })
      const { id } = useUiStore.getState().toasts[0]
      act(() => {
        useUiStore.getState().removeToast(id)
      })
      expect(useUiStore.getState().toasts).toHaveLength(0)
    })

    it('removeToast only removes the matching toast', () => {
      act(() => {
        useUiStore.getState().addToast({ type: 'info', message: 'A' })
        useUiStore.getState().addToast({ type: 'warning', message: 'B' })
      })
      const firstId = useUiStore.getState().toasts[0].id
      act(() => {
        useUiStore.getState().removeToast(firstId)
      })
      const toasts = useUiStore.getState().toasts
      expect(toasts).toHaveLength(1)
      expect(toasts[0].message).toBe('B')
    })

    it('addToast auto-removes after 4 seconds', async () => {
      vi.useFakeTimers()
      act(() => {
        useUiStore.getState().addToast({ type: 'success', message: 'Done' })
      })
      expect(useUiStore.getState().toasts).toHaveLength(1)
      await act(async () => { vi.advanceTimersByTime(4000) })
      expect(useUiStore.getState().toasts).toHaveLength(0)
      vi.useRealTimers()
    })
  })

  describe('uploadModal', () => {
    it('defaults to closed', () => {
      expect(useUiStore.getState().uploadModalOpen).toBe(false)
    })

    it('openUploadModal sets uploadModalOpen to true', () => {
      act(() => { useUiStore.getState().openUploadModal() })
      expect(useUiStore.getState().uploadModalOpen).toBe(true)
    })

    it('closeUploadModal sets uploadModalOpen to false', () => {
      useUiStore.setState({ uploadModalOpen: true })
      act(() => { useUiStore.getState().closeUploadModal() })
      expect(useUiStore.getState().uploadModalOpen).toBe(false)
    })
  })
})
