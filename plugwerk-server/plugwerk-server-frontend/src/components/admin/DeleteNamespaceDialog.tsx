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
import { TextField } from '@mui/material'
import { namespacesApi } from '../../api/config'
import type { NamespaceSummary } from '../../api/generated/model'
import { AppDialog } from '../common/AppDialog'

interface DeleteNamespaceDialogProps {
  namespace: NamespaceSummary | null
  onClose: () => void
  onDeleted: (slug: string) => void
  onError: (message: string) => void
}

export function DeleteNamespaceDialog({ namespace, onClose, onDeleted, onError }: DeleteNamespaceDialogProps) {
  const [confirmation, setConfirmation] = useState('')
  const [deleting, setDeleting] = useState(false)

  const slug = namespace?.slug ?? ''
  const matches = confirmation === slug

  function handleClose() {
    setConfirmation('')
    onClose()
  }

  async function handleDelete() {
    if (!matches) return
    setDeleting(true)
    try {
      await namespacesApi.deleteNamespace({ ns: slug })
      onDeleted(slug)
      handleClose()
    } catch {
      onError(`Failed to delete namespace "${slug}".`)
    } finally {
      setDeleting(false)
    }
  }

  return (
    <AppDialog
      open={!!namespace}
      onClose={handleClose}
      title="Delete Namespace"
      description={`This will permanently delete all plugins, releases, artifacts, members, and API keys in namespace \u201c${slug}\u201d. This action cannot be undone.`}
      actionLabel="Delete Namespace"
      onAction={handleDelete}
      actionColor="error"
      actionDisabled={!matches}
      actionLoading={deleting}
    >
      <TextField
        label={`Type "${slug}" to confirm`}
        value={confirmation}
        onChange={(e) => setConfirmation(e.target.value)}
        size="small"
        autoFocus
        fullWidth
      />
    </AppDialog>
  )
}
