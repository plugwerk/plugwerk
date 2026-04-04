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

export function RegisterPage() {
  return (
    <AuthCard title="Create account" subtitle="Register to publish plugins">
      <Box component="form" noValidate sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
        <TextField label="Username" required size="small" autoComplete="username" />
        <TextField label="Email" type="email" required size="small" autoComplete="email" />
        <TextField label="Password" type="password" required size="small" autoComplete="new-password" />
        <Button type="submit" variant="contained" size="large" fullWidth>
          Create Account
        </Button>
      </Box>
      <Typography variant="caption" color="text.disabled" sx={{ textAlign: 'center' }}>
        Already have an account?{' '}
        <MuiLink component={Link} to="/login" sx={{ color: 'primary.main' }}>
          Log in
        </MuiLink>
      </Typography>
    </AuthCard>
  )
}
