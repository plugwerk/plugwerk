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
import { Box } from '@mui/material'
import { Check } from 'lucide-react'
import { tokens } from '../../theme/tokens'
import type { ReactNode } from 'react'

export type BadgeVariant =
  | 'version'
  | 'published'
  | 'draft'
  | 'deprecated'
  | 'yanked'
  | 'tag'
  | 'verified'
  | 'suspended'
  | 'archived'
  | 'pending-review'

interface BadgeProps {
  variant: BadgeVariant
  children: ReactNode
}

const styles: Record<BadgeVariant, { bg: string; color: string }> = {
  version:    { bg: tokens.badge.version.bg,    color: tokens.badge.version.text },
  published:  { bg: tokens.badge.published.bg,  color: tokens.badge.published.text },
  draft:      { bg: tokens.badge.draft.bg,       color: tokens.badge.draft.text },
  deprecated: { bg: tokens.badge.deprecated.bg,  color: tokens.badge.deprecated.text },
  yanked:     { bg: tokens.badge.yanked.bg,       color: tokens.badge.yanked.text },
  tag:            { bg: tokens.badge.tag.bg,            color: tokens.badge.tag.text },
  verified:       { bg: tokens.badge.published.bg,      color: tokens.badge.published.text },
  suspended:      { bg: tokens.badge.suspended.bg,      color: tokens.badge.suspended.text },
  archived:       { bg: tokens.badge.archived.bg,       color: tokens.badge.archived.text },
  'pending-review': { bg: tokens.badge.pendingReview.bg, color: tokens.badge.pendingReview.text },
}

export function Badge({ variant, children }: BadgeProps) {
  const s = styles[variant]
  return (
    <Box
      component="span"
      sx={{
        display: 'inline-flex',
        alignItems: 'center',
        gap: '3px',
        px: '6px',
        py: '2px',
        borderRadius: tokens.radius.btn,
        background: s.bg,
        color: s.color,
        fontSize: '0.6875rem',
        fontWeight: 600,
        lineHeight: '16px',
        whiteSpace: 'nowrap',
      }}
    >
      {variant === 'verified' && <Check size={10} aria-hidden="true" />}
      {children}
    </Box>
  )
}
