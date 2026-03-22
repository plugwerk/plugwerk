// SPDX-License-Identifier: AGPL-3.0
// Copyright (C) 2026 devtank42 GmbH
import { Box, IconButton, Typography } from '@mui/material'
import { Info, CheckCircle, AlertTriangle, XCircle, X } from 'lucide-react'
import { useUiStore, type ToastItem } from '../../stores/uiStore'
import { tokens } from '../../theme/tokens'

const iconMap = {
  info:    <Info size={18} />,
  success: <CheckCircle size={18} />,
  warning: <AlertTriangle size={18} />,
  error:   <XCircle size={18} />,
}

const colorMap = {
  info:    tokens.color.primary,
  success: tokens.color.success,
  warning: tokens.color.warning,
  error:   tokens.color.danger,
}

function ToastItem({ toast }: { toast: ToastItem }) {
  const { removeToast } = useUiStore()
  const color = colorMap[toast.type]

  return (
    <Box
      role="status"
      sx={{
        display: 'flex',
        alignItems: 'flex-start',
        gap: 1.5,
        p: 1.5,
        pr: 1,
        bgcolor: 'background.paper',
        border: `1px solid`,
        borderColor: 'divider',
        borderRadius: tokens.radius.card,
        boxShadow: '0 4px 12px rgba(0,0,0,0.15)',
        minWidth: 280,
        maxWidth: 400,
      }}
    >
      <Box sx={{ color, flexShrink: 0, mt: '1px' }}>
        {iconMap[toast.type]}
      </Box>
      <Box sx={{ flex: 1 }}>
        {toast.title && (
          <Typography variant="body2" fontWeight={600}>
            {toast.title}
          </Typography>
        )}
        {toast.message && (
          <Typography variant="caption" color="text.secondary">
            {toast.message}
          </Typography>
        )}
      </Box>
      <IconButton
        size="small"
        onClick={() => removeToast(toast.id)}
        aria-label="Dismiss notification"
        sx={{ color: 'text.disabled', p: 0.25 }}
      >
        <X size={14} />
      </IconButton>
    </Box>
  )
}

export function ToastRegion() {
  const { toasts } = useUiStore()

  return (
    <Box
      role="region"
      aria-label="Notifications"
      aria-live="polite"
      sx={{
        position: 'fixed',
        bottom: 24,
        right: 24,
        zIndex: 2000,
        display: 'flex',
        flexDirection: 'column',
        gap: 1,
      }}
    >
      {toasts.map((t) => (
        <ToastItem key={t.id} toast={t} />
      ))}
    </Box>
  )
}
