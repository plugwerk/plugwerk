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
import { useState } from 'react'
import { useNavigate, useLocation } from 'react-router-dom'
import { Box, TextField, Button, Alert } from '@mui/material'
import { AuthCard } from '../components/auth/AuthCard'
import { useAuthStore } from '../stores/authStore'

export function LoginPage() {
  const { login } = useAuthStore()
  const navigate = useNavigate()
  const location = useLocation()
  const from = new URLSearchParams(location.search).get('from') ?? '/'

  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    if (!username.trim() || !password) {
      setError('Please enter username and password.')
      return
    }
    setError(null)
    setLoading(true)
    try {
      await login(username.trim(), password)
      await useAuthStore.getState().initNamespace()
      const { passwordChangeRequired, namespace } = useAuthStore.getState()
      if (passwordChangeRequired) {
        navigate('/change-password', { replace: true })
      } else if (!namespace) {
        navigate('/onboarding', { replace: true })
      } else {
        // Only use the saved return URL if it doesn't contain a stale namespace
        const safeFrom = from.startsWith('/namespaces/') ? '/' : from
        navigate(safeFrom, { replace: true })
      }
    } catch {
      setError('Invalid username or password.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <AuthCard title="Welcome back" subtitle="Sign in to your account">
      {error && (
        <Alert severity="error" role="alert" onClose={() => setError(null)}>
          {error}
        </Alert>
      )}

      <Box component="form" onSubmit={handleSubmit} noValidate sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
        <TextField
          label="Username"
          value={username}
          onChange={(e) => setUsername(e.target.value)}
          required
          autoComplete="username"
          size="small"
          autoFocus
        />
        <TextField
          label="Password"
          type="password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          required
          autoComplete="current-password"
          size="small"
        />
        <Button type="submit" variant="contained" size="large" disabled={loading} fullWidth>
          {loading ? 'Signing in…' : 'Sign In'}
        </Button>
      </Box>
    </AuthCard>
  )
}
