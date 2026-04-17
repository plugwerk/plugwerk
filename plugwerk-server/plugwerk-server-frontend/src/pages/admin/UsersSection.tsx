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
import { useState, useEffect } from "react";
import {
  Box,
  Typography,
  TextField,
  Button,
  Divider,
  CircularProgress,
  Chip,
  Switch,
} from "@mui/material";
import { Plus, Shield, Trash2 } from "lucide-react";
import { AppDialog } from "../../components/common/AppDialog";
import { ConfirmDeleteDialog } from "../../components/common/ConfirmDeleteDialog";
import { DataTable } from "../../components/common/DataTable";
import type { DataColumn } from "../../components/common/DataTable";
import { ActionIconButton } from "../../components/common/ActionIconButton";
import { Timestamp } from "../../components/common/Timestamp";
import { adminUsersApi } from "../../api/config";
import { useUiStore } from "../../stores/uiStore";
import type { UserDto } from "../../api/generated/model";

export function UsersSection() {
  const [users, setUsers] = useState<UserDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [dialogOpen, setDialogOpen] = useState(false);
  const [username, setUsername] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [saving, setSaving] = useState(false);
  const addToast = useUiStore((s) => s.addToast);
  const [deleteTarget, setDeleteTarget] = useState<UserDto | null>(null);
  const [deleting, setDeleting] = useState(false);

  useEffect(() => {
    async function load() {
      setLoading(true);
      try {
        const res = await adminUsersApi.listUsers();
        setUsers(res.data);
      } catch {
        setUsers([]);
      } finally {
        setLoading(false);
      }
    }
    load();
  }, []);

  async function handleToggleEnabled(user: UserDto) {
    try {
      const res = await adminUsersApi.updateUser({
        userId: user.id,
        userUpdateRequest: { enabled: !user.enabled },
      });
      setUsers((prev) => prev.map((u) => (u.id === user.id ? res.data : u)));
      addToast({
        message: `User "${user.username}" ${res.data.enabled ? "enabled" : "disabled"}.`,
        type: "success",
      });
    } catch {
      addToast({
        message: `Failed to update user ${user.username}.`,
        type: "error",
      });
    }
  }

  async function handleConfirmDelete() {
    if (!deleteTarget) return;
    setDeleting(true);
    try {
      await adminUsersApi.deleteUser({ userId: deleteTarget.id });
      setUsers((prev) => prev.filter((u) => u.id !== deleteTarget.id));
      addToast({
        message: `User "${deleteTarget.username}" deleted.`,
        type: "success",
      });
      setDeleteTarget(null);
    } catch {
      addToast({
        message: `Failed to delete user ${deleteTarget.username}.`,
        type: "error",
      });
    } finally {
      setDeleting(false);
    }
  }

  async function handleCreate() {
    if (!username.trim() || !password) return;
    setSaving(true);
    try {
      const res = await adminUsersApi.createUser({
        userCreateRequest: {
          username: username.trim(),
          email: email.trim() || undefined,
          password,
        },
      });
      setUsers((prev) => [...prev, res.data]);
      addToast({
        message: `User "${res.data.username}" created.`,
        type: "success",
      });
      setDialogOpen(false);
      setUsername("");
      setEmail("");
      setPassword("");
    } catch {
      addToast({ message: "Failed to create user.", type: "error" });
    } finally {
      setSaving(false);
    }
  }

  const userColumns: DataColumn<UserDto>[] = [
    {
      key: "username",
      header: "Username",
      render: (user) => (
        <Box
          sx={{
            display: "flex",
            alignItems: "center",
            gap: 0.5,
            flexWrap: "wrap",
          }}
        >
          <Typography variant="body2" fontWeight={500}>
            {user.username}
          </Typography>
          {user.isSuperadmin && (
            <Chip
              icon={<Shield size={12} />}
              label="superadmin"
              size="small"
              color="primary"
              sx={{ height: 18, fontSize: "0.65rem" }}
            />
          )}
          {user.passwordChangeRequired && (
            <Chip
              label="pw change required"
              size="small"
              color="warning"
              sx={{ height: 18, fontSize: "0.65rem" }}
            />
          )}
        </Box>
      ),
    },
    {
      key: "email",
      header: "Email",
      render: (user) => (
        <Typography variant="caption" color="text.secondary">
          {user.email ?? "—"}
        </Typography>
      ),
    },
    {
      key: "status",
      header: "Status",
      render: (user) => (
        <Chip
          label={user.enabled ? "active" : "disabled"}
          size="small"
          color={user.enabled ? "success" : "default"}
        />
      ),
    },
    {
      key: "created",
      header: "Created",
      render: (user) => (
        <Typography variant="caption" color="text.disabled">
          <Timestamp date={user.createdAt} />
        </Typography>
      ),
    },
    {
      key: "enabled",
      header: "Enabled",
      render: (user) => (
        <Switch
          checked={user.enabled}
          size="small"
          onChange={() => handleToggleEnabled(user)}
          disabled={user.isSuperadmin}
          inputProps={{ "aria-label": `Toggle ${user.username}` }}
        />
      ),
    },
    {
      key: "actions",
      header: "",
      align: "right",
      render: (user) => (
        <ActionIconButton
          icon={Trash2}
          tooltip="Delete"
          color="error"
          onClick={() => setDeleteTarget(user)}
          disabled={user.isSuperadmin}
        />
      ),
    },
  ];

  return (
    <Box sx={{ display: "flex", flexDirection: "column", gap: 3 }}>
      <Box
        sx={{
          display: "flex",
          alignItems: "center",
          justifyContent: "space-between",
        }}
      >
        <Box>
          <Typography variant="h2" gutterBottom>
            Users
          </Typography>
          <Divider sx={{ mb: 3 }} />
        </Box>
        <Button
          variant="outlined"
          size="small"
          startIcon={<Plus size={14} />}
          onClick={() => setDialogOpen(true)}
        >
          Add User
        </Button>
      </Box>

      {loading ? (
        <Box sx={{ display: "flex", justifyContent: "center", py: 4 }}>
          <CircularProgress size={24} />
        </Box>
      ) : users.length === 0 ? (
        <Typography variant="body2" color="text.secondary">
          No users found.
        </Typography>
      ) : (
        <DataTable<UserDto>
          columns={userColumns}
          rows={users}
          keyFn={(user) => user.id}
          ariaLabel="Users"
        />
      )}

      <AppDialog
        open={dialogOpen}
        onClose={() => setDialogOpen(false)}
        title="Add User"
        description="Create a new local user account. The user will be required to change their password on first login."
        actionLabel="Create User"
        onAction={handleCreate}
        actionDisabled={!username.trim() || !password}
        actionLoading={saving}
      >
        <Box sx={{ display: "flex", flexDirection: "column", gap: 2 }}>
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
      </AppDialog>

      <ConfirmDeleteDialog
        open={!!deleteTarget}
        title="Delete User"
        message={
          deleteTarget && (deleteTarget.namespaceMembershipCount ?? 0) > 0
            ? `User "${deleteTarget.username}" is a member of ${deleteTarget.namespaceMembershipCount} namespace(s). All memberships will be removed. This action cannot be undone.`
            : `User "${deleteTarget?.username ?? ""}" will be permanently deleted. This action cannot be undone.`
        }
        onConfirm={handleConfirmDelete}
        onCancel={() => setDeleteTarget(null)}
        loading={deleting}
        actionLabel="Delete User"
      />
    </Box>
  );
}
