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
import { useAuthStore } from './authStore'

describe('useAuthStore', () => {
  beforeEach(() => {
    localStorage.clear()
    useAuthStore.setState({
      accessToken: null,
      username: null,
      namespace: undefined,
      isAuthenticated: false,
    })
  })

  describe('initial state', () => {
    it('is not authenticated when nothing in localStorage', () => {
      expect(useAuthStore.getState().isAuthenticated).toBe(false)
    })

    it('has null accessToken when nothing in localStorage', () => {
      expect(useAuthStore.getState().accessToken).toBeNull()
    })

    it('has undefined namespace before initialization', () => {
      expect(useAuthStore.getState().namespace).toBeUndefined()
    })
  })

  describe('login', () => {
    it('sets accessToken and isAuthenticated on success', async () => {
      vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
        ok: true,
        json: () => Promise.resolve({ accessToken: 'tok_abc', expiresIn: 28800 }),
      }))

      await act(async () => {
        await useAuthStore.getState().login('alice', 'secret')
      })

      expect(useAuthStore.getState().isAuthenticated).toBe(true)
      expect(useAuthStore.getState().accessToken).toBe('tok_abc')
      expect(useAuthStore.getState().username).toBe('alice')
      expect(localStorage.getItem('pw-access-token')).toBe('tok_abc')

      vi.unstubAllGlobals()
    })

    it('throws on invalid credentials', async () => {
      vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: false }))

      await expect(
        act(async () => {
          await useAuthStore.getState().login('wrong', 'wrong')
        }),
      ).rejects.toThrow('Invalid credentials')

      expect(useAuthStore.getState().isAuthenticated).toBe(false)

      vi.unstubAllGlobals()
    })
  })

  describe('logout', () => {
    it('clears accessToken and isAuthenticated', () => {
      useAuthStore.setState({ accessToken: 'tok_xyz', isAuthenticated: true, username: 'alice' })
      localStorage.setItem('pw-access-token', 'tok_xyz')

      act(() => { useAuthStore.getState().logout() })

      expect(useAuthStore.getState().isAuthenticated).toBe(false)
      expect(useAuthStore.getState().accessToken).toBeNull()
      expect(localStorage.getItem('pw-access-token')).toBeNull()
    })

    it('clears namespace after logout', () => {
      useAuthStore.setState({ accessToken: 'tok_xyz', isAuthenticated: true, namespace: 'my-org' })

      act(() => { useAuthStore.getState().logout() })

      expect(useAuthStore.getState().namespace).toBeUndefined()
    })
  })

  describe('setNamespace', () => {
    it('updates namespace in state', () => {
      act(() => { useAuthStore.getState().setNamespace('acme') })
      expect(useAuthStore.getState().namespace).toBe('acme')
    })

    it('persists namespace to localStorage', () => {
      act(() => { useAuthStore.getState().setNamespace('acme') })
      expect(localStorage.getItem('pw-namespace')).toBe('acme')
    })
  })
})
