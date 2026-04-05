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
  Tabs,
  Tab,
  Autocomplete,
} from '@mui/material'
import { ArrowLeft, Plus, Trash2, Copy, Check } from 'lucide-react'
import { accessKeysApi, adminUsersApi, namespaceMembersApi, namespacesApi } from '../../api/config'
import { isAxiosError } from 'axios'
import type { AccessKeyDto, NamespaceMemberDto, NamespaceRole } from '../../api/generated/model'
import { NamespaceRole as NamespaceRoleEnum } from '../../api/generated/model'
import { formatDateTime } from '../../utils/formatDateTime'

interface NamespaceDetailViewProps {
  slug: string
  onBack: () => void
  onToast: (toast: { message: string; severity: 'success' | 'error' }) => void
}

const ROLE_LABELS: Record<string, string> = {
  ADMIN: 'Admin',
  MEMBER: 'Member',
  READ_ONLY: 'Read Only',
}

const TAB_IDS = ['settings', 'members', 'api-keys'] as const

export function NamespaceDetailView({ slug, onBack, onToast }: NamespaceDetailViewProps) {
  const [tab, setTab] = useState(0)

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 3 }}>
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
      </Box>

      <Tabs
        value={tab}
        onChange={(_, v) => setTab(v)}
        aria-label="Namespace settings tabs"
        sx={{ borderBottom: 1, borderColor: 'divider' }}
      >
        <Tab label="Settings"  id="ns-tab-settings"  aria-controls="ns-panel-settings" />
        <Tab label="Members"   id="ns-tab-members"   aria-controls="ns-panel-members" />
        <Tab label="API Keys"  id="ns-tab-api-keys"  aria-controls="ns-panel-api-keys" />
      </Tabs>

      {TAB_IDS.map((id, i) => (
        <Box
          key={id}
          role="tabpanel"
          id={`ns-panel-${id}`}
          aria-labelledby={`ns-tab-${id}`}
          hidden={tab !== i}
        >
          {tab === 0 && i === 0 && <SettingsSection slug={slug} onToast={onToast} />}
          {tab === 1 && i === 1 && <MembersSection slug={slug} onToast={onToast} />}
          {tab === 2 && i === 2 && <ApiKeysSection slug={slug} onToast={onToast} />}
        </Box>
      ))}
    </Box>
  )
}

