// SPDX-License-Identifier: AGPL-3.0
// Copyright (C) 2026 devtank42 GmbH
import { Alert, Typography } from '@mui/material'
import { Link } from 'react-router-dom'
import { tokens } from '../../theme/tokens'

interface PendingReviewBannerProps {
  count: number
  namespace: string
  isAdmin: boolean
}

export function PendingReviewBanner({ count, namespace, isAdmin }: PendingReviewBannerProps) {
  if (count <= 0) return null

  return (
    <Alert
      severity="info"
      sx={{
        py: 0.5,
        px: 2,
        alignItems: 'center',
        '& .MuiAlert-message': { display: 'flex', alignItems: 'center', gap: 1 },
      }}
    >
      <Typography variant="body2" sx={{ whiteSpace: 'nowrap' }}>
        {count} {count === 1 ? 'plugin' : 'plugins'} pending review
      </Typography>
      {isAdmin && (
        <Typography
          component={Link}
          to={`/namespaces/${namespace}/reviews/pending`}
          variant="body2"
          sx={{
            color: tokens.color.primary,
            textDecoration: 'none',
            whiteSpace: 'nowrap',
            fontWeight: 600,
            '&:hover': { textDecoration: 'underline' },
          }}
        >
          Review
        </Typography>
      )}
    </Alert>
  )
}
