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
import { useState, useEffect } from 'react'
import { Outlet } from 'react-router-dom'
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
  Snackbar,
  CircularProgress,
  Chip,
  Switch,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
} from '@mui/material'
import { CheckCircle, Plus, Shield, Trash2 } from 'lucide-react'
import { DataTable } from '../components/common/DataTable'
import type { DataColumn } from '../components/common/DataTable'
import { AdminSidebar } from '../components/admin/AdminSidebar'
import { adminUsersApi, oidcProvidersApi, reviewsApi } from '../api/config'
import { useAuthStore } from '../stores/authStore'
import type { OidcProviderDto, OidcProviderType, ReviewItemDto, UserDto } from '../api/generated/model'

function GeneralSection() {
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
      <Button variant="contained" sx={{ alignSelf: 'flex-start' }}>Save Changes</Button>
    </Box>
  )
}

function UsersSection() {
  const [users, setUsers] = useState<UserDto[]>([])
  const [loading, setLoading] = useState(true)
  const [dialogOpen, setDialogOpen] = useState(false)
  const [username, setUsername] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [saving, setSaving] = useState(false)
  const [toast, setToast] = useState<{ message: string; severity: 'success' | 'error' } | null>(null)

  useEffect(() => {
    async function load() {
      setLoading(true)
      try {
        const res = await adminUsersApi.listUsers()
        setUsers(res.data)
      } catch {
        setUsers([])
      } finally {
        setLoading(false)
      }
    }
    load()
  }, [])

  async function handleToggleEnabled(user: UserDto) {
    try {
      const res = await adminUsersApi.updateUser({ userId: user.id, userUpdateRequest: { enabled: !user.enabled } })
      setUsers((prev) => prev.map((u) => (u.id === user.id ? res.data : u)))
    } catch {
      setToast({ message: `Failed to update user ${user.username}.`, severity: 'error' })
    }
  }

  async function handleDelete(user: UserDto) {
    try {
      await adminUsersApi.deleteUser({ userId: user.id })
      setUsers((prev) => prev.filter((u) => u.id !== user.id))
      setToast({ message: `User "${user.username}" deleted.`, severity: 'success' })
    } catch {
      setToast({ message: `Failed to delete user ${user.username}.`, severity: 'error' })
    }
  }

  async function handleCreate() {
    if (!username.trim() || !password) return
    setSaving(true)
    try {
      const res = await adminUsersApi.createUser({
        userCreateRequest: { username: username.trim(), email: email.trim() || undefined, password },
      })
      setUsers((prev) => [...prev, res.data])
      setToast({ message: `User "${res.data.username}" created.`, severity: 'success' })
      setDialogOpen(false)
      setUsername('')
      setEmail('')
      setPassword('')
    } catch {
      setToast({ message: 'Failed to create user.', severity: 'error' })
    } finally {
      setSaving(false)
    }
  }

  const userColumns: DataColumn<UserDto>[] = [
    {
      key: 'username',
      header: 'Username',
      render: (user) => (
        <>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5, flexWrap: 'wrap' }}>
            <Typography variant="body2" fontWeight={500}>{user.username}</Typography>
            {user.isSuperadmin && (
              <Chip
                icon={<Shield size={12} />}
                label="superadmin"
                size="small"
                color="primary"
                sx={{ height: 18, fontSize: '0.65rem' }}
              />
            )}
          </Box>
          {user.passwordChangeRequired && (
            <Chip label="pw change required" size="small" color="warning" sx={{ mt: 0.5 }} />
          )}
        </>
      ),
    },
    {
      key: 'email',
      header: 'Email',
      render: (user) => (
        <Typography variant="caption" color="text.secondary">{user.email ?? '—'}</Typography>
      ),
    },
    {
      key: 'status',
      header: 'Status',
      render: (user) => (
        <Chip
          label={user.enabled ? 'active' : 'disabled'}
          size="small"
          color={user.enabled ? 'success' : 'default'}
        />
      ),
    },
    {
      key: 'created',
      header: 'Created',
      render: (user) => (
        <Typography variant="caption" color="text.disabled">
          {new Date(user.createdAt).toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' })}
        </Typography>
      ),
    },
    {
      key: 'enabled',
      header: 'Enabled',
      render: (user) => (
        <Switch
          checked={user.enabled}
          size="small"
          onChange={() => handleToggleEnabled(user)}
          disabled={user.isSuperadmin}
          inputProps={{ 'aria-label': `Toggle ${user.username}` }}
        />
      ),
    },
    {
      key: 'actions',
      header: '',
      render: (user) => (
        <Button
          size="small"
          color="error"
          startIcon={<Trash2 size={14} />}
          onClick={() => handleDelete(user)}
          disabled={user.isSuperadmin}
        >
          Delete
        </Button>
      ),
    },
  ]

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 3 }}>
      <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
        <Box>
          <Typography variant="h2" gutterBottom>Users</Typography>
          <Divider sx={{ mb: 3 }} />
        </Box>
        <Button variant="outlined" size="small" startIcon={<Plus size={14} />} onClick={() => setDialogOpen(true)}>
          Add User
        </Button>
      </Box>

      {loading ? (
        <Box sx={{ display: 'flex', justifyContent: 'center', py: 4 }}>
          <CircularProgress size={24} />
        </Box>
      ) : users.length === 0 ? (
        <Typography variant="body2" color="text.secondary">No users found.</Typography>
      ) : (
        <DataTable<UserDto>
          columns={userColumns}
          rows={users}
          keyFn={(user) => user.id}
          ariaLabel="Users"
        />
      )}

      <Dialog open={dialogOpen} onClose={() => setDialogOpen(false)} maxWidth="xs" fullWidth>
        <DialogTitle>Add User</DialogTitle>
        <DialogContent>
          <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2, pt: 1 }}>
            <TextField
              label="Username"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              required
              size="small"
              autoFocus
            />
            <TextField
              label="Email"
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              size="small"
            />
            <TextField
              label="Initial Password"
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
              size="small"
              helperText="User will be required to change this on first login."
            />
          </Box>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDialogOpen(false)}>Cancel</Button>
          <Button
            variant="contained"
            onClick={handleCreate}
            disabled={saving || !username.trim() || !password}
          >
            {saving ? 'Creating…' : 'Create'}
          </Button>
        </DialogActions>
      </Dialog>

      <Snackbar open={!!toast} autoHideDuration={4000} onClose={() => setToast(null)} anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}>
        <Alert severity={toast?.severity} onClose={() => setToast(null)} sx={{ width: '100%' }}>
          {toast?.message}
        </Alert>
      </Snackbar>
    </Box>
  )
}

