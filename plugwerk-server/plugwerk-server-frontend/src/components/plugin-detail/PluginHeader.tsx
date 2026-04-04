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
import { Box, Typography, Button } from '@mui/material'
import { Download, Calendar, Scale, Puzzle, Trash2 } from 'lucide-react'
import { Badge } from '../common/Badge'
import type { PluginDto, PluginReleaseDto } from '../../api/generated/model'
import { tokens } from '../../theme/tokens'
import { formatDateTime } from '../../utils/formatDateTime'
import { downloadArtifact } from '../../utils/downloadArtifact'

interface PluginHeaderProps {
  plugin: PluginDto
  latestRelease: PluginReleaseDto | null
  namespace: string
  isAdmin?: boolean
  onDeletePlugin?: () => void
  onError?: (message: string) => void
}

export function PluginHeader({ plugin, latestRelease, namespace, isAdmin, onDeletePlugin, onError }: PluginHeaderProps) {
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
            by <strong style={{ color: 'inherit' }}>{plugin.provider ?? namespace}</strong>
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
                Updated {formatDateTime(plugin.updatedAt)}
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

        {plugin.tags && plugin.tags.length > 0 && (
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5, flexWrap: 'wrap', mt: 1.5 }}>
            {plugin.tags.map((tag) => (
              <Badge key={tag} variant="tag">{tag}</Badge>
            ))}
          </Box>
        )}
      </Box>

      {/* Actions */}
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, flexShrink: 0 }}>
        {latestRelease && (
          <Button
            variant="outlined"
            size="medium"
            color="primary"
            startIcon={<Download size={15} />}
            aria-label={`Download v${latestRelease.version}`}
            sx={{ borderRadius: tokens.radius.btn }}
            onClick={() => {
              downloadArtifact(
                downloadUrl,
                `${plugin.pluginId}-${latestRelease.version}.${latestRelease.fileFormat ?? 'jar'}`,
              ).catch((err: Error) => onError?.(err.message))
            }}
          >
            Download
          </Button>
        )}
        {isAdmin && onDeletePlugin && (
          <Button
            variant="outlined"
            size="medium"
            color="error"
            startIcon={<Trash2 size={15} />}
            aria-label="delete plugin"
            onClick={onDeletePlugin}
            sx={{ borderRadius: tokens.radius.btn }}
          >
            Delete
          </Button>
        )}
      </Box>
    </Box>
  )
}
