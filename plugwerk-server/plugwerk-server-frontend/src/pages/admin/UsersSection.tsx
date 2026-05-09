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
  TablePagination,
} from "@mui/material";
import { KeyRound, Plus, Shield, Trash2 } from "lucide-react";
import { AppDialog } from "../../components/common/AppDialog";
import { ConfirmDeleteDialog } from "../../components/common/ConfirmDeleteDialog";
import { DataTable } from "../../components/common/DataTable";
import type { DataColumn } from "../../components/common/DataTable";
import { ActionIconButton } from "../../components/common/ActionIconButton";
import { Timestamp } from "../../components/common/Timestamp";
import { AdminResetLinkDialog } from "../../components/admin/AdminResetLinkDialog";
import { adminUsersApi } from "../../api/config";
import { useUiStore } from "../../stores/uiStore";
import { useAuthStore } from "../../stores/authStore";
import type {
  AdminPasswordResetResponse,
  UserDto,
} from "../../api/generated/model";

// Default page size matches the OpenAPI SizeQuery default and the MUI
// TablePagination convention. The 100 cap mirrors the backend SizeQuery
// max so the dropdown cannot offer values the server would reject.
const DEFAULT_PAGE_SIZE = 20;
const PAGE_SIZE_OPTIONS = [10, 20, 50, 100];
const DEFAULT_SORT = "username,asc";

