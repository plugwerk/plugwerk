// SPDX-License-Identifier: AGPL-3.0
// Copyright (C) 2026 devtank42 GmbH
import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Box, TextField, Button, Alert } from '@mui/material'
import { AuthCard } from '../components/auth/AuthCard'
import { authApi } from '../api/config'
import { useAuthStore } from '../stores/authStore'

export function ChangePasswordPage() {
  const navigate = useNavigate()
  const { username, clearPasswordChangeRequired } = useAuthStore()

  const [currentPassword, setCurrentPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    if (newPassword.length < 8) {
      setError('New password must be at least 8 characters.')
      return
    }
    if (newPassword !== confirmPassword) {
      setError('Passwords do not match.')
      return
    }
    setError(null)
    setLoading(true)
    try {
      await authApi.changePassword({ changePasswordRequest: { currentPassword, newPassword } })
      clearPasswordChangeRequired()
      navigate('/', { replace: true })
    } catch {
      setError('Failed to change password. Please check your current password.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <AuthCard
      title="Change your password"
      subtitle={`Signed in as ${username ?? 'unknown'}. You must set a new password to continue.`}
    >
      {error && (
        <Alert severity="error" role="alert" onClose={() => setError(null)}>
          {error}
        </Alert>
      )}
      <Box component="form" onSubmit={handleSubmit} noValidate sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
        <TextField
          label="Current Password"
          type="password"
          value={currentPassword}
          onChange={(e) => setCurrentPassword(e.target.value)}
          required
          size="small"
          autoComplete="current-password"
          autoFocus
        />
        <TextField
          label="New Password"
          type="password"
          value={newPassword}
          onChange={(e) => setNewPassword(e.target.value)}
          required
          size="small"
          autoComplete="new-password"
          helperText="At least 8 characters"
        />
        <TextField
          label="Confirm New Password"
          type="password"
          value={confirmPassword}
          onChange={(e) => setConfirmPassword(e.target.value)}
          required
          size="small"
          autoComplete="new-password"
        />
        <Button type="submit" variant="contained" size="large" disabled={loading} fullWidth>
          {loading ? 'Saving…' : 'Set New Password'}
        </Button>
      </Box>
    </AuthCard>
  )
}
