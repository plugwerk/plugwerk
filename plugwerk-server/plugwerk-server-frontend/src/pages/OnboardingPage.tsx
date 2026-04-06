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
import { Box, Button, Container, Typography } from '@mui/material'
import { Puzzle, Plus } from 'lucide-react'
import { useNavigate } from 'react-router-dom'
import { useAuthStore } from '../stores/authStore'
import { CreateNamespaceDialog } from '../components/admin/CreateNamespaceDialog'
import type { NamespaceSummary } from '../api/generated/model'

export function OnboardingPage() {
  const navigate = useNavigate()
  const setNamespace = useAuthStore((s) => s.setNamespace)
  const [createOpen, setCreateOpen] = useState(false)

  function handleCreated(ns: NamespaceSummary) {
    setNamespace(ns.slug)
    navigate(`/namespaces/${ns.slug}/plugins`)
  }

  return (
    <Box component="main" id="main-content" sx={{ flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
      <Container maxWidth="sm" sx={{ textAlign: 'center', py: 8 }}>
        <Box sx={{ color: 'text.disabled', mb: 3 }}>
          <Puzzle size={64} strokeWidth={1.2} />
        </Box>

        <Typography variant="h1" sx={{ fontSize: '2rem', mb: 2 }}>
          Welcome to Plugwerk
        </Typography>

        <Typography variant="body1" color="text.secondary" sx={{ mb: 4, maxWidth: 420, mx: 'auto' }}>
          No namespaces have been created yet. Create your first namespace to start publishing and managing plugins.
        </Typography>

        <Button
          variant="contained"
          size="large"
          startIcon={<Plus size={18} />}
          onClick={() => setCreateOpen(true)}
        >
          Create Namespace
        </Button>

        <Typography variant="caption" color="text.disabled" sx={{ display: 'block', mt: 3 }}>
          A namespace groups your plugins and controls who can access them.
        </Typography>

        <CreateNamespaceDialog
          open={createOpen}
          onClose={() => setCreateOpen(false)}
          onCreated={handleCreated}
          onError={() => {}}
        />
      </Container>
    </Box>
  )
}
