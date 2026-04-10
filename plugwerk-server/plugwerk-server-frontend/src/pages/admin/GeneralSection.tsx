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
  Typography,
  TextField,
  Button,
  Divider,
  Select,
  MenuItem,
  FormControl,
  InputLabel,
} from '@mui/material'
import { useUiStore } from '../../stores/uiStore'

export function GeneralSection() {
  const addToast = useUiStore((s) => s.addToast)

  function handleSave() {
    // TODO: persist settings via API once backend endpoint exists
    addToast({ message: 'Settings saved.', type: 'success' })
  }

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 3 }}>
      <Box>
        <Typography variant="h2" gutterBottom>General Settings</Typography>
        <Divider sx={{ mb: 3 }} />
      </Box>
      <TextField label="Max Upload Size (MB)" type="number" defaultValue={50} size="small" />
      <FormControl size="small" sx={{ minWidth: 200 }}>
        <InputLabel>Default Language</InputLabel>
        <Select defaultValue="en" label="Default Language">
          <MenuItem value="en">English</MenuItem>
          <MenuItem value="de">Deutsch</MenuItem>
        </Select>
      </FormControl>
      <Button variant="contained" sx={{ alignSelf: 'flex-start' }} onClick={handleSave}>
        Save Changes
      </Button>
    </Box>
  )
}
