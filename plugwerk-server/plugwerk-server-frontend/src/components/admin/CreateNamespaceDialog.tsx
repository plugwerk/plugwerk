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
import { useState } from 'react'
import {
  Box,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  TextField,
} from '@mui/material'
import { namespacesApi } from '../../api/config'
import type { NamespaceSummary } from '../../api/generated/model'
import { isAxiosError } from 'axios'

const SLUG_PATTERN = /^[a-z0-9][a-z0-9-]{0,62}[a-z0-9]$/

interface CreateNamespaceDialogProps {
  open: boolean
  onClose: () => void
  onCreated: (ns: NamespaceSummary) => void
  onError: (message: string) => void
}

export function CreateNamespaceDialog({ open, onClose, onCreated, onError }: CreateNamespaceDialogProps) {
  const [slug, setSlug] = useState('')
  const [ownerOrg, setOwnerOrg] = useState('')
  const [saving, setSaving] = useState(false)
  const [slugError, setSlugError] = useState<string | null>(null)

  function handleSlugChange(value: string) {
    setSlug(value)
    if (value && !SLUG_PATTERN.test(value)) {
      setSlugError('Must be lowercase alphanumeric with hyphens, 2\u201364 characters.')
    } else {
      setSlugError(null)
    }
  }

  function handleClose() {
    setSlug('')
    setOwnerOrg('')
    setSlugError(null)
    onClose()
  }

  async function handleCreate() {
    if (!slug.trim() || !SLUG_PATTERN.test(slug)) return
    setSaving(true)
    try {
      const res = await namespacesApi.createNamespace({
        namespaceCreateRequest: {
          slug: slug.trim(),
          ownerOrg: ownerOrg.trim() || undefined,
        },
      })
      onCreated(res.data)
      handleClose()
    } catch (error: unknown) {
      if (isAxiosError(error) && error.response?.status === 409) {
        setSlugError('Namespace already exists.')
      } else {
        onError('Failed to create namespace.')
      }
    } finally {
      setSaving(false)
    }
  }

  return (
    <Dialog open={open} onClose={handleClose} maxWidth="xs" fullWidth>
      <DialogTitle>Create Namespace</DialogTitle>
      <DialogContent>
        <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2, pt: 1 }}>
          <TextField
            label="Slug"
            value={slug}
            onChange={(e) => handleSlugChange(e.target.value)}
            required
            size="small"
            autoFocus
            error={!!slugError}
            helperText={slugError ?? 'Lowercase alphanumeric with hyphens, 2\u201364 characters.'}
          />
          <TextField
            label="Owner Organisation"
            value={ownerOrg}
            onChange={(e) => setOwnerOrg(e.target.value)}
            size="small"
          />
        </Box>
      </DialogContent>
      <DialogActions>
        <Button onClick={handleClose}>Cancel</Button>
        <Button
          variant="contained"
          onClick={handleCreate}
          disabled={saving || !slug.trim() || !!slugError}
        >
          {saving ? 'Creating\u2026' : 'Create'}
        </Button>
      </DialogActions>
    </Dialog>
  )
}
