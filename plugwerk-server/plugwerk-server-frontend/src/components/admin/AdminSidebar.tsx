// SPDX-License-Identifier: AGPL-3.0
// Copyright (C) 2026 devtank42 GmbH
import { Box, Typography } from '@mui/material'
import { Settings, Key, Users, FileCheck, Trash2 } from 'lucide-react'
import { tokens } from '../../theme/tokens'

export interface AdminSection {
  id: string
  label: string
  icon: React.ReactNode
  danger?: boolean
}

export const ADMIN_SECTIONS: AdminSection[] = [
  { id: 'general',   label: 'General',          icon: <Settings size={16} /> },
  { id: 'api-keys',  label: 'API Keys',          icon: <Key size={16} /> },
  { id: 'users',     label: 'Users',             icon: <Users size={16} /> },
  { id: 'reviews',   label: 'Pending Reviews',   icon: <FileCheck size={16} /> },
  { id: 'danger',    label: 'Danger Zone',       icon: <Trash2 size={16} />, danger: true },
]

interface AdminSidebarProps {
  activeSection: string
  onSelect: (id: string) => void
}

export function AdminSidebar({ activeSection, onSelect }: AdminSidebarProps) {
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
        const isActive = activeSection === section.id
        return (
          <Box
            key={section.id}
            component="button"
            onClick={() => onSelect(section.id)}
            aria-current={isActive ? 'page' : undefined}
            sx={{
              display: 'flex',
              alignItems: 'center',
              gap: 1.5,
              px: 2,
              py: 1,
              mx: 1,
              borderRadius: tokens.radius.btn,
              border: 'none',
              background: isActive
                ? (section.danger ? 'rgba(218,30,40,0.08)' : tokens.color.primaryLight)
                : 'transparent',
              color: isActive
                ? (section.danger ? tokens.color.danger : tokens.color.primary)
                : section.danger ? tokens.color.danger : 'text.secondary',
              fontWeight: isActive ? 600 : 400,
              fontSize: '0.875rem',
              cursor: 'pointer',
              textAlign: 'left',
              width: 'calc(100% - 16px)',
              transition: 'background 0.15s, color 0.15s',
              '&:hover': {
                background: section.danger
                  ? 'rgba(218,30,40,0.08)'
                  : isActive ? tokens.color.primaryLight : 'background.default',
                color: section.danger ? tokens.color.danger : 'text.primary',
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
