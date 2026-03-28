// SPDX-License-Identifier: AGPL-3.0
// Copyright (C) 2026 devtank42 GmbH
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  Button,
  Box,
  Typography,
  Tooltip,
  Snackbar,
  Alert,
} from '@mui/material'
import { Download, CheckCircle } from 'lucide-react'
import { useState } from 'react'
import { Badge } from '../common/Badge'
import type { PluginReleaseDto } from '../../api/generated/model'
import { tokens } from '../../theme/tokens'
import type { BadgeVariant } from '../common/Badge'
import { reviewsApi } from '../../api/config'
import { formatFileSize } from '../../utils/formatFileSize'

interface VersionsTabProps {
  releases: PluginReleaseDto[]
  namespace: string
  pluginId: string
  currentVersion?: string
  canApprove?: boolean
}

const statusToBadge: Record<string, BadgeVariant> = {
  published:  'published',
  draft:      'draft',
  deprecated: 'deprecated',
  yanked:     'yanked',
}

export function VersionsTab({ releases, namespace, pluginId, currentVersion, canApprove }: VersionsTabProps) {
  const [approvingId, setApprovingId] = useState<string | null>(null)
  const [toast, setToast] = useState<{ message: string; severity: 'success' | 'error' } | null>(null)

  async function handleApprove(rel: PluginReleaseDto) {
    if (!rel.id) return
    setApprovingId(rel.id)
    try {
      await reviewsApi.approveRelease({ ns: namespace, releaseId: rel.id })
      setToast({ message: `v${rel.version} approved and published.`, severity: 'success' })
      // Update status locally to avoid full reload
      rel.status = 'published'
    } catch {
      setToast({ message: `Failed to approve v${rel.version}.`, severity: 'error' })
    } finally {
      setApprovingId(null)
    }
  }

  return (
    <Box sx={{ overflowX: 'auto' }}>
      <Table aria-label="Release versions" size="small">
        <TableHead>
          <TableRow>
            <TableCell>Version</TableCell>
            <TableCell>Released</TableCell>
            <TableCell>Status</TableCell>
            <TableCell>Size</TableCell>
            <TableCell>Changelog</TableCell>
            <TableCell>Actions</TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {releases.map((rel) => {
            const isCurrent = rel.version === currentVersion
            const isDraft = rel.status === 'draft'
            const isYanked = rel.status === 'yanked'
            return (
              <TableRow
                key={rel.id}
                sx={{
                  opacity: isDraft ? 0.7 : 1,
                  ...(isDraft && {
                    borderLeft: `3px solid ${tokens.badge.draft.text}`,
                    background: tokens.badge.draft.bg + '55',
                  }),
                  ...(isCurrent && !isDraft && { background: tokens.color.primaryLight + '33' }),
                }}
              >
                <TableCell>
                  <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
                    <Badge variant="version">v{rel.version}</Badge>
                    {isCurrent && !isDraft && (
                      <Typography variant="caption" sx={{ color: tokens.color.primary, fontWeight: 600 }}>
                        current
                      </Typography>
                    )}
                  </Box>
                </TableCell>
                <TableCell>
                  <Typography variant="caption" color="text.disabled">
                    {rel.publishedAt ? new Date(rel.publishedAt).toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' }) : '—'}
                  </Typography>
                </TableCell>
                <TableCell>
                  <Badge variant={statusToBadge[rel.status] ?? 'draft'}>
                    {rel.status.charAt(0).toUpperCase() + rel.status.slice(1)}
                  </Badge>
                </TableCell>
                <TableCell>
                  <Typography variant="caption" color="text.disabled">
                    {rel.artifactSize ? formatFileSize(rel.artifactSize) : '—'}
                  </Typography>
                </TableCell>
                <TableCell>
                  <Typography variant="caption" color="text.secondary">
                    {rel.changelog ? rel.changelog.slice(0, 60) + (rel.changelog.length > 60 ? '…' : '') : '—'}
                  </Typography>
                </TableCell>
                <TableCell>
                  {isDraft && canApprove ? (
                    <Button
                      variant="outlined"
                      size="small"
                      color="success"
                      startIcon={<CheckCircle size={14} />}
                      loading={approvingId === rel.id}
                      onClick={() => handleApprove(rel)}
                    >
                      Approve
                    </Button>
                  ) : isDraft ? (
                    <Tooltip title="Awaiting review — download not available yet">
                      <Typography variant="caption" color="text.disabled">Pending review</Typography>
                    </Tooltip>
                  ) : isYanked ? (
                    <Typography variant="caption" color="text.disabled">Unavailable</Typography>
                  ) : (
                    <Button
                      variant="text"
                      size="small"
                      startIcon={<Download size={14} />}
                      href={`/api/v1/namespaces/${namespace}/plugins/${pluginId}/releases/${rel.version}/download`}
                      download
                    >
                      .jar
                    </Button>
                  )}
                </TableCell>
              </TableRow>
            )
          })}
        </TableBody>
      </Table>

      <Snackbar
        open={!!toast}
        autoHideDuration={4000}
        onClose={() => setToast(null)}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}
      >
        <Alert severity={toast?.severity} onClose={() => setToast(null)} sx={{ width: '100%' }}>
          {toast?.message}
        </Alert>
      </Snackbar>
    </Box>
  )
}
