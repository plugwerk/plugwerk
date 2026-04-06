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
import { useState, useEffect, useCallback } from 'react'
import {
  Box,
  Typography,
  Button,
  Divider,
  Snackbar,
  Alert,
  CircularProgress,
} from '@mui/material'
import { Pencil, Plus, Trash2 } from 'lucide-react'
import { DataTable } from '../common/DataTable'
import type { DataColumn } from '../common/DataTable'
import { namespacesApi } from '../../api/config'
import type { NamespaceSummary } from '../../api/generated/model'
import { CreateNamespaceDialog } from './CreateNamespaceDialog'
import { DeleteNamespaceDialog } from './DeleteNamespaceDialog'
import { NamespaceDetailView } from './NamespaceDetailView'

export function NamespacesSection() {
  const [namespaces, setNamespaces] = useState<NamespaceSummary[]>([])
  const [loading, setLoading] = useState(true)
  const [createOpen, setCreateOpen] = useState(false)
  const [deleteTarget, setDeleteTarget] = useState<NamespaceSummary | null>(null)
  const [editingSlug, setEditingSlug] = useState<string | null>(null)
  const [toast, setToast] = useState<{ message: string; severity: 'success' | 'error' } | null>(null)

  const loadNamespaces = useCallback(async () => {
    setLoading(true)
    try {
      const res = await namespacesApi.listNamespaces()
      setNamespaces(res.data)
    } catch {
      setNamespaces([])
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    loadNamespaces()
  }, [loadNamespaces])

  function handleCreated(ns: NamespaceSummary) {
    setNamespaces((prev) => [...prev, ns])
    setToast({ message: `Namespace "${ns.slug}" created.`, severity: 'success' })
  }

  function handleDeleted(slug: string) {
    setNamespaces((prev) => prev.filter((n) => n.slug !== slug))
    setDeleteTarget(null)
    setToast({ message: `Namespace "${slug}" deleted.`, severity: 'success' })
  }

  const namespaceCols: DataColumn<NamespaceSummary>[] = [
    {
      key: 'slug',
      header: 'Slug',
      render: (ns) => <Typography variant="body2" fontWeight={500}>{ns.slug}</Typography>,
    },
    {
      key: 'owner',
      header: 'Owner',
      render: (ns) => <Typography variant="caption" color="text.secondary">{ns.ownerOrg || '\u2014'}</Typography>,
    },
    {
      key: 'actions',
      header: '',
      align: 'right',
      render: (ns) => (
        <Box sx={{ display: 'flex', gap: 1, justifyContent: 'flex-end' }}>
          <Button size="small" startIcon={<Pencil size={14} />} onClick={() => setEditingSlug(ns.slug)}>
            Edit
          </Button>
          <Button size="small" color="error" startIcon={<Trash2 size={14} />} onClick={() => setDeleteTarget(ns)}>
            Delete
          </Button>
        </Box>
      ),
    },
  ]

  if (editingSlug) {
    return (
      <NamespaceDetailView
        slug={editingSlug}
        onBack={() => setEditingSlug(null)}
        onToast={setToast}
      />
    )
  }

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 3 }}>
      <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
        <Box>
          <Typography variant="h2" gutterBottom>Namespaces</Typography>
          <Divider sx={{ mb: 3 }} />
        </Box>
        <Button variant="outlined" size="small" startIcon={<Plus size={14} />} onClick={() => setCreateOpen(true)}>
          Create Namespace
        </Button>
      </Box>

      {loading ? (
        <Box sx={{ display: 'flex', justifyContent: 'center', py: 4 }}>
          <CircularProgress size={24} />
        </Box>
      ) : namespaces.length === 0 ? (
        <Typography variant="body2" color="text.secondary">No namespaces found.</Typography>
      ) : (
        <DataTable<NamespaceSummary>
          ariaLabel="Namespaces"
          rows={namespaces}
          keyFn={(ns) => ns.slug}
          columns={namespaceCols}
        />
      )}

      <CreateNamespaceDialog
        open={createOpen}
        onClose={() => setCreateOpen(false)}
        onCreated={handleCreated}
        onError={(msg) => setToast({ message: msg, severity: 'error' })}
      />

      <DeleteNamespaceDialog
        namespace={deleteTarget}
        onClose={() => setDeleteTarget(null)}
        onDeleted={handleDeleted}
        onError={(msg) => setToast({ message: msg, severity: 'error' })}
      />

      <Snackbar open={!!toast} autoHideDuration={4000} onClose={() => setToast(null)} anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}>
        <Alert severity={toast?.severity} onClose={() => setToast(null)} sx={{ width: '100%' }}>
          {toast?.message}
        </Alert>
      </Snackbar>
    </Box>
  )
}
