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
import { Box, TextField, Button, Typography, Link as MuiLink } from '@mui/material'
import { Link } from 'react-router-dom'
import { AuthCard } from '../components/auth/AuthCard'

export function ForgotPasswordPage() {
  return (
    <AuthCard title="Reset password" subtitle="Enter your email to receive a reset link">
      <Box component="form" noValidate sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
        <TextField label="Email" type="email" required size="small" autoComplete="email" />
        <Button type="submit" variant="contained" size="large" fullWidth>
          Send Reset Link
        </Button>
      </Box>
      <Typography variant="caption" color="text.disabled" sx={{ textAlign: 'center' }}>
        Remember your password?{' '}
        <MuiLink component={Link} to="/login" sx={{ color: 'primary.main' }}>
          Back to login
        </MuiLink>
      </Typography>
    </AuthCard>
  )
}
