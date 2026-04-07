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
import { useState } from 'react'
import { Box, Typography, Button, Menu, MenuItem } from '@mui/material'
import { Download, Calendar, Scale, Puzzle, Trash2, ArrowRightLeft } from 'lucide-react'
import { Badge } from '../common/Badge'
import type { BadgeVariant } from '../common/Badge'
import { CopyablePluginId } from '../common/CopyablePluginId'
import type { PluginDto, PluginReleaseDto } from '../../api/generated/model'
import { tokens } from '../../theme/tokens'
import { formatDateTime } from '../../utils/formatDateTime'
import { downloadArtifact } from '../../utils/downloadArtifact'
import { managementApi } from '../../api/config'

const PLUGIN_STATUSES = ['active', 'suspended', 'archived'] as const

const PLUGIN_STATUS_BADGE: Record<string, BadgeVariant> = {
  active: 'published',
  suspended: 'yanked',
  archived: 'deprecated',
}

interface PluginHeaderProps {
  plugin: PluginDto
  latestRelease: PluginReleaseDto | null
  namespace: string
  isAdmin?: boolean
  onDeletePlugin?: () => void
  onError?: (message: string) => void
  onPluginUpdated?: (plugin: PluginDto) => void
}

export function PluginHeader({ plugin, latestRelease, namespace, isAdmin, onDeletePlugin, onError, onPluginUpdated }: PluginHeaderProps) {
  const [statusMenuAnchor, setStatusMenuAnchor] = useState<HTMLElement | null>(null)
  const [updatingStatus, setUpdatingStatus] = useState(false)

  const downloadUrl = latestRelease
    ? `/api/v1/namespaces/${namespace}/plugins/${plugin.pluginId}/releases/${latestRelease.version}/download`
    : '#'

  async function handleStatusChange(newStatus: string) {
    setStatusMenuAnchor(null)
    setUpdatingStatus(true)
    try {
      const res = await managementApi.updatePlugin({
        ns: namespace,
        pluginId: plugin.pluginId!,
        pluginUpdateRequest: { status: newStatus as 'active' | 'suspended' | 'archived' },
      })
      onPluginUpdated?.(res.data)
    } catch {
      onError?.(`Failed to change plugin status to ${newStatus}.`)
    } finally {
      setUpdatingStatus(false)
    }
  }

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
          {plugin.status && (
            <Badge variant={PLUGIN_STATUS_BADGE[plugin.status] ?? 'published'}>
              {plugin.status.charAt(0).toUpperCase() + plugin.status.slice(1)}
            </Badge>
          )}
        </Box>

        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 0.5 }}>
          <Typography variant="caption" color="text.disabled">
            by <strong style={{ color: 'inherit' }}>{plugin.provider ?? namespace}</strong>
            {' · '}Namespace: <code>{namespace}</code>
          </Typography>
        </Box>
        <Box sx={{ mb: 1.5 }}>
          <CopyablePluginId pluginId={plugin.pluginId} />
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
        {isAdmin && (
          <>
            <Button
              variant="outlined"
              size="medium"
              startIcon={<ArrowRightLeft size={15} />}
              aria-label="Change plugin status"
              disabled={updatingStatus}
              onClick={(e) => setStatusMenuAnchor(e.currentTarget)}
              sx={{ borderRadius: tokens.radius.btn }}
            >
              {updatingStatus ? 'Changing\u2026' : 'Change Status'}
            </Button>
            <Menu
              anchorEl={statusMenuAnchor}
              open={!!statusMenuAnchor}
              onClose={() => setStatusMenuAnchor(null)}
            >
              {PLUGIN_STATUSES.filter((s) => s !== plugin.status).map((s) => (
                <MenuItem key={s} onClick={() => handleStatusChange(s)}>
                  {s.charAt(0).toUpperCase() + s.slice(1)}
                </MenuItem>
              ))}
            </Menu>
          </>
        )}
        {isAdmin && onDeletePlugin && (
          <Button
            variant="outlined"
            size="medium"
            color="error"
            startIcon={<Trash2 size={15} />}
            aria-label="Delete plugin"
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
