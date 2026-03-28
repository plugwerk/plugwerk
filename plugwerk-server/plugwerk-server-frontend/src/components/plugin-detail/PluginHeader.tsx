// SPDX-License-Identifier: AGPL-3.0
// Copyright (C) 2026 devtank42 GmbH
import { Box, Typography, Button } from '@mui/material'
import { Download, Calendar, Scale, Puzzle } from 'lucide-react'
import { Badge } from '../common/Badge'
import type { PluginDto, PluginReleaseDto } from '../../api/generated/model'
import { tokens } from '../../theme/tokens'
import { formatFileSize } from '../../utils/formatFileSize'

interface PluginHeaderProps {
  plugin: PluginDto
  latestRelease: PluginReleaseDto | null
  namespace: string
}

export function PluginHeader({ plugin, latestRelease, namespace }: PluginHeaderProps) {
  const downloadUrl = latestRelease
    ? `/api/v1/namespaces/${namespace}/plugins/${plugin.pluginId}/releases/${latestRelease.version}/download`
    : '#'

  return (
    <Box
      sx={{
        display: 'flex',
        alignItems: 'flex-start',
        gap: 2.5,
        pb: 3,
        borderBottom: '1px solid',
        borderColor: 'divider',
        mb: 2.5,
        flexWrap: { xs: 'wrap', sm: 'nowrap' },
      }}
    >
      {/* Plugin icon */}
      <Box
        sx={{
          width: { xs: 64, sm: 96 },
          height: { xs: 64, sm: 96 },
          borderRadius: tokens.radius.card,
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
        <Puzzle size={48} />
      </Box>

      {/* Plugin info */}
      <Box sx={{ flex: 1, minWidth: 0 }}>
        <Box sx={{ display: 'flex', alignItems: 'center', flexWrap: 'wrap', gap: 1, mb: 1 }}>
          <Typography variant="h1" sx={{ fontSize: { xs: '1.5rem', sm: '2.25rem' } }}>
            {plugin.name}
          </Typography>
          {latestRelease?.version && (
            <Badge variant="version">v{latestRelease.version}</Badge>
          )}
          {latestRelease?.status === 'published' && (
            <Badge variant="published">Published</Badge>
          )}
          {latestRelease?.status === 'draft' && (
            <Badge variant="draft">Draft</Badge>
          )}
        </Box>

        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 1.5 }}>
          <Typography variant="caption" color="text.disabled">
            by <strong style={{ color: 'inherit' }}>{plugin.author ?? namespace}</strong>
            {' · '}Namespace: <code>{namespace}</code>
          </Typography>
        </Box>

        <Box sx={{ display: 'flex', alignItems: 'center', gap: 3, flexWrap: 'wrap' }}>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5, color: 'text.disabled' }}>
            <Download size={14} aria-hidden="true" />
            <Typography variant="caption">{plugin.downloadCount ?? 0} downloads</Typography>
          </Box>
          {plugin.updatedAt && (
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5, color: 'text.disabled' }}>
              <Calendar size={14} aria-hidden="true" />
              <Typography variant="caption">
                Updated {new Date(plugin.updatedAt).toLocaleDateString()}
              </Typography>
            </Box>
          )}
          {plugin.license && (
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5, color: 'text.disabled' }}>
              <Scale size={14} aria-hidden="true" />
              <Typography variant="caption">{plugin.license}</Typography>
            </Box>
          )}
        </Box>
      </Box>

      {/* Actions */}
      {latestRelease && (
        <Box sx={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-end', gap: 1, flexShrink: 0 }}>
          <Button
            variant="contained"
            size="large"
            startIcon={<Download size={18} />}
            href={downloadUrl}
            download
            aria-label={`Download v${latestRelease.version}`}
          >
            Download v{latestRelease.version}
          </Button>
          {latestRelease.artifactSize && (
            <Typography variant="caption" color="text.disabled">
              {formatFileSize(latestRelease.artifactSize)} · .jar
            </Typography>
          )}
        </Box>
      )}
    </Box>
  )
}
