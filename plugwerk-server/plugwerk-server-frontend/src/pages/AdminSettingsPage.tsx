// SPDX-License-Identifier: AGPL-3.0
// Copyright (C) 2026 devtank42 GmbH
import { useState } from 'react'
import {
  Box,
  Container,
  Typography,
  TextField,
  Button,
  Divider,
  Alert,
  Select,
  MenuItem,
  FormControl,
  InputLabel,
} from '@mui/material'
import { AdminSidebar } from '../components/admin/AdminSidebar'

function GeneralSection() {
  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 3 }}>
      <Box>
        <Typography variant="h2" gutterBottom>General Settings</Typography>
        <Divider sx={{ mb: 3 }} />
      </Box>
      <TextField label="Instance Name" defaultValue="ACME Corp Plugin Hub" size="small" />
      <TextField label="Default Namespace" defaultValue="default" size="small" />
      <TextField label="Max Upload Size (MB)" type="number" defaultValue={50} size="small" />
      <FormControl size="small" sx={{ minWidth: 200 }}>
        <InputLabel>Default Language</InputLabel>
        <Select defaultValue="en" label="Default Language">
          <MenuItem value="en">English</MenuItem>
          <MenuItem value="de">Deutsch</MenuItem>
        </Select>
      </FormControl>
      <Button variant="contained" sx={{ alignSelf: 'flex-start' }}>Save Changes</Button>
    </Box>
  )
}

function ApiKeysSection() {
  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 3 }}>
      <Box>
        <Typography variant="h2" gutterBottom>API Keys</Typography>
        <Divider sx={{ mb: 3 }} />
      </Box>
      <Alert severity="info">
        API keys are used to authenticate requests from the CLI and CI/CD pipelines.
      </Alert>
      <Button variant="outlined" sx={{ alignSelf: 'flex-start' }}>Generate New API Key</Button>
    </Box>
  )
}

function UsersSection() {
  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 3 }}>
      <Box>
        <Typography variant="h2" gutterBottom>Users</Typography>
        <Divider sx={{ mb: 3 }} />
      </Box>
      <Typography variant="body2" color="text.secondary">
        User management will be available in Phase 2 (RBAC/OIDC).
      </Typography>
    </Box>
  )
}

function ReviewsSection() {
  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 3 }}>
      <Box>
        <Typography variant="h2" gutterBottom>Pending Reviews</Typography>
        <Divider sx={{ mb: 3 }} />
      </Box>
      <Typography variant="body2" color="text.secondary">
        Releases awaiting review will appear here.
      </Typography>
    </Box>
  )
}

function DangerSection() {
  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 3 }}>
      <Box>
        <Typography variant="h2" gutterBottom color="error">Danger Zone</Typography>
        <Divider sx={{ mb: 3 }} />
      </Box>
      <Alert severity="warning">
        Actions in this section are irreversible. Proceed with caution.
      </Alert>
      <Box sx={{ border: '1px solid', borderColor: 'error.main', borderRadius: 1, p: 2 }}>
        <Typography variant="body2" fontWeight={600} gutterBottom>Reset namespace</Typography>
        <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 1.5 }}>
          Delete all plugins and releases in the default namespace. This cannot be undone.
        </Typography>
        <Button variant="outlined" color="error" size="small">Reset namespace</Button>
      </Box>
    </Box>
  )
}

const sectionMap: Record<string, React.ReactNode> = {
  general: <GeneralSection />,
  'api-keys': <ApiKeysSection />,
  users: <UsersSection />,
  reviews: <ReviewsSection />,
  danger: <DangerSection />,
}

export function AdminSettingsPage() {
  const [activeSection, setActiveSection] = useState('general')

  return (
    <Box component="main" id="main-content" sx={{ flex: 1, display: 'flex' }}>
      <AdminSidebar activeSection={activeSection} onSelect={setActiveSection} />
      <Box sx={{ flex: 1, overflow: 'auto' }}>
        <Container maxWidth="md" sx={{ py: 4, maxWidth: 800 }}>
          {sectionMap[activeSection]}
        </Container>
      </Box>
    </Box>
  )
}
