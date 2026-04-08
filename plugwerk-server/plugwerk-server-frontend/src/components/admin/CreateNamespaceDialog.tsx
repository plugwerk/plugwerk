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
import { Box, TextField } from '@mui/material'
import { namespacesApi } from '../../api/config'
import type { NamespaceSummary } from '../../api/generated/model'
import { isAxiosError } from 'axios'
import { AppDialog } from '../common/AppDialog'

const SLUG_PATTERN = /^[a-z0-9][a-z0-9-]{0,62}[a-z0-9]$/

interface CreateNamespaceDialogProps {
  open: boolean
  onClose: () => void
  onCreated: (ns: NamespaceSummary) => void
  onError: (message: string) => void
}

export function CreateNamespaceDialog({ open, onClose, onCreated, onError }: CreateNamespaceDialogProps) {
  const [slug, setSlug] = useState('')
  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
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
    setName('')
    setDescription('')
    setSlugError(null)
    onClose()
  }

  async function handleCreate() {
    if (!slug.trim() || !SLUG_PATTERN.test(slug) || !name.trim()) return
    setSaving(true)
    try {
      const res = await namespacesApi.createNamespace({
        namespaceCreateRequest: {
          slug: slug.trim(),
          name: name.trim(),
          description: description.trim() || undefined,
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
    <AppDialog
      open={open}
      onClose={handleClose}
      title="Create Namespace"
      description="A namespace groups plugins, members, and API keys under a shared scope."
      actionLabel="Create"
      onAction={handleCreate}
      actionDisabled={!slug.trim() || !name.trim() || !!slugError}
      actionLoading={saving}
    >
      <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
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
          label="Name"
          value={name}
          onChange={(e) => setName(e.target.value)}
          required
          size="small"
          helperText="Human-readable display name for this namespace."
        />
        <TextField
          label="Description"
          value={description}
          onChange={(e) => setDescription(e.target.value)}
          size="small"
          multiline
          minRows={2}
        />
      </Box>
    </AppDialog>
  )
}
