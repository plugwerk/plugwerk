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
