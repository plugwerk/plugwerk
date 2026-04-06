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
import { Box, Typography } from '@mui/material'
import { Settings, Users, Shield, FileCheck, Globe } from 'lucide-react'
import { Link, useLocation } from 'react-router-dom'
import { tokens } from '../../theme/tokens'

interface AdminSection {
  path: string
  label: string
  icon: React.ReactNode
}

const ADMIN_SECTIONS: AdminSection[] = [
  { path: 'global-settings',  label: 'General',         icon: <Settings size={16} /> },
  { path: 'namespaces',       label: 'Namespaces',      icon: <Globe size={16} /> },
  { path: 'users',            label: 'Users',            icon: <Users size={16} /> },
  { path: 'oidc-providers',   label: 'OIDC Providers',   icon: <Shield size={16} /> },
  { path: 'reviews',          label: 'Pending Reviews',  icon: <FileCheck size={16} /> },
]

export function AdminSidebar() {
  const location = useLocation()

  return (
    <Box
      component="nav"
      aria-label="Admin navigation"
      sx={{
        width: 240,
        flexShrink: 0,
        borderRight: '1px solid',
        borderColor: 'divider',
        bgcolor: 'background.paper',
        display: { xs: 'none', md: 'flex' },
        flexDirection: 'column',
        position: 'sticky',
        top: 56,
        height: 'calc(100dvh - 56px - 48px)',
        overflowY: 'auto',
        pt: 1,
      }}
    >
      <Typography
        variant="caption"
        sx={{
          fontWeight: 600,
          color: 'text.disabled',
          textTransform: 'uppercase',
          letterSpacing: '0.08em',
          px: 2,
          py: 1,
        }}
      >
        Settings
      </Typography>

      {ADMIN_SECTIONS.map((section) => {
        const isActive = location.pathname === `/admin/${section.path}` || location.pathname.startsWith(`/admin/${section.path}/`)
        return (
          <Box
            key={section.path}
            component={Link}
            to={`/admin/${section.path}`}
            aria-current={isActive ? 'page' : undefined}
            sx={{
              display: 'flex',
              alignItems: 'center',
              gap: 1.5,
              px: 2,
              py: 1,
              mx: 1,
              borderRadius: tokens.radius.btn,
              textDecoration: 'none',
              background: isActive ? tokens.color.primaryLight : 'transparent',
              color: isActive ? tokens.color.primary : 'text.secondary',
              fontWeight: isActive ? 600 : 400,
              fontSize: '0.875rem',
              cursor: 'pointer',
              textAlign: 'left',
              width: 'calc(100% - 16px)',
              transition: 'background 0.15s, color 0.15s',
              '&:hover': {
                background: isActive ? tokens.color.primaryLight : 'background.default',
                color: 'text.primary',
              },
              '&:focus-visible': { outline: `2px solid ${tokens.color.primary}`, outlineOffset: -2 },
            }}
          >
            {section.icon}
            {section.label}
          </Box>
        )
      })}
    </Box>
  )
}
