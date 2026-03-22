// SPDX-License-Identifier: AGPL-3.0
// Copyright (C) 2026 devtank42 GmbH
import { Select } from '@mui/material'

interface FilterSelectProps {
  value: string | number
  onChange: (value: string) => void
  'aria-label'?: string
  id?: string
  minWidth?: number
  children: React.ReactNode
}

export function FilterSelect({
  value,
  onChange,
  minWidth,
  children,
  'aria-label': ariaLabel,
  id,
}: FilterSelectProps) {
  return (
    <Select
      size="small"
      displayEmpty
      value={value}
      onChange={(e) => onChange(e.target.value as string)}
      SelectDisplayProps={{ 'aria-label': ariaLabel }}
      inputProps={{ id }}
      sx={{
        minWidth,
        height: 36,
        fontSize: '0.875rem',
        color: 'text.secondary',
        '& .MuiSelect-select': {
          py: 0,
          display: 'flex',
          alignItems: 'center',
          height: '100%',
        },
        '& .MuiOutlinedInput-notchedOutline': {
          borderColor: 'divider',
        },
        '&:hover:not(.Mui-focused) .MuiOutlinedInput-notchedOutline': {
          borderColor: 'text.disabled',
        },
        '&.Mui-focused .MuiOutlinedInput-notchedOutline': {
          borderColor: 'primary.main',
          borderWidth: '1px',
        },
      }}
    >
      {children}
    </Select>
  )
}
