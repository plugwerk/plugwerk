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
  Typography,
} from '@mui/material'
import { namespacesApi } from '../../api/config'
import type { NamespaceSummary } from '../../api/generated/model'
import { tokens } from '../../theme/tokens'

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
    <Dialog open={!!namespace} onClose={handleClose} maxWidth="xs" fullWidth>
      <DialogTitle sx={{ color: tokens.color.danger }}>Delete Namespace</DialogTitle>
      <DialogContent>
        <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2, pt: 1 }}>
          <Typography variant="body2">
            This will permanently delete all plugins, releases, artifacts, members, and API keys
            in namespace &lsquo;<strong>{slug}</strong>&rsquo;. This action cannot be undone.
          </Typography>
          <TextField
            label={`Type "${slug}" to confirm`}
            value={confirmation}
            onChange={(e) => setConfirmation(e.target.value)}
            size="small"
            autoFocus
          />
        </Box>
      </DialogContent>
      <DialogActions>
        <Button onClick={handleClose}>Cancel</Button>
        <Button
          variant="contained"
          color="error"
          onClick={handleDelete}
          disabled={deleting || !matches}
        >
          {deleting ? 'Deleting\u2026' : 'Delete'}
        </Button>
      </DialogActions>
    </Dialog>
  )
}