const PROVIDER_TYPE_LABELS: Record<string, string> = {
  GENERIC_OIDC: 'Generic OIDC',
  KEYCLOAK: 'Keycloak',
  GITHUB: 'GitHub',
  GOOGLE: 'Google',
  FACEBOOK: 'Facebook',
}

const ISSUER_REQUIRED_TYPES = new Set(['GENERIC_OIDC', 'KEYCLOAK'])

function OidcProvidersSection() {
  const [providers, setProviders] = useState<OidcProviderDto[]>([])
  const [loading, setLoading] = useState(true)
  const [dialogOpen, setDialogOpen] = useState(false)
  const [name, setName] = useState('')
  const [providerType, setProviderType] = useState<OidcProviderType>('GENERIC_OIDC')
  const [clientId, setClientId] = useState('')
  const [clientSecret, setClientSecret] = useState('')
  const [issuerUri, setIssuerUri] = useState('')
  const [scope, setScope] = useState('openid profile email')
  const [saving, setSaving] = useState(false)
  const [toast, setToast] = useState<{ message: string; severity: 'success' | 'error' } | null>(null)

  useEffect(() => {
    async function load() {
      setLoading(true)
      try {
        const res = await oidcProvidersApi.listOidcProviders()
        setProviders(res.data)
      } catch {
        setProviders([])
      } finally {
        setLoading(false)
      }
    }
    load()
  }, [])

  async function handleToggleEnabled(provider: OidcProviderDto) {
    try {
      const res = await oidcProvidersApi.updateOidcProvider({
        providerId: provider.id,
        oidcProviderUpdateRequest: { enabled: !provider.enabled },
      })
      setProviders((prev) => prev.map((p) => (p.id === provider.id ? res.data : p)))
    } catch {
      setToast({ message: `Failed to update provider "${provider.name}".`, severity: 'error' })
    }
  }

  async function handleDelete(provider: OidcProviderDto) {
    try {
      await oidcProvidersApi.deleteOidcProvider({ providerId: provider.id })
      setProviders((prev) => prev.filter((p) => p.id !== provider.id))
      setToast({ message: `Provider "${provider.name}" deleted.`, severity: 'success' })
    } catch {
      setToast({ message: `Failed to delete provider "${provider.name}".`, severity: 'error' })
    }
  }

  async function handleCreate() {
    if (!name.trim() || !clientId.trim() || !clientSecret.trim()) return
    if (ISSUER_REQUIRED_TYPES.has(providerType) && !issuerUri.trim()) return
    setSaving(true)
    try {
      const res = await oidcProvidersApi.createOidcProvider({
        oidcProviderCreateRequest: {
          name: name.trim(),
          providerType,
          clientId: clientId.trim(),
          clientSecret: clientSecret.trim(),
          issuerUri: issuerUri.trim() || undefined,
          scope: scope.trim() || undefined,
        },
      })
      setProviders((prev) => [...prev, res.data])
      setToast({ message: `Provider "${res.data.name}" created.`, severity: 'success' })
      setDialogOpen(false)
      setName('')
      setClientId('')
      setClientSecret('')
      setIssuerUri('')
      setScope('openid profile email')
      setProviderType('GENERIC_OIDC')
    } catch {
      setToast({ message: 'Failed to create OIDC provider.', severity: 'error' })
    } finally {
      setSaving(false)
    }
  }

  const issuerRequired = ISSUER_REQUIRED_TYPES.has(providerType)

  const oidcColumns: DataColumn<OidcProviderDto>[] = [
    {
      key: 'name',
      header: 'Name',
      render: (provider) => (
        <Typography variant="body2" fontWeight={500}>{provider.name}</Typography>
      ),
    },
    {
      key: 'type',
      header: 'Type',
      render: (provider) => (
        <Chip label={PROVIDER_TYPE_LABELS[provider.providerType] ?? provider.providerType} size="small" />
      ),
    },
    {
      key: 'issuer',
      header: 'Issuer / Client ID',
      render: (provider) =>
        provider.issuerUri ? (
          <Typography variant="caption" color="text.secondary">{provider.issuerUri}</Typography>
        ) : (
          <Typography variant="caption" color="text.disabled">{provider.clientId}</Typography>
        ),
    },
    {
      key: 'enabled',
      header: 'Enabled',
      render: (provider) => (
        <Switch
          checked={provider.enabled}
          size="small"
          onChange={() => handleToggleEnabled(provider)}
          inputProps={{ 'aria-label': `Toggle ${provider.name}` }}
        />
      ),
    },
    {
      key: 'actions',
      header: '',
      render: (provider) => (
        <Button
          size="small"
          color="error"
          startIcon={<Trash2 size={14} />}
          onClick={() => handleDelete(provider)}
        >
          Delete
        </Button>
      ),
    },
  ]

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 3 }}>
      <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
        <Box>
          <Typography variant="h2" gutterBottom>OIDC Providers</Typography>
          <Divider sx={{ mb: 3 }} />
        </Box>
        <Button variant="outlined" size="small" startIcon={<Plus size={14} />} onClick={() => setDialogOpen(true)}>
          Add Provider
        </Button>
      </Box>

      <Alert severity="info">
        OIDC is disabled by default. Enable individual providers after adding their credentials.
      </Alert>

      {loading ? (
        <Box sx={{ display: 'flex', justifyContent: 'center', py: 4 }}>
          <CircularProgress size={24} />
        </Box>
      ) : providers.length === 0 ? (
        <Typography variant="body2" color="text.secondary">No OIDC providers configured.</Typography>
      ) : (
        <DataTable<OidcProviderDto>
          columns={oidcColumns}
          rows={providers}
          keyFn={(provider) => provider.id}
          ariaLabel="OIDC providers"
        />
      )}

      <Dialog open={dialogOpen} onClose={() => setDialogOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle>Add OIDC Provider</DialogTitle>
        <DialogContent>
          <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2, pt: 1 }}>
            <TextField
              label="Display Name"
              value={name}
              onChange={(e) => setName(e.target.value)}
              required
              size="small"
              autoFocus
            />
            <FormControl size="small" required>
              <InputLabel>Provider Type</InputLabel>
              <Select
                value={providerType}
                label="Provider Type"
                onChange={(e) => setProviderType(e.target.value as OidcProviderType)}
              >
                {Object.entries(PROVIDER_TYPE_LABELS).map(([value, label]) => (
                  <MenuItem key={value} value={value}>{label}</MenuItem>
                ))}
              </Select>
            </FormControl>
            <TextField
              label="Client ID"
              value={clientId}
              onChange={(e) => setClientId(e.target.value)}
              required
              size="small"
            />
            <TextField
              label="Client Secret"
              type="password"
              value={clientSecret}
              onChange={(e) => setClientSecret(e.target.value)}
              required
              size="small"
            />
            {issuerRequired && (
              <TextField
                label="Issuer URI"
                value={issuerUri}
                onChange={(e) => setIssuerUri(e.target.value)}
                required
                size="small"
                placeholder="https://your-idp.example.com/realms/myrealm"
              />
            )}
            <TextField
              label="Scope"
              value={scope}
              onChange={(e) => setScope(e.target.value)}
              size="small"
              helperText="Space-separated OAuth2 scopes"
            />
          </Box>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDialogOpen(false)}>Cancel</Button>
          <Button
            variant="contained"
            onClick={handleCreate}
            disabled={
              saving ||
              !name.trim() ||
              !clientId.trim() ||
              !clientSecret.trim() ||
              (issuerRequired && !issuerUri.trim())
            }
          >
            {saving ? 'Creating…' : 'Create'}
          </Button>
        </DialogActions>
      </Dialog>

      <Snackbar open={!!toast} autoHideDuration={4000} onClose={() => setToast(null)} anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}>
        <Alert severity={toast?.severity} onClose={() => setToast(null)} sx={{ width: '100%' }}>
          {toast?.message}
        </Alert>
      </Snackbar>
    </Box>
  )
}

