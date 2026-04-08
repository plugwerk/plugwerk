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
  Typography,
  Tooltip,
  Snackbar,
  Alert,
  Menu,
  MenuItem,
} from '@mui/material'
import { Download, CheckCircle, Trash2, ArrowRightLeft, Copy, Check } from 'lucide-react'
import { DataTable } from '../common/DataTable'
import type { DataColumn } from '../common/DataTable'
import { ActionIconButton } from '../common/ActionIconButton'
import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { Badge } from '../common/Badge'
import { ConfirmDeleteDialog } from '../common/ConfirmDeleteDialog'
import type { PluginReleaseDto } from '../../api/generated/model'
import type { ReleaseStatusUpdateRequestStatusEnum } from '../../api/generated/model/release-status-update-request'
import { tokens } from '../../theme/tokens'
import type { BadgeVariant } from '../common/Badge'
import { managementApi, reviewsApi } from '../../api/config'
import { formatFileSize } from '../../utils/formatFileSize'
import { formatDateTime } from '../../utils/formatDateTime'
import { downloadArtifact } from '../../utils/downloadArtifact'
import { formatCount, formatCountFull } from '../../utils/formatCount'

function CopyableSha256({ value }: { value?: string }) {
  const [copied, setCopied] = useState(false)

  if (!value) return <Typography variant="caption" color="text.disabled">—</Typography>

  async function handleCopy() {
    await navigator.clipboard.writeText(value!)
    setCopied(true)
    setTimeout(() => setCopied(false), 2000)
  }

  return (
    <Tooltip title={copied ? 'Copied!' : 'Click to copy full SHA-256'}>
      <Box
        onClick={handleCopy}
        sx={{
          display: 'inline-flex',
          alignItems: 'center',
          gap: 0.5,
          cursor: 'pointer',
          '&:hover': { color: 'text.primary' },
        }}
      >
        <Typography variant="caption" sx={{ fontFamily: 'monospace', color: 'text.disabled' }}>
          {value.slice(0, 12)}…
        </Typography>
        {copied
          ? <Check size={12} color={tokens.color.success} />
          : <Copy size={12} style={{ opacity: 0.4 }} />}
      </Box>
    </Tooltip>
  )
}

interface VersionsTabProps {
  releases: PluginReleaseDto[]
  namespace: string
  pluginId: string
  canApprove?: boolean
  onReleaseDeleted?: (version: string) => void
  onReleasesChanged?: (releases: PluginReleaseDto[]) => void
}

const statusToBadge: Record<string, BadgeVariant> = {
  published:  'published',
  draft:      'draft',
  deprecated: 'deprecated',
  yanked:     'yanked',
}

const STATUS_TRANSITIONS: Record<string, ReleaseStatusUpdateRequestStatusEnum[]> = {
  published: ['deprecated', 'yanked'],
  deprecated: ['published', 'yanked'],
  yanked: ['published', 'deprecated'],
}

function getLatestPublishedVersion(rels: PluginReleaseDto[]): string | undefined {
  return rels.find((r) => r.status === 'published')?.version
}

