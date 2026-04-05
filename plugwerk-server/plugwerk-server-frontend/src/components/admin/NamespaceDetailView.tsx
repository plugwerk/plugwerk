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
  TextField,
  Switch,
  FormControlLabel,
  Alert,
  CircularProgress,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  Chip,
  Select,
  MenuItem,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  FormControl,
  InputLabel,
} from '@mui/material'
import { ArrowLeft, Plus, Trash2 } from 'lucide-react'
import { axiosInstance, namespaceMembersApi } from '../../api/config'
import { isAxiosError } from 'axios'
import type { NamespaceMemberDto, NamespaceRole } from '../../api/generated/model'
import { NamespaceRole as NamespaceRoleEnum } from '../../api/generated/model'
import { tokens } from '../../theme/tokens'

interface NamespaceDetailViewProps {
  slug: string
  onBack: () => void
  onToast: (toast: { message: string; severity: 'success' | 'error' }) => void
}

const ROLE_COLORS: Record<string, 'primary' | 'default' | 'secondary'> = {
  ADMIN: 'primary',
  MEMBER: 'secondary',
  READ_ONLY: 'default',
}

export function NamespaceDetailView({ slug, onBack, onToast }: NamespaceDetailViewProps) {
  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
      <Box>
        <Button
          size="small"
          startIcon={<ArrowLeft size={14} />}
          onClick={onBack}
          sx={{ mb: 1 }}
        >
          Back to Namespaces
        </Button>
        <Typography variant="h2" gutterBottom>{slug}</Typography>
        <Divider />
      </Box>

      <SettingsSection slug={slug} onToast={onToast} />
      <Divider />
      <MembersSection slug={slug} onToast={onToast} />
      <Divider />
      <ApiKeysSection />
    </Box>
  )
}

function SettingsSection({ slug, onToast }: { slug: string; onToast: NamespaceDetailViewProps['onToast'] }) {
  const [ownerOrg, setOwnerOrg] = useState('')
  const [publicCatalog, setPublicCatalog] = useState(false)
  const [autoApprove, setAutoApprove] = useState(false)
  const [saving, setSaving] = useState(false)

  async function handleSave() {
    setSaving(true)
    try {
      await axiosInstance.patch(`/namespaces/${encodeURIComponent(slug)}`, {
        ownerOrg: ownerOrg.trim() || null,
        settings: {
          publicCatalog,
          autoApprove,
        },
      })
      onToast({ message: 'Namespace settings saved.', severity: 'success' })
    } catch (error: unknown) {
      if (isAxiosError(error) && (error.response?.status === 404 || error.response?.status === 405)) {
        onToast({ message: 'Namespace update is not yet supported by the server.', severity: 'error' })
      } else {
        onToast({ message: 'Failed to save namespace settings.', severity: 'error' })
      }
    } finally {
      setSaving(false)
    }
  }

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
      <Typography variant="h6">Settings</Typography>
      <TextField
        label="Owner Organisation"
        value={ownerOrg}
        onChange={(e) => setOwnerOrg(e.target.value)}
        size="small"
        sx={{ maxWidth: 400 }}
      />
      <FormControlLabel
        control={
          <Switch
            checked={publicCatalog}
            onChange={(e) => setPublicCatalog(e.target.checked)}
            size="small"
          />
        }
        label="Public Catalog"
      />
      <FormControlLabel
        control={
          <Switch
            checked={autoApprove}
            onChange={(e) => setAutoApprove(e.target.checked)}
            size="small"
          />
        }
        label="Auto-Approve Releases"
      />
      <Button
        variant="contained"
        onClick={handleSave}
        disabled={saving}
        sx={{ alignSelf: 'flex-start' }}
      >
        {saving ? 'Saving\u2026' : 'Save'}
      </Button>
    </Box>
  )
}