function ReviewsSection() {
  const namespace = useAuthStore((s) => s.namespace)
  const [items, setItems] = useState<ReviewItemDto[]>([])
  const [loading, setLoading] = useState(true)
  const [approvingId, setApprovingId] = useState<string | null>(null)
  const [toast, setToast] = useState<{ message: string; severity: 'success' | 'error' } | null>(null)

  useEffect(() => {
    async function load() {
      setLoading(true)
      try {
        const res = await reviewsApi.listPendingReviews({ ns: namespace })
        setItems(res.data)
      } catch {
        setItems([])
      } finally {
        setLoading(false)
      }
    }
    load()
  }, [namespace])

  async function handleApprove(item: ReviewItemDto) {
    setApprovingId(item.releaseId)
    try {
      await reviewsApi.approveRelease({ ns: namespace, releaseId: item.releaseId })
      setItems((prev) => prev.filter((i) => i.releaseId !== item.releaseId))
      setToast({ message: `${item.pluginName} v${item.version} approved and published.`, severity: 'success' })
    } catch {
      setToast({ message: `Failed to approve ${item.pluginName} v${item.version}.`, severity: 'error' })
    } finally {
      setApprovingId(null)
    }
  }

  const reviewColumns: DataColumn<ReviewItemDto>[] = [
    {
      key: 'plugin',
      header: 'Plugin',
      render: (item) => (
        <>
          <Typography variant="body2" fontWeight={500}>{item.pluginName}</Typography>
          <Typography variant="caption" color="text.secondary">{item.pluginId}</Typography>
        </>
      ),
    },
    {
      key: 'version',
      header: 'Version',
      render: (item) => <>v{item.version}</>,
    },
    {
      key: 'submitted',
      header: 'Submitted',
      render: (item) => (
        <Typography variant="caption" color="text.disabled">
          {new Date(item.submittedAt).toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' })}
        </Typography>
      ),
    },
    {
      key: 'action',
      header: 'Action',
      render: (item) => (
        <Button
          variant="outlined"
          size="small"
          color="success"
          startIcon={<CheckCircle size={14} />}
          loading={approvingId === item.releaseId}
          onClick={() => handleApprove(item)}
        >
          Approve
        </Button>
      ),
    },
  ]

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 3 }}>
      <Box>
        <Typography variant="h2" gutterBottom>Pending Reviews</Typography>
        <Divider sx={{ mb: 3 }} />
      </Box>

      {loading ? (
        <Box sx={{ display: 'flex', justifyContent: 'center', py: 4 }}>
          <CircularProgress size={24} />
        </Box>
      ) : items.length === 0 ? (
        <Typography variant="body2" color="text.secondary">
          No releases awaiting review.
        </Typography>
      ) : (
        <DataTable<ReviewItemDto>
          columns={reviewColumns}
          rows={items}
          keyFn={(item) => item.releaseId}
          ariaLabel="Pending reviews"
        />
      )}

      <Snackbar
        open={!!toast}
        autoHideDuration={4000}
        onClose={() => setToast(null)}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}
      >
        <Alert severity={toast?.severity} onClose={() => setToast(null)} sx={{ width: '100%' }}>
          {toast?.message}
        </Alert>
      </Snackbar>
    </Box>
  )
}

export { GeneralSection, UsersSection, OidcProvidersSection, ReviewsSection }

export function AdminSettingsPage() {
  return (
    <Box component="main" id="main-content" sx={{ flex: 1, display: 'flex' }}>
      <AdminSidebar />
      <Box sx={{ flex: 1, overflow: 'auto' }}>
        <Container maxWidth="lg" sx={{ py: 4 }}>
          <Outlet />
        </Container>
      </Box>
    </Box>
  )
}
