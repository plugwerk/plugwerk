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
import { Box, Card, CardActionArea, Tooltip, Typography } from '@mui/material'
import { Download, Clock, Puzzle, HardDrive } from 'lucide-react'
import { useRef } from 'react'
import { Link } from 'react-router-dom'
import { Badge } from '../common/Badge'
import type { BadgeVariant } from '../common/Badge'
import { ActionIconButton } from '../common/ActionIconButton'
import { CopyablePluginId } from '../common/CopyablePluginId'
import type { PluginDto } from '../../api/generated/model'
import { tokens } from '../../theme/tokens'
import { useIsOverflowing } from '../../hooks/useIsOverflowing'
import { formatFileSize } from '../../utils/formatFileSize'
import { formatDateTime, formatRelativeTime } from '../../utils/formatDateTime'
import { downloadArtifact } from '../../utils/downloadArtifact'

const STATUS_BADGE: Record<string, BadgeVariant> = {
  suspended: 'suspended',
  archived: 'archived',
}

interface PluginCardProps {
  plugin: PluginDto
  namespace: string
}

function formatCount(n: number | undefined): string {
  if (!n) return '0'
  if (n >= 1000) return `${(n / 1000).toFixed(1)}k`
  return String(n)
}


export function PluginCard({ plugin, namespace }: PluginCardProps) {
  const isDraftOnly = plugin.hasDraftOnly === true
  const statusBadge = plugin.status ? STATUS_BADGE[plugin.status] : undefined
  const latestRelease = plugin.latestRelease
  const downloadUrl = latestRelease
    ? `/api/v1/namespaces/${namespace}/plugins/${plugin.pluginId}/releases/${latestRelease.version}/download`
    : null

  const nameRef = useRef<HTMLElement>(null)
  const providerRef = useRef<HTMLElement>(null)
  const descRef = useRef<HTMLElement>(null)
  const nameOverflowing = useIsOverflowing(nameRef)
  const providerOverflowing = useIsOverflowing(providerRef)
  const descOverflowing = useIsOverflowing(descRef)

  return (
    <Card
      component={Link}
      to={`/namespaces/${namespace}/plugins/${plugin.pluginId}`}
      role="listitem"
      aria-label={`${plugin.name} plugin`}
      sx={{
        display: 'flex',
        flexDirection: 'column',
        height: '100%',
        textDecoration: 'none',
        transition: 'border-color 0.15s, box-shadow 0.15s',
        ...(isDraftOnly && { opacity: 0.6, filter: 'saturate(0.5)' }),
        '&:hover': {
          borderColor: tokens.color.primary,
          boxShadow: `0 0 0 1px ${tokens.color.primary}`,
        },
      }}
    >
      <CardActionArea
        component="div"
        sx={{ flex: 1, display: 'flex', flexDirection: 'column', alignItems: 'flex-start', p: 2.5, gap: 1.5 }}
      >
        {/* Header: icon + meta */}
        <Box sx={{ display: 'flex', gap: 1.5, width: '100%' }}>
          <Box
            sx={{
              width: 48,
              height: 48,
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
            <Puzzle size={28} />
          </Box>
          <Box sx={{ flex: 1, minWidth: 0 }}>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5, mb: 0.5 }}>
              <Tooltip title={nameOverflowing ? plugin.name : ''} placement="top">
                <Typography
                  ref={nameRef}
                  variant="body1"
                  fontWeight={600}
                  sx={{
                    overflow: 'hidden',
                    textOverflow: 'ellipsis',
                    whiteSpace: 'nowrap',
                    flex: 1,
                    minWidth: 0,
                  }}
                >
                  {plugin.name}
                </Typography>
              </Tooltip>
              {isDraftOnly && <Badge variant="pending-review">Pending Review</Badge>}
              {statusBadge && (
                <Badge variant={statusBadge}>
                  {plugin.status!.charAt(0).toUpperCase() + plugin.status!.slice(1)}
                </Badge>
              )}
              {latestRelease?.version && (
                <Badge variant="version">v{latestRelease.version}</Badge>
              )}
            </Box>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
              <Tooltip title={providerOverflowing ? (plugin.provider ?? '') : ''} placement="bottom">
                <Typography
                  ref={providerRef}
                  variant="caption"
                  color="text.disabled"
                  sx={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', minWidth: 0 }}
                >
                  {plugin.provider ?? ''}
                </Typography>
              </Tooltip>
            </Box>
            <CopyablePluginId pluginId={plugin.pluginId} />
          </Box>
        </Box>

        {/* Description */}
        {plugin.description && (
          <Tooltip title={descOverflowing ? plugin.description : ''} placement="bottom">
            <Typography
              ref={descRef}
              variant="body2"
              color="text.secondary"
              sx={{
                display: '-webkit-box',
                WebkitLineClamp: 2,
                WebkitBoxOrient: 'vertical',
                overflow: 'hidden',
                lineHeight: 1.6,
              }}
            >
              {plugin.description}
            </Typography>
          </Tooltip>
        )}

        {/* Tags */}
        {plugin.tags && plugin.tags.length > 0 && (
          <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 0.5 }} aria-label="Tags">
            {plugin.tags.slice(0, 4).map((tag) => (
              <Badge key={tag} variant="tag">{tag}</Badge>
            ))}
          </Box>
        )}

        {/* Stats */}
        <Box
          sx={{ display: 'flex', alignItems: 'center', gap: 2, mt: 'auto' }}
          aria-label="Plugin statistics"
        >
          {downloadUrl && (
            <ActionIconButton
              icon={Download}
              tooltip="Download latest release"
              onClick={(e) => {
                e.preventDefault()
                e.stopPropagation()
                downloadArtifact(
                  downloadUrl,
                  `${plugin.pluginId}-${latestRelease!.version}.${latestRelease!.fileFormat ?? 'jar'}`,
                ).catch(() => {})
              }}
            />
          )}
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5, color: 'text.disabled' }}>
            <Download size={12} aria-hidden="true" />
            <Typography variant="caption">{formatCount(plugin.downloadCount ?? 0)}</Typography>
          </Box>
          {latestRelease?.artifactSize && (
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5, color: 'text.disabled' }}>
              <HardDrive size={12} aria-hidden="true" />
              <Typography variant="caption">{formatFileSize(latestRelease.artifactSize)}</Typography>
            </Box>
          )}
          <Tooltip title={formatDateTime(plugin.updatedAt)} placement="top">
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5, color: 'text.disabled', cursor: 'default' }}>
              <Clock size={12} aria-hidden="true" />
              <Typography variant="caption">
                {formatRelativeTime(plugin.updatedAt)}
              </Typography>
            </Box>
          </Tooltip>
        </Box>
      </CardActionArea>
    </Card>
  )
}
