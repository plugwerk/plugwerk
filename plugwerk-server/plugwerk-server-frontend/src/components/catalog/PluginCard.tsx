// SPDX-License-Identifier: AGPL-3.0
// Copyright (C) 2026 devtank42 GmbH
import { Box, Card, CardActionArea, Tooltip, Typography } from '@mui/material'
import { Download, Clock, Puzzle } from 'lucide-react'
import { useRef } from 'react'
import { Link } from 'react-router-dom'
import { Badge } from '../common/Badge'
import type { PluginDto } from '../../api/generated/model'
import { tokens } from '../../theme/tokens'
import { useIsOverflowing } from '../../hooks/useIsOverflowing'

interface PluginCardProps {
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

export function PluginCard({ plugin, namespace }: PluginCardProps) {
  const isDeprecated = plugin.status === 'archived'
  const isDraft = !plugin.latestVersion && !!plugin.latestDraftVersion

  const nameRef = useRef<HTMLElement>(null)
  const authorRef = useRef<HTMLElement>(null)
  const descRef = useRef<HTMLElement>(null)
  const nameOverflowing = useIsOverflowing(nameRef)
  const authorOverflowing = useIsOverflowing(authorRef)
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
              {(plugin.latestVersion ?? plugin.latestDraftVersion) && (
                <Badge variant="version">v{plugin.latestVersion ?? plugin.latestDraftVersion}</Badge>
              )}
            </Box>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
              <Tooltip title={authorOverflowing ? (plugin.author ?? namespace) : ''} placement="bottom">
                <Typography
                  ref={authorRef}
                  variant="caption"
                  color="text.disabled"
                  sx={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', minWidth: 0 }}
                >
                  {plugin.author ?? namespace}
                </Typography>
              </Tooltip>
              {isDraft && <Badge variant="draft">Draft</Badge>}
              {isDeprecated && <Badge variant="deprecated">Deprecated</Badge>}
            </Box>
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
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5, color: 'text.disabled' }}>
            <Download size={12} aria-hidden="true" />
            <Typography variant="caption">{formatCount(plugin.downloadCount ?? 0)}</Typography>
          </Box>
          <Tooltip title={formatAbsoluteTime(plugin.updatedAt)} placement="top">
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
