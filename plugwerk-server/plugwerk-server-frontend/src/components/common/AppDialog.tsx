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
import type { ReactNode } from 'react'
import {
  Box,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  IconButton,
  Typography,
} from '@mui/material'
import { X } from 'lucide-react'

interface AppDialogProps {
  /** Whether the dialog is open. */
  open: boolean
  /** Called when the dialog should close (Cancel, X, Escape, overlay click). */
  onClose: () => void
  /** Dialog title shown in the header. */
  title: string
  /** Optional description shown below the title in muted color. */
  description?: string
  /** Form or content rendered below the description. */
  children?: ReactNode
  /** Label for the primary action button. */
  actionLabel: string
  /** Called when the primary action button is clicked. */
  onAction: () => void
  /** Color variant for the primary action button. Use "error" for destructive actions. */
  actionColor?: 'primary' | 'error'
  /** Whether the primary action button is disabled. */
  actionDisabled?: boolean
  /** Whether the primary action is in progress (shows loading text). */
  actionLoading?: boolean
  /** Label for the cancel button. Defaults to "Cancel". */
  cancelLabel?: string
  /** Maximum width of the dialog in pixels. Defaults to 560. */
  maxWidth?: number
  /** Whether to hide the footer action buttons (for custom footers). */
  hideActions?: boolean
}

/**
 * Shared dialog wrapper enforcing the three-section layout:
 *
 * ┌──────────────────────────────────────┐
 * │  Title                           [X] │  Header
 * ├──────────────────────────────────────┤
 * │  Description (muted, smaller font)   │
 * │                                      │  Content
 * │  {children}                          │
 * ├──────────────────────────────────────┤
 * │             [Cancel]   [Action]      │  Footer
 * └──────────────────────────────────────┘
 */
export function AppDialog({
  open,
  onClose,
  title,
  description,
  children,
  actionLabel,
  onAction,
  actionColor = 'primary',
  actionDisabled = false,
  actionLoading = false,
  cancelLabel = 'Cancel',
  maxWidth = 560,
  hideActions = false,
}: AppDialogProps) {
  const isDisabled = actionDisabled || actionLoading

  return (
    <Dialog
      open={open}
      onClose={onClose}
      fullWidth
      slotProps={{
        paper: {
          sx: { maxWidth },
        },
      }}
    >
      {/* ── Header ── */}
      <DialogTitle
        sx={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          pr: 1,
        }}
      >
        <Box component="span" sx={{ fontWeight: 600 }}>
          {title}
        </Box>
        <IconButton onClick={onClose} size="small" aria-label="Close dialog">
          <X size={18} />
        </IconButton>
      </DialogTitle>

      {/* ── Content ── */}
      <DialogContent>
        {description && (
          <Typography
            variant="body2"
            color="text.secondary"
            sx={{ mb: children ? 2.5 : 0 }}
          >
            {description}
          </Typography>
        )}
        {children}
      </DialogContent>

      {/* ── Footer ── */}
      {!hideActions && (
        <DialogActions>
          <Button onClick={onClose} disabled={actionLoading}>
            {cancelLabel}
          </Button>
          <Button
            variant="contained"
            color={actionColor}
            onClick={onAction}
            disabled={isDisabled}
          >
            {actionLoading ? `${actionLabel}\u2026` : actionLabel}
          </Button>
        </DialogActions>
      )}
    </Dialog>
  )
}
