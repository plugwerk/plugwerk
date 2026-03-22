// SPDX-License-Identifier: AGPL-3.0
// Copyright (C) 2026 devtank42 GmbH
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { act } from 'react'
import { useAuthStore } from './authStore'

describe('useAuthStore', () => {
  beforeEach(() => {
    localStorage.clear()
    useAuthStore.setState({
      accessToken: null,
      username: null,
      namespace: 'default',
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

    it('has "default" namespace when nothing in localStorage', () => {
      expect(useAuthStore.getState().namespace).toBe('default')
    })
  })

  describe('login', () => {
    it('sets accessToken and isAuthenticated on success', async () => {
      vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
        ok: true,
        json: () => Promise.resolve({ accessToken: 'tok_abc', expiresIn: 28800 }),
      }))

      await act(async () => {
        await useAuthStore.getState().login('test', 'test')
      })

      expect(useAuthStore.getState().isAuthenticated).toBe(true)
      expect(useAuthStore.getState().accessToken).toBe('tok_abc')
      expect(useAuthStore.getState().username).toBe('test')
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
      useAuthStore.setState({ accessToken: 'tok_xyz', isAuthenticated: true, username: 'test' })
      localStorage.setItem('pw-access-token', 'tok_xyz')

      act(() => { useAuthStore.getState().logout() })

      expect(useAuthStore.getState().isAuthenticated).toBe(false)
      expect(useAuthStore.getState().accessToken).toBeNull()
      expect(localStorage.getItem('pw-access-token')).toBeNull()
    })

    it('preserves namespace after logout', () => {
      useAuthStore.setState({ accessToken: 'tok_xyz', isAuthenticated: true, namespace: 'my-org' })

      act(() => { useAuthStore.getState().logout() })

      expect(useAuthStore.getState().namespace).toBe('my-org')
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
