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
import { Download, CheckCircle, Trash2 } from 'lucide-react'
import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Badge } from '../common/Badge'
import { ConfirmDeleteDialog } from '../common/ConfirmDeleteDialog'
import type { PluginReleaseDto } from '../../api/generated/model'
import { tokens } from '../../theme/tokens'
import type { BadgeVariant } from '../common/Badge'
import { managementApi, reviewsApi } from '../../api/config'
import { formatFileSize } from '../../utils/formatFileSize'
import { formatDateTime } from '../../utils/formatDateTime'
import { downloadArtifact } from '../../utils/downloadArtifact'

interface VersionsTabProps {
  releases: PluginReleaseDto[]
  namespace: string
  pluginId: string
  currentVersion?: string
  canApprove?: boolean
  onReleaseDeleted?: (version: string) => void
}

const statusToBadge: Record<string, BadgeVariant> = {
  published:  'published',
  draft:      'draft',
  deprecated: 'deprecated',
  yanked:     'yanked',
}

export function VersionsTab({ releases, namespace, pluginId, currentVersion, canApprove, onReleaseDeleted }: VersionsTabProps) {
  const navigate = useNavigate()
  const [approvingId, setApprovingId] = useState<string | null>(null)
  const [deleteTarget, setDeleteTarget] = useState<PluginReleaseDto | null>(null)
  const [isDeleting, setIsDeleting] = useState(false)
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

  async function handleDeleteRelease() {
    if (!deleteTarget?.version) return
    setIsDeleting(true)
    try {
      const response = await managementApi.deleteRelease({ ns: namespace, pluginId, version: deleteTarget.version })
      const pluginDeleted = response.headers?.['x-plugin-deleted'] === 'true'
      if (pluginDeleted) {
        setToast({ message: 'Plugin and release deleted.', severity: 'success' })
        navigate(`/${namespace}`)
      } else {
        setToast({ message: `v${deleteTarget.version} deleted.`, severity: 'success' })
        onReleaseDeleted?.(deleteTarget.version)
      }
    } catch {
      setToast({ message: `Failed to delete v${deleteTarget.version}.`, severity: 'error' })
    } finally {
      setIsDeleting(false)
      setDeleteTarget(null)
    }
  }

  return (
    <Box sx={{ overflowX: 'auto' }}>
      <Table aria-label="Release versions" size="small">
        <TableHead>
          <TableRow>
            <TableCell>Version</TableCell>
            <TableCell>Uploaded</TableCell>
            <TableCell>Status</TableCell>
            <TableCell>Format</TableCell>
            <TableCell>Size</TableCell>
            <TableCell>SHA-256</TableCell>
            <TableCell align="right">Downloads</TableCell>
            <TableCell sx={{ width: 120 }}>Actions</TableCell>
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
                    {formatDateTime(rel.createdAt)}
                  </Typography>
                </TableCell>
                <TableCell>
                  <Badge variant={statusToBadge[rel.status] ?? 'draft'}>
                    {rel.status.charAt(0).toUpperCase() + rel.status.slice(1)}
                  </Badge>
                </TableCell>
                <TableCell>
                  <Typography variant="caption" color="text.disabled">
                    .{rel.fileFormat ?? 'jar'}
                  </Typography>
                </TableCell>
                <TableCell>
                  <Typography variant="caption" color="text.disabled">
                    {rel.artifactSize ? formatFileSize(rel.artifactSize) : '—'}
                  </Typography>
                </TableCell>
                <TableCell>
                  <Tooltip title={rel.artifactSha256 ?? ''}>
                    <Typography variant="caption" sx={{ fontFamily: 'monospace', color: 'text.disabled' }}>
                      {rel.artifactSha256 ? rel.artifactSha256.slice(0, 12) + '…' : '—'}
                    </Typography>
                  </Tooltip>
                </TableCell>
                <TableCell align="right">
                  <Typography variant="caption" color="text.disabled">
                    {rel.downloadCount ?? 0}
                  </Typography>
                </TableCell>
                <TableCell>
                  <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
                    {isDraft && canApprove ? (
                      <Button
                        variant="outlined"
                        size="small"
                        color="success"
                        startIcon={<CheckCircle size={14} />}
                        loading={approvingId === rel.id}
                        onClick={() => handleApprove(rel)}
                        sx={{ borderRadius: tokens.radius.btn }}
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
                      <Tooltip title={`Download .${rel.fileFormat ?? 'jar'}`}>
                        <Button
                          variant="text"
                          size="small"
                          aria-label={`download release ${rel.version}`}
                          sx={{ minWidth: 'auto', p: 0.5, borderRadius: tokens.radius.btn }}
                          onClick={() => {
                            downloadArtifact(
                              `/api/v1/namespaces/${namespace}/plugins/${pluginId}/releases/${rel.version}/download`,
                              `${pluginId}-${rel.version}.${rel.fileFormat ?? 'jar'}`,
                            ).catch(() => setToast({ message: 'Download failed.', severity: 'error' }))
                          }}
                        >
                          <Download size={14} />
                        </Button>
                      </Tooltip>
                    )}
                    {canApprove && (
                      <Tooltip title="Delete release">
                        <Button
                          variant="text"
                          size="small"
                          color="error"
                          aria-label={`delete release ${rel.version}`}
                          onClick={() => setDeleteTarget(rel)}
                          sx={{ minWidth: 'auto', p: 0.5, borderRadius: tokens.radius.btn }}
                        >
                          <Trash2 size={14} />
                        </Button>
                      </Tooltip>
                    )}
                  </Box>
                </TableCell>
              </TableRow>
            )
          })}
        </TableBody>
      </Table>

      <ConfirmDeleteDialog
        open={!!deleteTarget}
        title="Delete Release"
        message={releases.length === 1
          ? `Are you sure you want to delete v${deleteTarget?.version ?? ''}? This is the last release — the entire plugin will also be removed. This action cannot be undone.`
          : `Are you sure you want to delete v${deleteTarget?.version ?? ''}? This action cannot be undone.`}
        onConfirm={handleDeleteRelease}
        onCancel={() => setDeleteTarget(null)}
        loading={isDeleting}
      />

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
