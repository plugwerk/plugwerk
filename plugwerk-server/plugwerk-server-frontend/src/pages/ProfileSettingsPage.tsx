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
import {
  Box,
  Container,
  Typography,
  Divider,
  Select,
  MenuItem,
  FormControl,
  InputLabel,
  TextField,
  Button,
  Alert,
} from '@mui/material'
import { useAuthStore } from '../stores/authStore'

export function ProfileSettingsPage() {
  const { apiKey, namespace } = useAuthStore()

  return (
    <Box component="main" id="main-content" sx={{ flex: 1, py: 4 }}>
      <Container maxWidth="sm">
        <Typography variant="h1" gutterBottom>Profile Settings</Typography>
        <Divider sx={{ mb: 4 }} />

        <Box sx={{ display: 'flex', flexDirection: 'column', gap: 3 }}>

          {/* Language preference */}
          <Box>
            <Typography variant="h4" gutterBottom>Language</Typography>
            <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
              Your preferred UI language. Overrides the system default set by the administrator.
            </Typography>
            <FormControl size="small" sx={{ minWidth: 200 }}>
              <InputLabel>Language</InputLabel>
              <Select defaultValue="en" label="Language">
                <MenuItem value="en">English</MenuItem>
                <MenuItem value="de">Deutsch</MenuItem>
              </Select>
            </FormControl>
          </Box>

          <Divider />

          {/* Active namespace */}
          <Box>
            <Typography variant="h4" gutterBottom>Active Namespace</Typography>
            <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
              The namespace used for all catalog and upload operations.
            </Typography>
            <TextField
              size="small"
              label="Namespace"
              defaultValue={namespace}
              sx={{ minWidth: 200 }}
            />
          </Box>

          <Divider />

          {/* API Key */}
          <Box>
            <Typography variant="h4" gutterBottom>API Key</Typography>
            <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
              Your personal API key for authenticating CLI and CI/CD requests.
            </Typography>
            {apiKey ? (
              <TextField
                size="small"
                label="API Key"
                type="password"
                value={apiKey}
                InputProps={{ readOnly: true }}
                sx={{ minWidth: 320 }}
              />
            ) : (
              <Alert severity="info">No API key set. Log in to configure one.</Alert>
            )}
          </Box>

          <Box>
            <Button variant="contained" sx={{ alignSelf: 'flex-start' }}>Save Changes</Button>
          </Box>
        </Box>
      </Container>
    </Box>
  )
}