function MembersSection({ slug, onToast }: { slug: string; onToast: NamespaceDetailViewProps['onToast'] }) {
  const [members, setMembers] = useState<NamespaceMemberDto[]>([])
  const [loading, setLoading] = useState(true)
  const [addOpen, setAddOpen] = useState(false)
  const [newSubject, setNewSubject] = useState('')
  const [newRole, setNewRole] = useState<NamespaceRole>(NamespaceRoleEnum.Member)
  const [addSaving, setAddSaving] = useState(false)

  const loadMembers = useCallback(async () => {
    setLoading(true)
    try {
      const res = await namespaceMembersApi.listNamespaceMembers({ ns: slug })
      setMembers(res.data)
    } catch {
      setMembers([])
    } finally {
      setLoading(false)
    }
  }, [slug])

  useEffect(() => {
    loadMembers()
  }, [loadMembers])

  async function handleRoleChange(member: NamespaceMemberDto, role: NamespaceRole) {
    try {
      const res = await namespaceMembersApi.updateNamespaceMember({
        ns: slug,
        userSubject: member.userSubject,
        namespaceMemberUpdateRequest: { role },
      })
      setMembers((prev) => prev.map((m) => (m.userSubject === member.userSubject ? res.data : m)))
    } catch {
      onToast({ message: `Failed to update role for "${member.userSubject}".`, severity: 'error' })
    }
  }

  async function handleRemove(member: NamespaceMemberDto) {
    try {
      await namespaceMembersApi.removeNamespaceMember({ ns: slug, userSubject: member.userSubject })
      setMembers((prev) => prev.filter((m) => m.userSubject !== member.userSubject))
      onToast({ message: `Member "${member.userSubject}" removed.`, severity: 'success' })
    } catch {
      onToast({ message: `Failed to remove member "${member.userSubject}".`, severity: 'error' })
    }
  }

  async function handleAdd() {
    if (!newSubject.trim()) return
    setAddSaving(true)
    try {
      const res = await namespaceMembersApi.addNamespaceMember({
        ns: slug,
        namespaceMemberCreateRequest: { userSubject: newSubject.trim(), role: newRole },
      })
      setMembers((prev) => [...prev, res.data])
      onToast({ message: `Member "${newSubject.trim()}" added.`, severity: 'success' })
      setAddOpen(false)
      setNewSubject('')
      setNewRole(NamespaceRoleEnum.Member)
    } catch {
      onToast({ message: 'Failed to add member.', severity: 'error' })
    } finally {
      setAddSaving(false)
    }
  }

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
      <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
        <Typography variant="h6">Members</Typography>
        <Button variant="outlined" size="small" startIcon={<Plus size={14} />} onClick={() => setAddOpen(true)}>
          Add Member
        </Button>
      </Box>

      {loading ? (
        <Box sx={{ display: 'flex', justifyContent: 'center', py: 4 }}>
          <CircularProgress size={24} />
        </Box>
      ) : members.length === 0 ? (
        <Typography variant="body2" color="text.secondary">No members found.</Typography>
      ) : (
        <Table size="small" aria-label="Namespace members">
          <TableHead>
            <TableRow>
              <TableCell>Subject</TableCell>
              <TableCell>Role</TableCell>
              <TableCell>Created</TableCell>
              <TableCell />
            </TableRow>
          </TableHead>
          <TableBody>
            {members.map((member) => (
              <TableRow key={member.userSubject}>
                <TableCell>
                  <Typography variant="body2" fontWeight={500}>{member.userSubject}</Typography>
                </TableCell>
                <TableCell>
                  <Select
                    value={member.role}
                    size="small"
                    onChange={(e) => handleRoleChange(member, e.target.value as NamespaceRole)}
                    sx={{ minWidth: 120 }}
                  >
                    {Object.values(NamespaceRoleEnum).map((role) => (
                      <MenuItem key={role} value={role}>
                        <Chip
                          label={role}
                          size="small"
                          color={ROLE_COLORS[role] ?? 'default'}
                          sx={{ cursor: 'pointer' }}
                        />
                      </MenuItem>
                    ))}
                  </Select>
                </TableCell>
                <TableCell>
                  <Typography variant="caption" color="text.disabled">
                    {new Date(member.createdAt).toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' })}
                  </Typography>
                </TableCell>
                <TableCell>
                  <Button
                    size="small"
                    color="error"
                    startIcon={<Trash2 size={14} />}
                    onClick={() => handleRemove(member)}
                  >
                    Remove
                  </Button>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      )}

      <Dialog open={addOpen} onClose={() => setAddOpen(false)} maxWidth="xs" fullWidth>
        <DialogTitle>Add Member</DialogTitle>
        <DialogContent>
          <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2, pt: 1 }}>
            <TextField
              label="Subject"
              value={newSubject}
              onChange={(e) => setNewSubject(e.target.value)}
              required
              size="small"
              autoFocus
              helperText="Username or OIDC subject claim."
            />
            <FormControl size="small" required>
              <InputLabel>Role</InputLabel>
              <Select
                value={newRole}
                label="Role"
                onChange={(e) => setNewRole(e.target.value as NamespaceRole)}
              >
                {Object.values(NamespaceRoleEnum).map((role) => (
                  <MenuItem key={role} value={role}>{role}</MenuItem>
                ))}
              </Select>
            </FormControl>
          </Box>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setAddOpen(false)}>Cancel</Button>
          <Button
            variant="contained"
            onClick={handleAdd}
            disabled={addSaving || !newSubject.trim()}
          >
            {addSaving ? 'Adding\u2026' : 'Add'}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  )
}

function ApiKeysSection() {
  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
      <Typography variant="h6">API Keys</Typography>
      <Alert severity="info">
        API keys provide programmatic access. The key is shown only once after creation.
      </Alert>
      <Alert severity="warning" sx={{ bgcolor: `${tokens.badge.draft.bg}`, color: tokens.badge.draft.text }}>
        API key management coming soon.
      </Alert>
    </Box>
  )
}
