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
import {
  Box,
  Container,
  Typography,
  Select,
  MenuItem,
  FormControl,
  InputLabel,
  Button,
  alpha,
  useTheme,
} from '@mui/material'
import { User, Globe, FolderOpen, Lock } from 'lucide-react'
import { Link } from 'react-router-dom'
import { useAuthStore } from '../stores/authStore'
import { useNamespaceStore } from '../stores/namespaceStore'
import { tokens } from '../theme/tokens'

interface SectionProps {
  icon: React.ReactNode
  title: string
  description?: string
  children: React.ReactNode
}

function Section({ icon, title, description, children }: SectionProps) {
  const theme = useTheme()
  const isDark = theme.palette.mode === 'dark'

  return (
    <Box
      sx={{
        border: '1px solid',
        borderColor: 'divider',
        borderRadius: tokens.radius.card,
        background: isDark ? alpha('#ffffff', 0.02) : tokens.color.white,
        overflow: 'hidden',
      }}
    >
      <Box
        sx={{
          display: 'flex',
          alignItems: 'center',
          gap: 1.5,
          px: 3,
          py: 2,
          borderBottom: '1px solid',
          borderColor: 'divider',
          background: isDark ? alpha('#ffffff', 0.03) : tokens.color.gray10,
        }}
      >
        <Box sx={{ color: 'text.secondary', display: 'flex' }}>{icon}</Box>
        <Box>
          <Typography variant="subtitle1" fontWeight={600}>{title}</Typography>
          {description && (
            <Typography variant="caption" color="text.secondary">{description}</Typography>
          )}
        </Box>
      </Box>
      <Box sx={{ px: 3, py: 2.5 }}>
        {children}
      </Box>
    </Box>
  )
}

interface InfoRowProps {
  label: string
  value: string
}

function InfoRow({ label, value }: InfoRowProps) {
  return (
    <Box sx={{ display: 'flex', alignItems: 'baseline', gap: 2, py: 0.75 }}>
      <Typography
        variant="caption"
        color="text.secondary"
        sx={{ minWidth: 80, flexShrink: 0, fontWeight: 500 }}
      >
        {label}
      </Typography>
      <Typography variant="body2">{value}</Typography>
    </Box>
  )
}

export function ProfileSettingsPage() {
  const { username, namespace, setNamespace } = useAuthStore()
  const { namespaces } = useNamespaceStore()

  return (
    <Box component="main" id="main-content" sx={{ flex: 1, py: 4 }}>
      <Container maxWidth="sm">
        <Typography variant="h1" sx={{ mb: 0.5 }}>Profile Settings</Typography>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 4 }}>
          Manage your account preferences and workspace configuration.
        </Typography>

        <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2.5 }}>

          {/* Personal Information */}
          <Section icon={<User size={18} />} title="Personal Information">
            <InfoRow label="Username" value={username ?? '—'} />
            <InfoRow label="Email" value="Not set" />
          </Section>

          {/* Security */}
          <Section
            icon={<Lock size={18} />}
            title="Security"
            description="Manage your password"
          >
            <Button
              component={Link}
              to="/change-password"
              variant="outlined"
              size="small"
              sx={{ borderRadius: tokens.radius.btn }}
            >
              Change Password
            </Button>
          </Section>

          {/* Language */}
          <Section
            icon={<Globe size={18} />}
            title="Language"
            description="Overrides the system default set by the administrator"
          >
            <FormControl size="small" sx={{ minWidth: 220 }}>
              <InputLabel>Language</InputLabel>
              <Select defaultValue="en" label="Language">
                <MenuItem value="en">English</MenuItem>
                <MenuItem value="de">Deutsch</MenuItem>
              </Select>
            </FormControl>
          </Section>

          {/* Default Namespace */}
          <Section
            icon={<FolderOpen size={18} />}
            title="Default Namespace"
            description="Used by default for catalog and upload operations"
          >
            <FormControl size="small" sx={{ minWidth: 220 }}>
              <InputLabel>Namespace</InputLabel>
              <Select
                value={namespace ?? ''}
                label="Namespace"
                onChange={(e) => setNamespace(e.target.value)}
              >
                {namespaces.map((ns) => (
                  <MenuItem key={ns.slug} value={ns.slug}>{ns.name} ({ns.slug})</MenuItem>
                ))}
              </Select>
            </FormControl>
          </Section>

          <Box sx={{ display: 'flex', justifyContent: 'flex-end' }}>
            <Button variant="contained" sx={{ borderRadius: tokens.radius.btn }}>
              Save Changes
            </Button>
          </Box>
        </Box>
      </Container>
    </Box>
  )
}