function SettingsSection({ slug, onToast }: { slug: string; onToast: NamespaceDetailViewProps['onToast'] }) {
  const [ownerOrg, setOwnerOrg] = useState('')
  const [publicCatalog, setPublicCatalog] = useState(false)
  const [autoApprove, setAutoApprove] = useState(false)
  const [saving, setSaving] = useState(false)
  const [loaded, setLoaded] = useState(false)

  useEffect(() => {
    async function load() {
      try {
        const res = await namespacesApi.listNamespaces()
        const ns = res.data.find((n) => n.slug === slug)
        if (ns) {
          setOwnerOrg(ns.ownerOrg ?? '')
          setPublicCatalog(ns.publicCatalog ?? false)
          setAutoApprove(ns.autoApproveReleases ?? false)
        }
      } catch { /* ignore */ }
      setLoaded(true)
    }
    load()
  }, [slug])

  async function handleSave() {
    setSaving(true)
    try {
      await namespacesApi.updateNamespace({
        ns: slug,
        namespaceUpdateRequest: {
          ownerOrg: ownerOrg.trim() || undefined,
          publicCatalog,
          autoApproveReleases: autoApprove,
        },
      })
      onToast({ message: 'Namespace settings saved.', severity: 'success' })
    } catch {
      onToast({ message: 'Failed to save namespace settings.', severity: 'error' })
    } finally {
      setSaving(false)
    }
  }

  if (!loaded) return <CircularProgress size={24} />

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
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
  const [addError, setAddError] = useState<string | null>(null)
  const [userOptions, setUserOptions] = useState<string[]>([])

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

  useEffect(() => {
    if (!addOpen) return
    async function loadUsers() {
      try {
        const res = await adminUsersApi.listUsers()
        const existing = new Set(members.map((m) => m.userSubject))
        setUserOptions(
          res.data
            .filter((u) => !u.isSuperadmin && !existing.has(u.username))
            .map((u) => u.username),
        )
      } catch {
        setUserOptions([])
      }
    }
    loadUsers()
  }, [addOpen, members])

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
    setAddError(null)
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
    } catch (error: unknown) {
      if (isAxiosError(error) && error.response?.status === 409) {
        setAddError(error.response.data?.message ?? `User "${newSubject.trim()}" is already a member of this namespace.`)
      } else {
        setAddError('Failed to add member.')
      }
    } finally {
      setAddSaving(false)
    }
  }

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
      <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'flex-end', mb: 2 }}>
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
                    variant="standard"
                    onChange={(e) => handleRoleChange(member, e.target.value as NamespaceRole)}
                    sx={{ fontSize: '0.875rem' }}
                    disableUnderline
                  >
                    {Object.values(NamespaceRoleEnum).map((role) => (
                      <MenuItem key={role} value={role}>
                        {ROLE_LABELS[role] ?? role}
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

      <Dialog open={addOpen} onClose={() => { setAddOpen(false); setAddError(null) }} maxWidth="xs" fullWidth>
        <DialogTitle>Add Member</DialogTitle>
        <DialogContent>
          <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2, pt: 1 }}>
            {addError && <Alert severity="error">{addError}</Alert>}
            <Autocomplete
              freeSolo
              options={userOptions}
              inputValue={newSubject}
              onInputChange={(_, value) => { setNewSubject(value); setAddError(null) }}
              renderInput={(params) => (
                <TextField
                  {...params}
                  label="User"
                  required
                  size="small"
                  autoFocus
                  helperText="Username or OIDC subject claim."
                />
              )}
            />
            <FormControl size="small" required>
              <InputLabel>Role</InputLabel>
              <Select
                value={newRole}
                label="Role"
                onChange={(e) => setNewRole(e.target.value as NamespaceRole)}
              >
                {Object.values(NamespaceRoleEnum).map((role) => (
                  <MenuItem key={role} value={role}>{ROLE_LABELS[role] ?? role}</MenuItem>
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

function ApiKeysSection({ slug, onToast }: { slug: string; onToast: NamespaceDetailViewProps['onToast'] }) {
  const [keys, setKeys] = useState<AccessKeyDto[]>([])
  const [loading, setLoading] = useState(true)
  const [createOpen, setCreateOpen] = useState(false)
  const [description, setDescription] = useState('')
  const [creating, setCreating] = useState(false)
  const [newKey, setNewKey] = useState<string | null>(null)
  const [copied, setCopied] = useState(false)

  const loadKeys = useCallback(async () => {
    setLoading(true)
    try {
      const res = await accessKeysApi.listAccessKeys({ ns: slug })
      setKeys(res.data)
    } catch {
      setKeys([])
    } finally {
      setLoading(false)
    }
  }, [slug])

  useEffect(() => {
    loadKeys()
  }, [loadKeys])

  async function handleCreate() {
    setCreating(true)
    try {
      const res = await accessKeysApi.createAccessKey({
        ns: slug,
        accessKeyCreateRequest: { description: description.trim() || undefined },
      })
      setNewKey(res.data.key)
      setDescription('')
      setCreateOpen(false)
      loadKeys()
    } catch {
      onToast({ message: 'Failed to create API key.', severity: 'error' })
    } finally {
      setCreating(false)
    }
  }

  async function handleRevoke(keyId: string) {
    try {
      await accessKeysApi.revokeAccessKey({ ns: slug, keyId })
      setKeys((prev) => prev.filter((k) => k.id !== keyId))
      onToast({ message: 'API key revoked.', severity: 'success' })
    } catch {
      onToast({ message: 'Failed to revoke API key.', severity: 'error' })
    }
  }

  function handleCopyKey() {
    if (!newKey) return
    navigator.clipboard.writeText(newKey)
    setCopied(true)
    setTimeout(() => setCopied(false), 2000)
  }

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, mb: 2 }}>
        <Typography variant="caption" color="text.secondary" sx={{ flex: 1 }}>
          API keys provide programmatic access for CI/CD pipelines and the SDK. The key is shown only once after creation.
        </Typography>
        <Button variant="outlined" size="small" startIcon={<Plus size={14} />} onClick={() => setCreateOpen(true)} sx={{ flexShrink: 0 }}>
          Generate Key
        </Button>
      </Box>

      {newKey && (
        <Alert
          severity="success"
          action={
            <Button size="small" startIcon={copied ? <Check size={14} /> : <Copy size={14} />} onClick={handleCopyKey}>
              {copied ? 'Copied' : 'Copy'}
            </Button>
          }
          onClose={() => setNewKey(null)}
        >
          <Typography variant="caption" sx={{ fontFamily: 'monospace', wordBreak: 'break-all' }}>
            {newKey}
          </Typography>
        </Alert>
      )}

      {loading ? (
        <Box sx={{ display: 'flex', justifyContent: 'center', py: 4 }}>
          <CircularProgress size={24} />
        </Box>
      ) : keys.length === 0 ? (
        <Typography variant="body2" color="text.secondary">No API keys configured.</Typography>
      ) : (
        <Table size="small" aria-label="API keys">
          <TableHead>
            <TableRow>
              <TableCell>Description</TableCell>
              <TableCell>Key Prefix</TableCell>
              <TableCell>Status</TableCell>
              <TableCell>Created</TableCell>
              <TableCell />
            </TableRow>
          </TableHead>
          <TableBody>
            {keys.map((key) => (
              <TableRow key={key.id} sx={{ opacity: key.revoked ? 0.5 : 1 }}>
                <TableCell>
                  <Typography variant="body2">{key.description || '—'}</Typography>
                </TableCell>
                <TableCell>
                  <Typography variant="caption" sx={{ fontFamily: 'monospace' }}>
                    {key.keyPrefix ?? '—'}
                  </Typography>
                </TableCell>
                <TableCell>
                  <Chip
                    label={key.revoked ? 'revoked' : 'active'}
                    size="small"
                    color={key.revoked ? 'default' : 'success'}
                  />
                </TableCell>
                <TableCell>
                  <Typography variant="caption" color="text.disabled">
                    {formatDateTime(key.createdAt)}
                  </Typography>
                </TableCell>
                <TableCell>
                  {!key.revoked && (
                    <Button
                      size="small"
                      color="error"
                      startIcon={<Trash2 size={14} />}
                      onClick={() => handleRevoke(key.id)}
                    >
                      Revoke
                    </Button>
                  )}
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      )}

      <Dialog open={createOpen} onClose={() => setCreateOpen(false)} maxWidth="xs" fullWidth>
        <DialogTitle>Generate API Key</DialogTitle>
        <DialogContent>
          <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2, pt: 1 }}>
            <TextField
              label="Description (optional)"
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              size="small"
              autoFocus
              helperText="A label to identify this key (e.g. 'CI pipeline')."
            />
          </Box>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setCreateOpen(false)}>Cancel</Button>
          <Button variant="contained" onClick={handleCreate} disabled={creating}>
            {creating ? 'Generating\u2026' : 'Generate'}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  )
}
