// SPDX-License-Identifier: AGPL-3.0
// Copyright (C) 2026 devtank42 GmbH
import { useEffect, useState } from 'react'
import { Box, Container, Typography, Alert, MenuItem } from '@mui/material'
import { FilterBar } from '../components/catalog/FilterBar'
import { PluginCard } from '../components/catalog/PluginCard'
import { PluginListRow } from '../components/catalog/PluginListRow'
import { PluginCardSkeleton } from '../components/catalog/PluginCardSkeleton'
import { PaginationBar } from '../components/catalog/PaginationBar'
import { EmptyState } from '../components/common/EmptyState'
import { usePluginStore } from '../stores/pluginStore'
import { useAuthStore } from '../stores/authStore'
import { useUiStore } from '../stores/uiStore'
import { useNamespaceStore } from '../stores/namespaceStore'
import { FilterSelect } from '../components/common/FilterSelect'

export function CatalogPage() {
  const { namespace, setNamespace } = useAuthStore()
  const { plugins, loading, error, totalElements, resetFilters, fetchPlugins } = usePluginStore()
  const { searchQuery } = useUiStore()
  const { namespaces, fetchNamespaces } = useNamespaceStore()
  const [view, setView] = useState<'card' | 'list'>('card')

  useEffect(() => {
    fetchNamespaces()
  }, [])

  useEffect(() => {
    fetchPlugins(namespace)
  }, [namespace])

  useEffect(() => {
    const store = usePluginStore.getState()
    store.setFilters({ search: searchQuery, page: 0 })
    fetchPlugins(namespace)
  }, [searchQuery, namespace])

  return (
    <Box component="main" id="main-content" sx={{ flex: 1, py: 4 }}>
      <Container maxWidth="xl">
        {/* Page header */}
        <Box
          sx={{
            display: 'flex',
            alignItems: 'flex-end',
            justifyContent: 'space-between',
            flexWrap: 'wrap',
            gap: 2,
            mb: 1,
          }}
        >
          <Box>
            <Typography variant="h1" gutterBottom>Plugin Catalog</Typography>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
              <Typography variant="caption" color="text.disabled" component="label" htmlFor="ns-select">
                Namespace:
              </Typography>
              <FilterSelect
                id="ns-select"
                value={namespace}
                onChange={(v) => { setNamespace(v); fetchPlugins(v) }}
                aria-label="Select namespace"
                minWidth={160}
              >
                {namespaces.length > 0
                  ? namespaces.map((ns) => (
                      <MenuItem key={ns.slug} value={ns.slug}>{ns.slug}</MenuItem>
                    ))
                  : <MenuItem value={namespace}>{namespace}</MenuItem>
                }
              </FilterSelect>
            </Box>
          </Box>
          <Typography
            variant="caption"
            color="text.disabled"
            aria-live="polite"
          >
            {!loading && `${totalElements} plugins`}
          </Typography>
        </Box>

        {/* Filters */}
        <FilterBar view={view} onViewChange={setView} namespace={namespace} />

        {/* Error */}
        {error && (
          <Alert severity="error" sx={{ mb: 3 }}>
            {error}
          </Alert>
        )}

        {/* Loading skeleton */}
        {loading && (
          <Box
            aria-label="Loading plugins"
            aria-busy="true"
            sx={{
              display: 'grid',
              gridTemplateColumns: 'repeat(auto-fill, minmax(300px, 1fr))',
              gap: 2.5,
            }}
          >
            {Array.from({ length: 6 }).map((_, i) => (
              <PluginCardSkeleton key={i} />
            ))}
          </Box>
        )}

        {/* Empty state */}
        {!loading && !error && plugins.length === 0 && (
          <EmptyState
            title="No plugins found"
            message="Try different search terms or reset your filters to see all available plugins."
            actionLabel="Show all plugins"
            onAction={resetFilters}
          />
        )}

        {/* Card view */}
        {!loading && !error && plugins.length > 0 && view === 'card' && (
          <Box
            role="list"
            aria-label="Plugin cards"
            sx={{
              display: 'grid',
              gridTemplateColumns: 'repeat(auto-fill, minmax(300px, 1fr))',
              gap: 2.5,
            }}
          >
            {plugins.map((plugin) => (
              <PluginCard key={plugin.id} plugin={plugin} namespace={namespace} />
            ))}
          </Box>
        )}

        {/* List view */}
        {!loading && !error && plugins.length > 0 && view === 'list' && (
          <Box
            role="list"
            aria-label="Plugin list"
            sx={{ display: 'flex', flexDirection: 'column', gap: 1 }}
          >
            {plugins.map((plugin) => (
              <PluginListRow key={plugin.id} plugin={plugin} namespace={namespace} />
            ))}
          </Box>
        )}

        {/* Pagination */}
        {!loading && !error && plugins.length > 0 && (
          <PaginationBar namespace={namespace} />
        )}
      </Container>
    </Box>
  )
}
