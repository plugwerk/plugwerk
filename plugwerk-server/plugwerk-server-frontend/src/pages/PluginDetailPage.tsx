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
import { useCallback, useEffect, useState } from 'react'
import {
  Box,
  Container,
  Tabs,
  Tab,
  Typography,
  Alert,
  CircularProgress,
  Link as MuiLink,
  Snackbar,
} from '@mui/material'
import { ChevronRight } from 'lucide-react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { PluginHeader } from '../components/plugin-detail/PluginHeader'
import { OverviewTab } from '../components/plugin-detail/OverviewTab'
import { VersionsTab } from '../components/plugin-detail/VersionsTab'
import { ChangelogTab } from '../components/plugin-detail/ChangelogTab'
import { DependenciesTab } from '../components/plugin-detail/DependenciesTab'
import { catalogApi, managementApi } from '../api/config'
import type { PluginDto, PluginReleaseDto } from '../api/generated/model'
import { ConfirmDeleteDialog } from '../components/common/ConfirmDeleteDialog'
import { useAuthStore } from '../stores/authStore'

const TAB_IDS = ['overview', 'versions', 'changelog', 'dependencies']

export function PluginDetailPage() {
  const { namespace = '', pluginId = '' } = useParams<{ namespace: string; pluginId: string }>()
  const navigate = useNavigate()
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated)
  const namespaceRole = useAuthStore((s) => s.namespaceRole)
  const fetchNamespaceRole = useAuthStore((s) => s.fetchNamespaceRole)
  const isAdmin = namespaceRole === 'ADMIN'
  const [plugin, setPlugin] = useState<PluginDto | null>(null)
  const [releases, setReleases] = useState<PluginReleaseDto[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [tab, setTab] = useState(0)
  const [showDeletePlugin, setShowDeletePlugin] = useState(false)
  const [isDeletingPlugin, setIsDeletingPlugin] = useState(false)
  const [toast, setToast] = useState<{ message: string; severity: 'success' | 'error' } | null>(null)

  useEffect(() => {
    async function load() {
      setLoading(true)
      setError(null)
      try {
        const [pluginRes, releasesRes] = await Promise.all([
          catalogApi.getPlugin({ ns: namespace, pluginId }),
          catalogApi.listReleases({ ns: namespace, pluginId }),
        ])
        setPlugin(pluginRes.data)
        setReleases(releasesRes.data.content)
      } catch {
        setError('Failed to load plugin details.')
      } finally {
        setLoading(false)
      }
    }
    if (pluginId) load()
  }, [namespace, pluginId])

  useEffect(() => {
    if (isAuthenticated) fetchNamespaceRole(namespace)
  }, [isAuthenticated, namespace, fetchNamespaceRole])

  const handleReleaseDeleted = useCallback((version: string) => {
    setReleases((prev) => prev.filter((r) => r.version !== version))
  }, [])

  async function handleDeletePlugin() {
    setIsDeletingPlugin(true)
    try {
      await managementApi.deletePlugin({ ns: namespace, pluginId })
      setToast({ message: `Plugin ${pluginId} deleted.`, severity: 'success' })
      setTimeout(() => navigate(`/namespaces/${namespace}/plugins`), 1000)
    } catch {
      setToast({ message: `Failed to delete plugin ${pluginId}.`, severity: 'error' })
    } finally {
      setIsDeletingPlugin(false)
      setShowDeletePlugin(false)
    }
  }

  const latestRelease = releases.find((r) => r.status === 'published') ?? releases[0] ?? null
  const draftCount = releases.filter((r) => r.status === 'draft').length

  if (loading) {
    return (
      <Box component="main" id="main-content" sx={{ flex: 1, display: 'flex', justifyContent: 'center', alignItems: 'center', py: 8 }}>
        <CircularProgress />
      </Box>
    )
  }

  if (error || !plugin) {
    return (
      <Box component="main" id="main-content" sx={{ flex: 1, py: 4 }}>
        <Container maxWidth="lg">
          <Alert severity="error">{error ?? 'Plugin not found.'}</Alert>
        </Container>
      </Box>
    )
  }

  return (
    <Box component="main" id="main-content" sx={{ flex: 1, py: 4 }}>
      <Container maxWidth="lg">
        {/* Breadcrumb */}
        <Box
          component="nav"
          aria-label="Breadcrumb"
          sx={{ display: 'flex', alignItems: 'center', gap: 0.5, mb: 2 }}
        >
          <MuiLink component={Link} to="/" sx={{ fontSize: '0.875rem', color: 'text.secondary', '&:hover': { color: 'primary.main' } }}>
            Catalog
          </MuiLink>
          <ChevronRight size={14} style={{ color: 'var(--mui-palette-text-disabled)' }} aria-hidden="true" />
          <Typography variant="body2" color="text.primary" aria-current="page">
            {plugin.name}
          </Typography>
        </Box>

        {/* Plugin Header */}
        <PluginHeader
          plugin={plugin}
          latestRelease={latestRelease}
          namespace={namespace}
          isAdmin={isAdmin}
          onDeletePlugin={() => setShowDeletePlugin(true)}
          onError={(message) => setToast({ message, severity: 'error' })}
          onPluginUpdated={(updated) => {
            setPlugin(updated)
            setToast({ message: `Plugin status changed to ${updated.status}.`, severity: 'success' })
          }}
        />

        {/* Tabs + Content */}
        <Box sx={{ mt: 2 }}>
          <Tabs
            value={tab}
            onChange={(_, v) => setTab(v)}
            aria-label="Plugin information tabs"
            sx={{ borderBottom: 1, borderColor: 'divider', mb: 3 }}
          >
            <Tab label="Overview"      id="tab-overview"     aria-controls="panel-overview" />
            <Tab
              label={
                <Box sx={{ display: 'flex', flexDirection: 'row', alignItems: 'flex-start', gap: 0.5 }}>
                  <span>Versions</span>
                  {draftCount > 0 && (
                    <Box
                      component="span"
                      sx={{
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        minWidth: 16,
                        height: 16,
                        px: 0.5,
                        mt: '-2px',
                        borderRadius: '8px',
                        fontSize: '0.625rem',
                        fontWeight: 700,
                        lineHeight: 1,
                        bgcolor: 'warning.main',
                        color: '#161616',
                      }}
                    >
                      {draftCount}
                    </Box>
                  )}
                </Box>
              }
              id="tab-versions"
              aria-controls="panel-versions"
            />
            <Tab label="Changelog"     id="tab-changelog"    aria-controls="panel-changelog" />
            <Tab label="Dependencies"  id="tab-dependencies" aria-controls="panel-dependencies" />
          </Tabs>

          {TAB_IDS.map((id, i) => (
            <Box
              key={id}
              role="tabpanel"
              id={`panel-${id}`}
              aria-labelledby={`tab-${id}`}
              hidden={tab !== i}
            >
              {tab === 0 && i === 0 && <OverviewTab plugin={plugin} />}
              {tab === 1 && i === 1 && (
                <VersionsTab
                  releases={releases}
                  namespace={namespace}
                  pluginId={pluginId}
                  canApprove={isAdmin}
                  onReleaseDeleted={handleReleaseDeleted}
                  onReleasesChanged={setReleases}
                />
              )}
              {tab === 2 && i === 2 && <ChangelogTab releases={releases} />}
              {tab === 3 && i === 3 && <DependenciesTab release={latestRelease} namespace={namespace} />}
            </Box>
          ))}
        </Box>
      </Container>

      <ConfirmDeleteDialog
        open={showDeletePlugin}
        title="Delete Plugin"
        message={`Are you sure you want to delete "${plugin.name}" and all its releases? This action cannot be undone.`}
        onConfirm={handleDeletePlugin}
        onCancel={() => setShowDeletePlugin(false)}
        loading={isDeletingPlugin}
      />

      <Snackbar
        open={!!toast}
        autoHideDuration={4000}
        onClose={() => setToast(null)}
        anchorOrigin={{ vertical: 'top', horizontal: 'center' }}
      >
        <Alert severity={toast?.severity} onClose={() => setToast(null)} sx={{ width: '100%' }}>
          {toast?.message}
        </Alert>
      </Snackbar>
    </Box>
  )
}
