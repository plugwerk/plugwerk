// SPDX-License-Identifier: AGPL-3.0
// Copyright (C) 2026 devtank42 GmbH
import { Box, Tooltip, Typography } from '@mui/material'
import { Download, Clock, Puzzle, HardDrive } from 'lucide-react'
import { Link } from 'react-router-dom'
import { Badge } from '../common/Badge'
import type { PluginDto } from '../../api/generated/model'
import { tokens } from '../../theme/tokens'
import { formatFileSize } from '../../utils/formatFileSize'
import { formatDateTime, formatRelativeTime } from '../../utils/formatDateTime'

interface PluginListRowProps {
  plugin: PluginDto
  namespace: string
}

function formatCount(n: number | undefined): string {
  if (!n) return '0'
  if (n >= 1000) return `${(n / 1000).toFixed(1)}k`
  return String(n)
}


export function PluginListRow({ plugin, namespace }: PluginListRowProps) {
  const isDeprecated = plugin.status === 'archived'
  const latestRelease = plugin.latestRelease
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
        <Typography variant="caption" color="text.disabled">{plugin.provider ?? namespace}</Typography>
      </Box>

      {isDeprecated && <Badge variant="deprecated">Deprecated</Badge>}
      {latestRelease?.version && (
        <Badge variant="version">v{latestRelease.version}</Badge>
      )}

      <Box sx={{ display: 'flex', gap: 2, color: 'text.disabled', flexShrink: 0 }}>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
          <Download size={12} aria-hidden="true" />
          <Typography variant="caption">{formatCount(plugin.downloadCount ?? 0)}</Typography>
        </Box>
        {latestRelease?.artifactSize && (
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
            <HardDrive size={12} aria-hidden="true" />
            <Typography variant="caption">{formatFileSize(latestRelease.artifactSize)}</Typography>
          </Box>
        )}
        <Tooltip title={formatDateTime(plugin.updatedAt)} placement="top">
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5, cursor: 'default' }}>
            <Clock size={12} aria-hidden="true" />
            <Typography variant="caption">{formatRelativeTime(plugin.updatedAt)}</Typography>
          </Box>
        </Tooltip>
      </Box>
    </Box>
  )
}
