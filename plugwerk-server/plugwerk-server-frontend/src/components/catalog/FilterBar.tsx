// SPDX-License-Identifier: AGPL-3.0
// Copyright (C) 2026 devtank42 GmbH
import { useState } from 'react'
import { Box, Button, InputBase, MenuItem, ToggleButton, ToggleButtonGroup, useTheme, alpha } from '@mui/material'
import { LayoutGrid, List, Search } from 'lucide-react'
import { usePluginStore } from '../../stores/pluginStore'
import { useUiStore } from '../../stores/uiStore'
import { FilterSelect } from '../common/FilterSelect'
import { tokens } from '../../theme/tokens'

interface FilterBarProps {
  view: 'card' | 'list'
  onViewChange: (view: 'card' | 'list') => void
  namespace: string
}

const CATEGORIES = ['Reporting', 'Export', 'Integration', 'Security', 'UI Extensions', 'Data Processing']
const TAGS = ['pdf', 'excel', 'oauth', 'charts', 'api']
const SORT_OPTIONS = [
  { value: 'name,asc',           label: 'Name A–Z' },
  { value: 'name,desc',          label: 'Name Z–A' },
  { value: 'downloadCount,desc', label: 'Most Downloads' },
  { value: 'updatedAt,desc',     label: 'Newest' },
]
const COMPATIBILITY_OPTIONS = [
  { value: '',        label: 'Any Version' },
  { value: '>=3.0.0', label: '≥ 3.0.0' },
  { value: '>=2.5.0', label: '≥ 2.5.0' },
  { value: '>=2.0.0', label: '≥ 2.0.0' },
]

export function FilterBar({ view, onViewChange, namespace }: FilterBarProps) {
  const theme = useTheme()
  const isDark = theme.palette.mode === 'dark'
  const { filters, setFilters, fetchPlugins } = usePluginStore()
  const { searchQuery, setSearchQuery } = useUiStore()
  const [compatibility, setCompatibility] = useState('')
  const hasActiveFilters = !!(filters.category || filters.tag || filters.status || compatibility)

  function handleChange(key: string, value: string) {
    setFilters({ [key]: value, page: 0 })
    fetchPlugins(namespace)
  }

  function handleReset() {
    setFilters({ category: '', tag: '', status: '', sort: 'name,asc', page: 0 })
    setCompatibility('')
    fetchPlugins(namespace)
  }

  return (
    <Box
      role="group"
      aria-label="Filter and sort options"
      sx={{
        display: 'flex',
        alignItems: 'center',
        flexWrap: 'wrap',
        gap: 1,
        py: 2,
        borderBottom: '1px solid',
        borderColor: 'divider',
        mb: 3,
      }}
    >
      <FilterSelect
        value={filters.category}
        onChange={(v) => handleChange('category', v)}
        aria-label="Filter by category"
        minWidth={160}
      >
        <MenuItem value="">All Categories</MenuItem>
        {CATEGORIES.map((c) => (
          <MenuItem key={c} value={c}>{c}</MenuItem>
        ))}
      </FilterSelect>

      <FilterSelect
        value={filters.tag}
        onChange={(v) => handleChange('tag', v)}
        aria-label="Filter by tag"
        minWidth={140}
      >
        <MenuItem value="">All Tags</MenuItem>
        {TAGS.map((t) => (
          <MenuItem key={t} value={t}>{t}</MenuItem>
        ))}
      </FilterSelect>

      <FilterSelect
        value={filters.status}
        onChange={(v) => handleChange('status', v)}
        aria-label="Filter by status"
        minWidth={140}
      >
        <MenuItem value="">Active</MenuItem>
        <MenuItem value="archived">Archived</MenuItem>
      </FilterSelect>

      <FilterSelect
        value={compatibility}
        onChange={setCompatibility}
        aria-label="Filter by compatibility"
        minWidth={140}
      >
        {COMPATIBILITY_OPTIONS.map((o) => (
          <MenuItem key={o.value} value={o.value}>{o.label}</MenuItem>
        ))}
      </FilterSelect>

      <FilterSelect
        value={filters.sort}
        onChange={(v) => handleChange('sort', v)}
        aria-label="Sort order"
        minWidth={140}
      >
        {SORT_OPTIONS.map((o) => (
          <MenuItem key={o.value} value={o.value}>{o.label}</MenuItem>
        ))}
      </FilterSelect>

      {hasActiveFilters && (
        <Button variant="text" size="small" onClick={handleReset} sx={{ color: 'text.secondary' }}>
          Reset filters
        </Button>
      )}

      {/* Search — fills available space */}
      <Box role="search" sx={{ flex: 1, minWidth: 0, position: 'relative' }}>
        <Box
          sx={{
            position: 'absolute',
            left: 10,
            top: '50%',
            transform: 'translateY(-50%)',
            color: 'text.disabled',
            display: 'flex',
            pointerEvents: 'none',
          }}
          aria-hidden="true"
        >
          <Search size={14} />
        </Box>
        <InputBase
          value={searchQuery}
          onChange={(e) => setSearchQuery(e.target.value)}
          placeholder="Search plugins…"
          aria-label="Search plugins"
          sx={{
            width: '100%',
            pl: '30px',
            pr: 1,
            py: 0.5,
            borderRadius: tokens.radius.btn,
            border: `1px solid ${isDark ? '#393939' : tokens.color.gray20}`,
            background: isDark ? '#1c1c1c' : tokens.color.gray10,
            fontSize: '0.8125rem',
            '&:focus-within': {
              borderColor: tokens.color.primary,
              background: isDark ? '#262626' : tokens.color.white,
              outline: `2px solid ${alpha(tokens.color.primary, 0.25)}`,
              outlineOffset: 0,
            },
          }}
        />
      </Box>

      <ToggleButtonGroup
        value={view}
        exclusive
        onChange={(_, v) => v && onViewChange(v)}
        aria-label="Switch view"
        size="small"
      >
        <ToggleButton value="card" aria-label="Card view">
          <LayoutGrid size={16} />
        </ToggleButton>
        <ToggleButton value="list" aria-label="List view">
          <List size={16} />
        </ToggleButton>
      </ToggleButtonGroup>
    </Box>
  )
}
