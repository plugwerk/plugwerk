// SPDX-License-Identifier: AGPL-3.0
// Copyright (C) 2026 devtank42 GmbH
import { Box, Tooltip, Typography } from '@mui/material'
import { Download, Clock, Puzzle } from 'lucide-react'
import { Link } from 'react-router-dom'
import { Badge } from '../common/Badge'
import type { PluginDto } from '../../api/generated/model'
import { tokens } from '../../theme/tokens'

interface PluginListRowProps {
  plugin: PluginDto
  namespace: string
}

function formatCount(n: number | undefined): string {
  if (!n) return '0'
  if (n >= 1000) return `${(n / 1000).toFixed(1)}k`
  return String(n)
}

function formatAbsoluteTime(dateStr: string | undefined): string {
  if (!dateStr) return ''
  const d = new Date(dateStr)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${pad(d.getDate())}.${pad(d.getMonth() + 1)}.${d.getFullYear()} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

function formatRelativeTime(dateStr: string | undefined): string {
  if (!dateStr) return '—'
  const diff = Date.now() - new Date(dateStr).getTime()
  const minutes = Math.floor(diff / 60_000)
  if (minutes < 1) return 'just now'
  if (minutes < 60) return `${minutes}m ago`
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `${hours}h ago`
  const days = Math.floor(hours / 24)
  if (days < 7) return `${days}d ago`
  const weeks = Math.floor(days / 7)
  if (weeks < 5) return `${weeks}w ago`
  const months = Math.floor(days / 30)
  if (months < 12) return `${months}mo ago`
  return `${Math.floor(months / 12)}y ago`
}

export function PluginListRow({ plugin, namespace }: PluginListRowProps) {
  const isDeprecated = plugin.status === 'archived'
  const isDraft = !plugin.latestVersion && !!plugin.latestDraftVersion
  return (
    <Box
      component={Link}
      to={`/namespaces/${namespace}/plugins/${plugin.pluginId}`}
      role="listitem"
      sx={{
        display: 'flex',
        alignItems: 'center',
        gap: 2,
        px: 2,
        py: 1.5,
        borderBottom: '1px solid',
        borderColor: 'divider',
        textDecoration: 'none',
        color: 'inherit',
        bgcolor: 'background.paper',
        transition: 'background-color 0.15s',
        '&:last-child': { borderBottom: 'none' },
        '&:hover': { bgcolor: 'background.default' },
      }}
    >
      <Box
        sx={{
          width: 36,
          height: 36,
          borderRadius: tokens.radius.btn,
          background: 'background.default',
          border: '1px solid',
          borderColor: 'divider',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          color: 'text.disabled',
          flexShrink: 0,
        }}
        aria-hidden="true"
      >
        <Puzzle size={20} />
      </Box>

      <Box sx={{ flex: 1, minWidth: 0 }}>
        <Typography variant="body2" fontWeight={600} noWrap>{plugin.name}</Typography>
        <Typography variant="caption" color="text.disabled">{plugin.author ?? namespace}</Typography>
      </Box>

      {isDraft && <Badge variant="draft">Draft</Badge>}
      {isDeprecated && <Badge variant="deprecated">Deprecated</Badge>}
      {(plugin.latestVersion ?? plugin.latestDraftVersion) && (
        <Badge variant="version">v{plugin.latestVersion ?? plugin.latestDraftVersion}</Badge>
      )}

      <Box sx={{ display: 'flex', gap: 2, color: 'text.disabled', flexShrink: 0 }}>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
          <Download size={12} aria-hidden="true" />
          <Typography variant="caption">{formatCount(plugin.downloadCount ?? 0)}</Typography>
        </Box>
        <Tooltip title={formatAbsoluteTime(plugin.updatedAt)} placement="top">
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5, cursor: 'default' }}>
            <Clock size={12} aria-hidden="true" />
            <Typography variant="caption">{formatRelativeTime(plugin.updatedAt)}</Typography>
          </Box>
        </Tooltip>
      </Box>
    </Box>
  )
}
