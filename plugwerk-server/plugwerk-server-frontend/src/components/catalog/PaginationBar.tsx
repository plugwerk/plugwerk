// SPDX-License-Identifier: AGPL-3.0
// Copyright (C) 2026 devtank42 GmbH
import { Box, MenuItem, Pagination, Typography } from '@mui/material'
import { usePluginStore } from '../../stores/pluginStore'
import { FilterSelect } from '../common/FilterSelect'

interface PaginationBarProps {
  namespace: string
}

const PAGE_SIZES = [12, 24, 48]

export function PaginationBar({ namespace }: PaginationBarProps) {
  const { filters, totalElements, totalPages, setFilters, fetchPlugins } = usePluginStore()

  function handlePageChange(_: unknown, page: number) {
    setFilters({ page: page - 1 })
    fetchPlugins(namespace)
  }

  function handleSizeChange(value: string) {
    setFilters({ size: Number(value), page: 0 })
    fetchPlugins(namespace)
  }

  const start = filters.page * filters.size + 1
  const end = Math.min(start + filters.size - 1, totalElements)

  return (
    <Box
      sx={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        flexWrap: 'wrap',
        gap: 2,
        mt: 4,
      }}
    >
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
        <Typography variant="caption" color="text.primary">Show:</Typography>
        <FilterSelect
          value={filters.size}
          onChange={handleSizeChange}
          aria-label="Items per page"
          minWidth={70}
        >
          {PAGE_SIZES.map((s) => (
            <MenuItem key={s} value={s}>{s}</MenuItem>
          ))}
        </FilterSelect>
      </Box>

      <Pagination
        count={totalPages}
        page={filters.page + 1}
        onChange={handlePageChange}
        size="small"
        color="primary"
        aria-label="Pagination"
      />

      <Typography variant="caption" color="text.primary">
        Showing {start}–{end} of {totalElements}
      </Typography>
    </Box>
  )
}
