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
import { Box, Typography } from '@mui/material'
import type { SxProps, Theme } from '@mui/material'

export interface DataColumn<T> {
  key: string
  header: string
  align?: 'left' | 'right' | 'center'
  width?: string | number
  render: (row: T) => React.ReactNode
}

interface DataTableProps<T> {
  columns: DataColumn<T>[]
  rows: T[]
  keyFn: (row: T) => string
  ariaLabel: string
  rowSx?: (row: T) => SxProps<Theme> | undefined
  onRowClick?: (row: T) => void
  emptyMessage?: string
}

export function DataTable<T>({ columns, rows, keyFn, ariaLabel, rowSx, onRowClick, emptyMessage }: DataTableProps<T>) {
  if (rows.length === 0 && emptyMessage) {
    return (
      <Typography variant="body2" color="text.secondary" sx={{ py: 3 }}>
        {emptyMessage}
      </Typography>
    )
  }

  return (
    <Box
      component="table"
      role="table"
      aria-label={ariaLabel}
      sx={{
        width: '100%',
        borderCollapse: 'collapse',
        tableLayout: 'auto',
      }}
    >
      <Box component="thead">
        <Box component="tr">
          {columns.map((col) => (
            <Box
              component="th"
              key={col.key}
              sx={{
                textAlign: col.align ?? 'left',
                width: col.width,
                px: 2,
                py: 1,
                borderBottom: '1px solid',
                borderColor: 'divider',
                fontSize: '0.75rem',
                fontWeight: 600,
                color: 'text.disabled',
                textTransform: 'uppercase',
                letterSpacing: '0.04em',
                whiteSpace: 'nowrap',
              }}
            >
              {col.header}
            </Box>
          ))}
        </Box>
      </Box>
      <Box component="tbody">
        {rows.map((row) => (
          <Box
            component="tr"
            key={keyFn(row)}
            onClick={onRowClick ? () => onRowClick(row) : undefined}
            sx={{
              bgcolor: 'background.paper',
              transition: 'background-color 0.15s',
              '&:hover': { bgcolor: 'background.default' },
              '&:last-child td': { borderBottom: 'none' },
              ...(onRowClick && { cursor: 'pointer' }),
              ...(rowSx?.(row) as object),
            }}
          >
            {columns.map((col) => (
              <Box
                component="td"
                key={col.key}
                sx={{
                  textAlign: col.align ?? 'left',
                  width: col.width,
                  px: 2,
                  py: 1.5,
                  borderBottom: '1px solid',
                  borderColor: 'divider',
                  fontSize: '0.875rem',
                  verticalAlign: 'middle',
                }}
              >
                {col.render(row)}
              </Box>
            ))}
          </Box>
        ))}
      </Box>
    </Box>
  )
}
