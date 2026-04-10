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
  Alert,
  CircularProgress,
  Select,
  MenuItem,
  FormControl,
  InputLabel,
  Autocomplete,
} from '@mui/material'
import { Plus, Trash2 } from 'lucide-react'
import { AppDialog } from '../../common/AppDialog'
import { DataTable } from '../../common/DataTable'
import type { DataColumn } from '../../common/DataTable'
import { ActionIconButton } from '../../common/ActionIconButton'
import { adminUsersApi, namespaceMembersApi } from '../../../api/config'
import { isAxiosError } from 'axios'
import type { NamespaceMemberDto, NamespaceRole } from '../../../api/generated/model'
import { NamespaceRole as NamespaceRoleEnum } from '../../../api/generated/model'
import { useUiStore } from '../../../stores/uiStore'

const ROLE_LABELS: Record<string, string> = {
  ADMIN: 'Admin',
  MEMBER: 'Member',
  READ_ONLY: 'Read Only',
}

export function MembersSection({ slug }: { slug: string }) {
  const addToast = useUiStore((s) => s.addToast)
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
      addToast({ message: `Failed to update role for "${member.userSubject}".`, type: 'error' })
    }
  }

  async function handleRemove(member: NamespaceMemberDto) {
    try {
      await namespaceMembersApi.removeNamespaceMember({ ns: slug, userSubject: member.userSubject })
      setMembers((prev) => prev.filter((m) => m.userSubject !== member.userSubject))
      addToast({ message: `Member "${member.userSubject}" removed.`, type: 'success' })
    } catch {
      addToast({ message: `Failed to remove member "${member.userSubject}".`, type: 'error' })
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
      addToast({ message: `Member "${newSubject.trim()}" added.`, type: 'success' })
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
      render: (member) => <Typography variant="body2" fontWeight={500}>{member.userSubject}</Typography>,
    },
    {
      key: 'role',
      header: 'Role',
      render: (member) => (
        <Select value={member.role} size="small" variant="standard" onChange={(e) => handleRoleChange(member, e.target.value as NamespaceRole)} sx={{ fontSize: '0.875rem' }} disableUnderline>
          {Object.values(NamespaceRoleEnum).map((role) => (
            <MenuItem key={role} value={role}>{ROLE_LABELS[role] ?? role}</MenuItem>
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
      render: (member) => <ActionIconButton icon={Trash2} tooltip="Remove member" color="error" onClick={() => handleRemove(member)} />,
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
        <Box sx={{ display: 'flex', justifyContent: 'center', py: 4 }}><CircularProgress size={24} /></Box>
      ) : members.length === 0 ? (
        <Typography variant="body2" color="text.secondary">No members found.</Typography>
      ) : (
        <DataTable<NamespaceMemberDto> columns={memberColumns} rows={members} keyFn={(member) => member.userSubject} ariaLabel="Namespace members" />
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
              <TextField {...params} label="User" required size="small" autoFocus helperText="Username or OIDC subject claim." />
            )}
          />
          <FormControl size="small" required>
            <InputLabel>Role</InputLabel>
            <Select value={newRole} label="Role" onChange={(e) => setNewRole(e.target.value as NamespaceRole)}>
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
