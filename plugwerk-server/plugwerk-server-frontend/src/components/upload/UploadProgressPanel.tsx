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
import { useEffect, useRef } from 'react'
import {
  Box,
  IconButton,
  LinearProgress,
  Paper,
  Slide,
  Typography,
} from '@mui/material'
import { CheckCircle, Loader2, X, XCircle, FileBox } from 'lucide-react'
import { useUploadStore, type FileUploadEntry } from '../../stores/uploadStore'
import { tokens } from '../../theme/tokens'

const AUTO_DISMISS_MS = 5000

const statusColor: Record<string, string> = {
  pending: tokens.color.gray40,
  uploading: tokens.color.primary,
  success: tokens.color.success,
  failed: tokens.color.danger,
}

function StatusIcon({ status }: { status: FileUploadEntry['status'] }) {
  switch (status) {
    case 'uploading':
    case 'pending':
      return (
        <Loader2
          size={16}
          color={status === 'uploading' ? tokens.color.primary : tokens.color.gray40}
          style={{
            animation: status === 'uploading' ? 'upload-spin 1s linear infinite' : undefined,
          }}
        />
      )
    case 'success':
      return <CheckCircle size={16} color={tokens.color.success} />
    case 'failed':
      return <XCircle size={16} color={tokens.color.danger} />
  }
}

function EntryRow({ entry }: { entry: FileUploadEntry }) {
  return (
    <Box
      sx={{
        px: 2,
        py: 1,
        borderBottom: '1px solid',
        borderColor: 'divider',
        '&:last-child': { borderBottom: 'none' },
      }}
    >
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5, minWidth: 0 }}>
        <Box sx={{ flexShrink: 0, display: 'flex', alignItems: 'center' }}>
          <StatusIcon status={entry.status} />
        </Box>

        <Box sx={{ flex: 1, minWidth: 0 }}>
          <Typography
            variant="body2"
            sx={{
              overflow: 'hidden',
              textOverflow: 'ellipsis',
              whiteSpace: 'nowrap',
              fontWeight: 500,
              lineHeight: 1.4,
            }}
          >
            {entry.fileName}
          </Typography>
        </Box>

        {(entry.status === 'uploading' || entry.status === 'pending') && (
          <Typography
            variant="caption"
            color="text.secondary"
            sx={{ flexShrink: 0, fontVariantNumeric: 'tabular-nums' }}
          >
            {entry.progress}%
          </Typography>
        )}
      </Box>

      {entry.status === 'uploading' && (
        <LinearProgress
          variant="determinate"
          value={entry.progress}
          sx={{ mt: 0.75, height: 3, borderRadius: 1 }}
        />
      )}

      {entry.status === 'failed' && entry.errorMessage && (
        <Typography
          variant="caption"
          sx={{
            color: tokens.color.danger,
            display: 'block',
            mt: 0.5,
            lineHeight: 1.3,
          }}
        >
          {entry.errorMessage}
        </Typography>
      )}
    </Box>
  )
}

export function UploadProgressPanel() {
  const { entries, panelVisible, dismissPanel } = useUploadStore()

  const totalCount = entries.length
  const successCount = entries.filter((e) => e.status === 'success').length
  const failedCount = entries.filter((e) => e.status === 'failed').length
  const isComplete = totalCount > 0 && successCount + failedCount === totalCount
  // Auto-dismiss after 5 seconds when all uploads have completed (success or failed)
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null)
  useEffect(() => {
    if (isComplete && panelVisible) {
      timerRef.current = setTimeout(() => dismissPanel(), AUTO_DISMISS_MS)
    }
    return () => {
      if (timerRef.current) clearTimeout(timerRef.current)
    }
  }, [isComplete, panelVisible, dismissPanel])

  const headerText = isComplete
    ? `Upload complete — ${successCount} succeeded`
    : `Uploading ${totalCount} file${totalCount !== 1 ? 's' : ''}`

  return (
    <Slide direction="up" in={panelVisible} mountOnEnter unmountOnExit>
      <Paper
        elevation={0}
        role="region"
        aria-label="Upload progress"
        sx={{
          position: 'fixed',
          bottom: 24,
          right: 24,
          width: 380,
          maxHeight: 400,
          display: 'flex',
          flexDirection: 'column',
          zIndex: 1300,
          borderRadius: tokens.radius.dialog,
          boxShadow: tokens.shadow.modal,
          border: '1px solid',
          borderColor: 'divider',
          overflow: 'hidden',
          '@keyframes upload-spin': {
            from: { transform: 'rotate(0deg)' },
            to: { transform: 'rotate(360deg)' },
          },
        }}
      >
        {/* Header */}
        <Box
          sx={{
            display: 'flex',
            alignItems: 'center',
            gap: 1.5,
            px: 2,
            py: 1.5,
            borderBottom: '1px solid',
            borderColor: 'divider',
            bgcolor: isComplete ? undefined : 'action.hover',
            borderLeft: `3px solid ${isComplete
              ? (failedCount > 0 ? tokens.color.warning : tokens.color.success)
              : tokens.color.primary}`,
          }}
        >
          <FileBox size={18} color={statusColor[isComplete ? (failedCount > 0 ? 'failed' : 'success') : 'uploading']} />
          <Box sx={{ flex: 1, minWidth: 0 }}>
            <Typography variant="body2" fontWeight={600} sx={{ lineHeight: 1.3 }}>
              {headerText}
            </Typography>
            {isComplete && failedCount > 0 && (
              <Typography variant="caption" sx={{ color: tokens.color.danger, lineHeight: 1.3 }}>
                {failedCount} failed
              </Typography>
            )}
          </Box>
          <IconButton
            size="small"
            onClick={dismissPanel}
            aria-label="Dismiss upload panel"
            sx={{ color: 'text.disabled', p: 0.5 }}
          >
            <X size={16} />
          </IconButton>
        </Box>

        {/* File list */}
        <Box sx={{ overflowY: 'auto', flex: 1 }}>
          {entries.map((entry) => (
            <EntryRow key={entry.id} entry={entry} />
          ))}
        </Box>
      </Paper>
    </Slide>
  )
}