export function VersionsTab({ releases, namespace, pluginId, canApprove, onReleaseDeleted, onReleasesChanged }: VersionsTabProps) {
  const navigate = useNavigate()
  const [localReleases, setLocalReleases] = useState(releases)
  const latestVersion = getLatestPublishedVersion(localReleases)
  const [approvingId, setApprovingId] = useState<string | null>(null)

  useEffect(() => { setLocalReleases(releases) }, [releases])

  function updateReleaseLocally(releaseId: string, newStatus: string) {
    setLocalReleases((prev) => {
      const updated = prev.map((r) => r.id === releaseId ? { ...r, status: newStatus as PluginReleaseDto['status'] } : r)
      onReleasesChanged?.(updated)
      return updated
    })
  }
  const [deleteTarget, setDeleteTarget] = useState<PluginReleaseDto | null>(null)
  const [isDeleting, setIsDeleting] = useState(false)
  const [toast, setToast] = useState<{ message: string; severity: 'success' | 'error' } | null>(null)
  const [statusMenuAnchor, setStatusMenuAnchor] = useState<HTMLElement | null>(null)
  const [statusMenuRelease, setStatusMenuRelease] = useState<PluginReleaseDto | null>(null)
  const [updatingStatusId, setUpdatingStatusId] = useState<string | null>(null)

  async function handleApprove(rel: PluginReleaseDto) {
    if (!rel.id) return
    setApprovingId(rel.id)
    try {
      await reviewsApi.approveRelease({ ns: namespace, releaseId: rel.id })
      setToast({ message: `v${rel.version} approved and published.`, severity: 'success' })
      updateReleaseLocally(rel.id!, 'published')
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
        navigate(`/namespaces/${namespace}/plugins`)
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

  async function handleStatusChange(rel: PluginReleaseDto, newStatus: ReleaseStatusUpdateRequestStatusEnum) {
    setStatusMenuAnchor(null)
    setStatusMenuRelease(null)
    setUpdatingStatusId(rel.id ?? null)
    try {
      await managementApi.updateReleaseStatus({
        ns: namespace,
        pluginId,
        version: rel.version,
        releaseStatusUpdateRequest: { status: newStatus },
      })
      updateReleaseLocally(rel.id!, newStatus)
      setToast({ message: `v${rel.version} status changed to ${newStatus}.`, severity: 'success' })
    } catch {
      setToast({ message: `Failed to change status of v${rel.version}.`, severity: 'error' })
    } finally {
      setUpdatingStatusId(null)
    }
  }

  const releaseColumns: DataColumn<PluginReleaseDto>[] = [
    {
      key: 'version',
      header: 'Version',
      render: (rel) => (
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
          <Badge variant="version">v{rel.version}</Badge>
          {rel.version === latestVersion && (
            <Typography variant="caption" sx={{ color: tokens.color.primary, fontWeight: 600 }}>
              latest
            </Typography>
          )}
        </Box>
      ),
    },
    {
      key: 'uploaded',
      header: 'Uploaded',
      render: (rel) => (
        <Typography variant="caption" color="text.disabled">
          {formatDateTime(rel.createdAt)}
        </Typography>
      ),
    },
    {
      key: 'status',
      header: 'Status',
      render: (rel) => (
        <Badge variant={statusToBadge[rel.status] ?? 'draft'}>
          {rel.status.charAt(0).toUpperCase() + rel.status.slice(1)}
        </Badge>
      ),
    },
    {
      key: 'format',
      header: 'Format',
      render: (rel) => (
        <Typography variant="caption" color="text.disabled">
          .{rel.fileFormat ?? 'jar'}
        </Typography>
      ),
    },
    {
      key: 'size',
      header: 'Size',
      align: 'right',
      render: (rel) => (
        <Typography variant="caption" color="text.disabled">
          {rel.artifactSize ? formatFileSize(rel.artifactSize) : '—'}
        </Typography>
      ),
    },
    {
      key: 'sha256',
      header: 'SHA-256',
      render: (rel) => <CopyableSha256 value={rel.artifactSha256} />,
    },
    {
      key: 'downloads',
      header: 'Downloads',
      align: 'right',
      render: (rel) => (
        <Tooltip title={formatCountFull(rel.downloadCount)} placement="left">
          <Typography variant="caption" color="text.disabled">
            {formatCount(rel.downloadCount)}
          </Typography>
        </Tooltip>
      ),
    },
    {
      key: 'actions',
      header: 'Actions',
      width: 140,
      render: (rel) => {
        const isDraft = rel.status === 'draft'
        const transitions = STATUS_TRANSITIONS[rel.status] ?? []
        return (
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
            <ActionIconButton
              icon={Download}
              tooltip={`Download .${rel.fileFormat ?? 'jar'}`}
              onClick={() => {
                downloadArtifact(
                  `/api/v1/namespaces/${namespace}/plugins/${pluginId}/releases/${rel.version}/download`,
                  `${pluginId}-${rel.version}.${rel.fileFormat ?? 'jar'}`,
                ).catch(() => setToast({ message: 'Download failed.', severity: 'error' }))
              }}
            />
            {isDraft && canApprove && (
              <ActionIconButton icon={CheckCircle} tooltip="Approve" color="success" loading={approvingId === rel.id} onClick={() => handleApprove(rel)} />
            )}
            {canApprove && transitions.length > 0 && (
              <ActionIconButton
                icon={ArrowRightLeft}
                tooltip="Change release status"
                loading={updatingStatusId === rel.id}
                onClick={(e) => { setStatusMenuAnchor(e.currentTarget as HTMLElement); setStatusMenuRelease(rel) }}
              />
            )}
            {canApprove && (
              <ActionIconButton icon={Trash2} tooltip="Delete release" color="error" onClick={() => setDeleteTarget(rel)} />
            )}
          </Box>
        )
      },
    },
  ]

  return (
    <Box sx={{ overflowX: 'auto' }}>
      <DataTable<PluginReleaseDto>
        columns={releaseColumns}
        rows={localReleases}
        keyFn={(rel) => rel.id ?? rel.version}
        ariaLabel="Release versions"
      />

      <Menu
        anchorEl={statusMenuAnchor}
        open={!!statusMenuAnchor && !!statusMenuRelease}
        onClose={() => { setStatusMenuAnchor(null); setStatusMenuRelease(null) }}
      >
        {(STATUS_TRANSITIONS[statusMenuRelease?.status ?? ''] ?? []).map((target) => (
          <MenuItem key={target} onClick={() => statusMenuRelease && handleStatusChange(statusMenuRelease, target)}>
            {target.charAt(0).toUpperCase() + target.slice(1)}
          </MenuItem>
        ))}
      </Menu>

      <ConfirmDeleteDialog
        open={!!deleteTarget}
        title="Delete Release"
        message={localReleases.length === 1
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
        anchorOrigin={{ vertical: 'top', horizontal: 'center' }}
      >
        <Alert severity={toast?.severity} onClose={() => setToast(null)} sx={{ width: '100%' }}>
          {toast?.message}
        </Alert>
      </Snackbar>
    </Box>
  )
}
