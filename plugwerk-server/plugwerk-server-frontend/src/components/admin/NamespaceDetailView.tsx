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
  Chip,
  Select,
  MenuItem,
  FormControl,
  InputLabel,
  Tabs,
  Tab,
  Autocomplete,
} from '@mui/material'
import { ArrowLeft, Plus, Trash2, Copy, Check } from 'lucide-react'
import { AppDialog } from '../common/AppDialog'
import { DataTable } from '../common/DataTable'
import type { DataColumn } from '../common/DataTable'
import { ActionIconButton } from '../common/ActionIconButton'
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
  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
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
          setName(ns.name ?? '')
          setDescription(ns.description ?? '')
          setPublicCatalog(ns.publicCatalog ?? false)
          setAutoApprove(ns.autoApproveReleases ?? false)
        }
      } catch { /* ignore */ }
      setLoaded(true)
    }
    load()
  }, [slug])

  async function handleSave() {
    if (!name.trim()) return
    setSaving(true)
    try {
      await namespacesApi.updateNamespace({
        ns: slug,
        namespaceUpdateRequest: {
          name: name.trim(),
          description: description.trim() || undefined,
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
    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 3 }}>
      <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr' }, gap: 3 }}>
        <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
          <TextField
            label="Name"
            value={name}
            onChange={(e) => setName(e.target.value)}
            required
            size="small"
            fullWidth
          />
          <TextField
            label="Description"
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            size="small"
            multiline
            minRows={2}
            fullWidth
          />
        </Box>
        <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1 }}>
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
        </Box>
      </Box>
      <Button
        variant="contained"
        onClick={handleSave}
        disabled={saving || !name.trim()}
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
        const res = await adminUsersApi.listUsers({ enabled: true })
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

  const memberColumns: DataColumn<NamespaceMemberDto>[] = [
    {
      key: 'subject',
      header: 'Subject',
      render: (member) => (
        <Typography variant="body2" fontWeight={500}>{member.userSubject}</Typography>
      ),
    },
    {
      key: 'role',
      header: 'Role',
      render: (member) => (
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
      ),
    },
    {
      key: 'created',
      header: 'Created',
      render: (member) => (
        <Typography variant="caption" color="text.disabled">
          {new Date(member.createdAt).toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' })}
        </Typography>
      ),
    },
    {
      key: 'actions',
      header: '',
      align: 'right',
      render: (member) => (
        <ActionIconButton icon={Trash2} tooltip="Remove member" color="error" onClick={() => handleRemove(member)} />
      ),
    },
  ]

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
        <DataTable<NamespaceMemberDto>
          columns={memberColumns}
          rows={members}
          keyFn={(member) => member.userSubject}
          ariaLabel="Namespace members"
        />
      )}

      <AppDialog
        open={addOpen}
        onClose={() => { setAddOpen(false); setAddError(null) }}
        title="Add Member"
        description="Add a user to this namespace by entering their username or OIDC subject claim and selecting a role."
        actionLabel="Add Member"
        onAction={handleAdd}
        actionDisabled={!newSubject.trim()}
        actionLoading={addSaving}
      >
        <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
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
      </AppDialog>
    </Box>
  )
}

function ApiKeysSection({ slug, onToast }: { slug: string; onToast: NamespaceDetailViewProps['onToast'] }) {
  const [keys, setKeys] = useState<AccessKeyDto[]>([])
  const [loading, setLoading] = useState(true)
  const [createOpen, setCreateOpen] = useState(false)
  const [keyName, setKeyName] = useState('')
  const [expiresAt, setExpiresAt] = useState('')
  const [creating, setCreating] = useState(false)
  const [createError, setCreateError] = useState<string | null>(null)
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
    if (!keyName.trim()) return
    setCreating(true)
    setCreateError(null)
    try {
      const parsedExpiry = expiresAt ? new Date(expiresAt).toISOString() : undefined
      const res = await accessKeysApi.createAccessKey({
        ns: slug,
        accessKeyCreateRequest: {
          name: keyName.trim(),
          expiresAt: parsedExpiry,
        },
      })
      setNewKey(res.data.key)
      setKeyName('')
      setExpiresAt('')
      setCreateOpen(false)
      loadKeys()
    } catch (error: unknown) {
      if (isAxiosError(error) && error.response?.status === 409) {
        setCreateError(`An API key named "${keyName.trim()}" already exists in this namespace.`)
      } else {
        const msg = isAxiosError(error)
          ? (error.response?.data?.message ?? error.message)
          : 'Failed to create API key.'
        setCreateError(msg)
      }
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

  const apiKeyColumns: DataColumn<AccessKeyDto>[] = [
    {
      key: 'name',
      header: 'Name',
      render: (key) => (
        <Typography variant="body2">{key.name || '—'}</Typography>
      ),
    },
    {
      key: 'keyPrefix',
      header: 'Key Prefix',
      render: (key) => (
        <Typography variant="caption" sx={{ fontFamily: 'monospace' }}>
          {key.keyPrefix ?? '—'}
        </Typography>
      ),
    },
    {
      key: 'status',
      header: 'Status',
      render: (key) => (
        <Chip
          label={key.revoked ? 'revoked' : 'active'}
          size="small"
          color={key.revoked ? 'default' : 'success'}
        />
      ),
    },
    {
      key: 'expires',
      header: 'Expires',
      render: (key) => (
        <Typography variant="caption" color="text.disabled">
          {key.expiresAt ? formatDateTime(key.expiresAt) : 'Never'}
        </Typography>
      ),
    },
    {
      key: 'created',
      header: 'Created',
      render: (key) => (
        <Typography variant="caption" color="text.disabled">
          {formatDateTime(key.createdAt)}
        </Typography>
      ),
    },
    {
      key: 'actions',
      header: '',
      align: 'right',
      render: (key) =>
        !key.revoked ? (
          <ActionIconButton icon={Trash2} tooltip="Revoke key" color="error" onClick={() => handleRevoke(key.id)} />
        ) : null,
    },
  ]

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
        <DataTable<AccessKeyDto>
          columns={apiKeyColumns}
          rows={keys}
          keyFn={(key) => key.id}
          ariaLabel="API keys"
          rowSx={(key) => key.revoked ? { opacity: 0.5 } : undefined}
        />
      )}

      <AppDialog
        open={createOpen}
        onClose={() => { setCreateOpen(false); setCreateError(null) }}
        title="Generate API Key"
        description="Create a new API key for programmatic access. The key is shown only once after creation."
        actionLabel="Generate Key"
        onAction={handleCreate}
        actionDisabled={!keyName.trim()}
        actionLoading={creating}
      >
        <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
          {createError && <Alert severity="error">{createError}</Alert>}
          <TextField
            label="Name"
            value={keyName}
            onChange={(e) => { setKeyName(e.target.value); setCreateError(null) }}
            size="small"
            required
            autoFocus
            helperText="Unique name to identify this key (e.g. 'CI pipeline')."
          />
          <TextField
            label="Expires (optional)"
            type="datetime-local"
            value={expiresAt}
            onChange={(e) => setExpiresAt(e.target.value)}
            size="small"
            slotProps={{ inputLabel: { shrink: true } }}
            helperText="Leave empty for a key that never expires."
          />
        </Box>
      </AppDialog>
    </Box>
  )
}
