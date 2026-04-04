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
