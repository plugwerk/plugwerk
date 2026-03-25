// SPDX-License-Identifier: AGPL-3.0
// Copyright (C) 2026 devtank42 GmbH
import { useState, useEffect } from 'react'
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
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  Chip,
  Switch,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
} from '@mui/material'
import { CheckCircle, Plus, Shield, Trash2 } from 'lucide-react'
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
        <Table size="small" aria-label="Users">
          <TableHead>
            <TableRow>
              <TableCell>Username</TableCell>
              <TableCell>Email</TableCell>
              <TableCell>Status</TableCell>
              <TableCell>Created</TableCell>
              <TableCell>Enabled</TableCell>
              <TableCell />
            </TableRow>
          </TableHead>
          <TableBody>
            {users.map((user) => (
              <TableRow key={user.id}>
                <TableCell>
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
                </TableCell>
                <TableCell>
                  <Typography variant="caption" color="text.secondary">{user.email ?? '—'}</Typography>
                </TableCell>
                <TableCell>
                  <Chip
                    label={user.enabled ? 'active' : 'disabled'}
                    size="small"
                    color={user.enabled ? 'success' : 'default'}
                  />
                </TableCell>
                <TableCell>
                  <Typography variant="caption" color="text.disabled">
                    {new Date(user.createdAt).toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' })}
                  </Typography>
                </TableCell>
                <TableCell>
                  <Switch
                    checked={user.enabled}
                    size="small"
                    onChange={() => handleToggleEnabled(user)}
                    disabled={user.isSuperadmin}
                    inputProps={{ 'aria-label': `Toggle ${user.username}` }}
                  />
                </TableCell>
                <TableCell>
                  <Button
                    size="small"
                    color="error"
                    startIcon={<Trash2 size={14} />}
                    onClick={() => handleDelete(user)}
                    disabled={user.isSuperadmin}
                  >
                    Delete
                  </Button>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
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
        <Table size="small" aria-label="OIDC providers">
          <TableHead>
            <TableRow>
              <TableCell>Name</TableCell>
              <TableCell>Type</TableCell>
              <TableCell>Issuer / Client ID</TableCell>
              <TableCell>Enabled</TableCell>
              <TableCell />
            </TableRow>
          </TableHead>
          <TableBody>
            {providers.map((provider) => (
              <TableRow key={provider.id}>
                <TableCell>
                  <Typography variant="body2" fontWeight={500}>{provider.name}</Typography>
                </TableCell>
                <TableCell>
                  <Chip label={PROVIDER_TYPE_LABELS[provider.providerType] ?? provider.providerType} size="small" />
                </TableCell>
                <TableCell>
                  {provider.issuerUri ? (
                    <Typography variant="caption" color="text.secondary">{provider.issuerUri}</Typography>
                  ) : (
                    <Typography variant="caption" color="text.disabled">{provider.clientId}</Typography>
                  )}
                </TableCell>
                <TableCell>
                  <Switch
                    checked={provider.enabled}
                    size="small"
                    onChange={() => handleToggleEnabled(provider)}
                    inputProps={{ 'aria-label': `Toggle ${provider.name}` }}
                  />
                </TableCell>
                <TableCell>
                  <Button
                    size="small"
                    color="error"
                    startIcon={<Trash2 size={14} />}
                    onClick={() => handleDelete(provider)}
                  >
                    Delete
                  </Button>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
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
        <Table size="small" aria-label="Pending reviews">
          <TableHead>
            <TableRow>
              <TableCell>Plugin</TableCell>
              <TableCell>Version</TableCell>
              <TableCell>Submitted</TableCell>
              <TableCell>Action</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {items.map((item) => (
              <TableRow key={item.releaseId}>
                <TableCell>
                  <Typography variant="body2" fontWeight={500}>{item.pluginName}</Typography>
                  <Typography variant="caption" color="text.secondary">{item.pluginId}</Typography>
                </TableCell>
                <TableCell>v{item.version}</TableCell>
                <TableCell>
                  <Typography variant="caption" color="text.disabled">
                    {new Date(item.submittedAt).toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' })}
                  </Typography>
                </TableCell>
                <TableCell>
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
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
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
  'oidc-providers': <OidcProvidersSection />,
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