export function UsersSection() {
  const [users, setUsers] = useState<UserDto[]>([]);
  const [totalElements, setTotalElements] = useState(0);
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(DEFAULT_PAGE_SIZE);
  const [sort] = useState(DEFAULT_SORT);
  const [refreshTrigger, setRefreshTrigger] = useState(0);
  const [loading, setLoading] = useState(true);
  const [dialogOpen, setDialogOpen] = useState(false);
  const [username, setUsername] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [saving, setSaving] = useState(false);
  const addToast = useUiStore((s) => s.addToast);
  const currentUserId = useAuthStore((s) => s.userId);
  const [deleteTarget, setDeleteTarget] = useState<UserDto | null>(null);
  const [deleting, setDeleting] = useState(false);
  const [resetTarget, setResetTarget] = useState<UserDto | null>(null);
  const [resetting, setResetting] = useState(false);
  const [resetLinkResult, setResetLinkResult] = useState<{
    user: UserDto;
    response: AdminPasswordResetResponse;
  } | null>(null);

  // Refetch on every page/size/sort change and after every mutation
  // (refreshTrigger). Keeping the source of truth on the server side avoids
  // the optimistic-cache bugs that the previous "mutate users array in place"
  // code was prone to once the data set spans multiple pages.
  useEffect(() => {
    async function load() {
      setLoading(true);
      try {
        const res = await adminUsersApi.listUsers({ page, size, sort });
        setUsers(res.data.content);
        setTotalElements(res.data.totalElements);
      } catch {
        setUsers([]);
        setTotalElements(0);
      } finally {
        setLoading(false);
      }
    }
    load();
  }, [page, size, sort, refreshTrigger]);

  const refresh = () => setRefreshTrigger((n) => n + 1);

  async function handleToggleEnabled(user: UserDto) {
    try {
      const res = await adminUsersApi.updateUser({
        userId: user.id,
        userUpdateRequest: { enabled: !user.enabled },
      });
      // Optimistic local update is safe — the toggled row stays on the same
      // page. A full refresh would just be wasted bytes.
      setUsers((prev) => prev.map((u) => (u.id === user.id ? res.data : u)));
      addToast({
        message: `User "${user.displayName}" ${res.data.enabled ? "enabled" : "disabled"}.`,
        type: "success",
      });
    } catch {
      addToast({
        message: `Failed to update user ${user.displayName}.`,
        type: "error",
      });
    }
  }

  async function handleConfirmDelete() {
    if (!deleteTarget) return;
    setDeleting(true);
    try {
      await adminUsersApi.deleteUser({ userId: deleteTarget.id });
      // Refetch so the page metadata (totalElements/totalPages) stays
      // consistent and the next page promotes a row into the slot we
      // just freed. Local filter would leave a hole.
      refresh();
      addToast({
        message: `User "${deleteTarget.displayName}" deleted.`,
        type: "success",
      });
      setDeleteTarget(null);
    } catch {
      addToast({
        message: `Failed to delete user ${deleteTarget.displayName}.`,
        type: "error",
      });
    } finally {
      setDeleting(false);
    }
  }

  async function handleConfirmReset() {
    if (!resetTarget) return;
    setResetting(true);
    try {
      const res = await adminUsersApi.adminResetUserPassword({
        userId: resetTarget.id,
      });
      const response: AdminPasswordResetResponse = res.data;
      if (response.emailSent) {
        addToast({
          message: `Reset email sent to ${resetTarget.email}. All sessions revoked.`,
          type: "success",
        });
        setResetTarget(null);
      } else if (response.resetUrl) {
        // SMTP unavailable — surface the link so the operator can deliver
        // it out-of-band. The toast has a `warning` severity to differentiate
        // from the happy-path "everything went fine" message.
        addToast({
          message: `Reset triggered for ${resetTarget.displayName}, but email could not be sent. Copy the link to deliver it manually.`,
          type: "warning",
        });
        setResetLinkResult({ user: resetTarget, response });
        setResetTarget(null);
      } else {
        // Defensive — server returned emailSent=false without a resetUrl;
        // shouldn't happen by API contract but surface it cleanly.
        addToast({
          message: `Reset triggered for ${resetTarget.displayName}, but the email could not be sent and no fallback link was returned.`,
          type: "error",
        });
        setResetTarget(null);
      }
    } catch {
      addToast({
        message: `Failed to reset password for ${resetTarget.displayName}.`,
        type: "error",
      });
    } finally {
      setResetting(false);
    }
  }

  async function handleCreate() {
    if (!username.trim() || !email.trim() || !password) return;
    setSaving(true);
    try {
      const res = await adminUsersApi.createUser({
        userCreateRequest: {
          username: username.trim(),
          email: email.trim(),
          password,
        },
      });
      // Refetch to keep page metadata accurate and let the new user fall
      // into the right slot per the active sort.
      refresh();
      addToast({
        message: `User "${res.data.displayName}" created.`,
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
      key: "displayName",
      header: "User",
      render: (user) => (
        <Box
          sx={{
            display: "flex",
            alignItems: "center",
            gap: 0.5,
            flexWrap: "wrap",
          }}
        >
          <Typography
            variant="body2"
            sx={{
              fontWeight: 500,
            }}
          >
            {user.displayName}
          </Typography>
          {user.source === "EXTERNAL" && user.providerName ? (
            // EXTERNAL: provider name disambiguates two same-named users from
            // different IdPs (e.g. a Google "Alice" and a Keycloak "Alice").
            // Same priority as the namespace member picker (#412) — provider
            // wins over username because for OIDC the username is just the
            // IdP-assigned subject claim and rarely useful at a glance.
            <Typography
              variant="caption"
              sx={{
                color: "text.secondary",
              }}
            >
              ({user.providerName})
            </Typography>
          ) : (
            user.username &&
            user.username !== user.displayName && (
              <Typography
                variant="caption"
                sx={{
                  color: "text.secondary",
                }}
              >
                ({user.username})
              </Typography>
            )
          )}
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
      key: "source",
      header: "Source",
      render: (user) => (
        <Chip
          label={user.source}
          size="small"
          variant="outlined"
          color={user.source === "EXTERNAL" ? "info" : "default"}
        />
      ),
    },
    {
      key: "email",
      header: "Email",
      render: (user) => (
        <Typography
          variant="caption"
          sx={{
            color: "text.secondary",
          }}
        >
          {user.email}
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
        <Typography
          variant="caption"
          sx={{
            color: "text.disabled",
          }}
        >
          <Timestamp date={user.createdAt} />
        </Typography>
      ),
    },
    {
      key: "lastLogin",
      header: "Last login",
      render: (user) => (
        <Typography
          variant="caption"
          sx={{
            color: "text.disabled",
          }}
        >
          <Timestamp date={user.lastLoginAt} variant="relative" />
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
          slotProps={{ input: { "aria-label": `Toggle ${user.displayName}` } }}
        />
      ),
    },
    {
      key: "actions",
      header: "",
      align: "right",
      render: (user) => {
        const isExternal = user.source === "EXTERNAL";
        const isSelf = currentUserId !== null && user.id === currentUserId;
        const resetTooltip = isExternal
          ? `OIDC users reset upstream${
              user.providerName ? ` with ${user.providerName}` : ""
            }`
          : isSelf
            ? "Use Profile → Change password instead"
            : !user.enabled
              ? "Enable the user before resetting their password"
              : "Reset password";
        return (
          <Box
            sx={{
              display: "inline-flex",
              gap: 0.5,
              justifyContent: "flex-end",
            }}
          >
            <ActionIconButton
              icon={KeyRound}
              tooltip={resetTooltip}
              onClick={() => setResetTarget(user)}
              disabled={isExternal || isSelf || !user.enabled}
            />
            <ActionIconButton
              icon={Trash2}
              tooltip="Delete"
              color="error"
              onClick={() => setDeleteTarget(user)}
              disabled={user.isSuperadmin}
            />
          </Box>
        );
      },
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
      ) : users.length === 0 && totalElements === 0 ? (
        <Typography
          variant="body2"
          sx={{
            color: "text.secondary",
          }}
        >
          No users found.
        </Typography>
      ) : (
        <Box>
          <DataTable<UserDto>
            columns={userColumns}
            rows={users}
            keyFn={(user) => user.id}
            ariaLabel="Users"
          />
          <TablePagination
            component="div"
            count={totalElements}
            page={page}
            onPageChange={(_event, newPage) => setPage(newPage)}
            rowsPerPage={size}
            onRowsPerPageChange={(event) => {
              const newSize = parseInt(event.target.value, 10);
              setSize(newSize);
              setPage(0);
            }}
            rowsPerPageOptions={PAGE_SIZE_OPTIONS}
            labelRowsPerPage="Users per page"
          />
        </Box>
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
            // Deliberately no `autoFocus` (issue #405). MUI Dialog's focus-
            // trap relocates focus to the dialog container right after a
            // child autoFocus fires, which counts as a `blur` on the input
            // — any future touched-gated validation would then mark the
            // field as user-interacted before the operator did anything.
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
            ? `User "${deleteTarget.displayName}" is a member of ${deleteTarget.namespaceMembershipCount} namespace(s). All memberships will be removed. This action cannot be undone.`
            : `User "${deleteTarget?.displayName ?? ""}" will be permanently deleted. This action cannot be undone.`
        }
        onConfirm={handleConfirmDelete}
        onCancel={() => setDeleteTarget(null)}
        loading={deleting}
        actionLabel="Delete User"
      />
      <AppDialog
        open={!!resetTarget}
        onClose={() => setResetTarget(null)}
        title="Reset password"
        description={
          resetTarget
            ? `Reset password for "${resetTarget.displayName}"? An email with a single-use reset link will be sent to ${resetTarget.email}. All existing sessions for this user will be revoked immediately, and the user will be required to choose a new password.`
            : ""
        }
        actionLabel="Send reset link"
        onAction={handleConfirmReset}
        actionLoading={resetting}
      />
      {resetLinkResult && (
        <AdminResetLinkDialog
          open={true}
          targetDisplayName={resetLinkResult.user.displayName}
          resetUrl={resetLinkResult.response.resetUrl ?? ""}
          onClose={() => setResetLinkResult(null)}
        />
      )}
    </Box>
  );
}
