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
import { useState, useEffect, useCallback } from "react";
import {
  Box,
  Typography,
  Button,
  Alert,
  CircularProgress,
  Select,
  MenuItem,
  FormControl,
  InputLabel,
  Autocomplete,
  TextField,
} from "@mui/material";
import { Plus, Trash2 } from "lucide-react";
import { AppDialog } from "../../common/AppDialog";
import { DataTable } from "../../common/DataTable";
import type { DataColumn } from "../../common/DataTable";
import { ActionIconButton } from "../../common/ActionIconButton";
import { Timestamp } from "../../common/Timestamp";
import { adminUsersApi, namespaceMembersApi } from "../../../api/config";
import { namespaceRoleKeys } from "../../../api/hooks/useNamespaceRole";
import { useQueryClient } from "@tanstack/react-query";
import { isAxiosError } from "axios";
import type {
  NamespaceMemberDto,
  NamespaceRole,
} from "../../../api/generated/model";
import { NamespaceRole as NamespaceRoleEnum } from "../../../api/generated/model";
import { useUiStore } from "../../../stores/uiStore";

const ROLE_LABELS: Record<string, string> = {
  ADMIN: "Admin",
  MEMBER: "Member",
  READ_ONLY: "Read Only",
};

interface UserOption {
  /** Plugwerk user UUID — submitted to the API. */
  userId: string;
  /** Visible label for the picker. */
  label: string;
}

export function MembersSection({ slug }: { slug: string }) {
  const addToast = useUiStore((s) => s.addToast);
  const queryClient = useQueryClient();
  const [members, setMembers] = useState<NamespaceMemberDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [addOpen, setAddOpen] = useState(false);
  const [newUser, setNewUser] = useState<UserOption | null>(null);
  const [newRole, setNewRole] = useState<NamespaceRole>(
    NamespaceRoleEnum.Member,
  );
  const [addSaving, setAddSaving] = useState(false);
  const [addError, setAddError] = useState<string | null>(null);
  const [userOptions, setUserOptions] = useState<UserOption[]>([]);

  const loadMembers = useCallback(async () => {
    setLoading(true);
    try {
      const res = await namespaceMembersApi.listNamespaceMembers({ ns: slug });
      setMembers(res.data);
    } catch {
      setMembers([]);
    } finally {
      setLoading(false);
    }
  }, [slug]);

  useEffect(() => {
    loadMembers();
  }, [loadMembers]);

  useEffect(() => {
    if (!addOpen) return;
    async function loadUsers() {
      try {
        const res = await adminUsersApi.listUsers({ enabled: true });
        const existing = new Set(members.map((m) => m.userId));
        setUserOptions(
          res.data
            .filter((u) => !u.isSuperadmin && !existing.has(u.id))
            .map((u) => ({
              userId: u.id,
              // Prefer displayName for the picker; fall back to username for
              // local-account-only deployments where displayName === username.
              label:
                u.username && u.username !== u.displayName
                  ? `${u.displayName} (${u.username})`
                  : u.displayName,
            })),
        );
      } catch {
        setUserOptions([]);
      }
    }
    loadUsers();
  }, [addOpen, members]);

  function invalidateRoleCache() {
    // A role change for the current user must be reflected in every
    // useNamespaceRole subscriber (AdminRoute, TopBar, CatalogPage, …) without
    // waiting for staleTime (ADR-0028 / #329).
    queryClient.invalidateQueries({
      queryKey: namespaceRoleKeys.byNamespace(slug),
    });
  }

  async function handleRoleChange(
    member: NamespaceMemberDto,
    role: NamespaceRole,
  ) {
    try {
      const res = await namespaceMembersApi.updateNamespaceMember({
        ns: slug,
        userId: member.userId,
        namespaceMemberUpdateRequest: { role },
      });
      invalidateRoleCache();
      setMembers((prev) =>
        prev.map((m) => (m.userId === member.userId ? res.data : m)),
      );
    } catch {
      addToast({
        message: `Failed to update role for "${member.displayName}".`,
        type: "error",
      });
    }
  }

  async function handleRemove(member: NamespaceMemberDto) {
    try {
      await namespaceMembersApi.removeNamespaceMember({
        ns: slug,
        userId: member.userId,
      });
      invalidateRoleCache();
      setMembers((prev) => prev.filter((m) => m.userId !== member.userId));
      addToast({
        message: `Member "${member.displayName}" removed.`,
        type: "success",
      });
    } catch {
      addToast({
        message: `Failed to remove member "${member.displayName}".`,
        type: "error",
      });
    }
  }

  async function handleAdd() {
    if (!newUser) return;
    setAddSaving(true);
    setAddError(null);
    try {
      const res = await namespaceMembersApi.addNamespaceMember({
        ns: slug,
        namespaceMemberCreateRequest: {
          userId: newUser.userId,
          role: newRole,
        },
      });
      invalidateRoleCache();
      setMembers((prev) => [...prev, res.data]);
      addToast({
        message: `Member "${newUser.label}" added.`,
        type: "success",
      });
      setAddOpen(false);
      setNewUser(null);
      setNewRole(NamespaceRoleEnum.Member);
    } catch (error: unknown) {
      if (isAxiosError(error) && error.response?.status === 409) {
        setAddError(
          error.response.data?.message ??
            `User "${newUser.label}" is already a member of this namespace.`,
        );
      } else {
        setAddError("Failed to add member.");
      }
    } finally {
      setAddSaving(false);
    }
  }

  const memberColumns: DataColumn<NamespaceMemberDto>[] = [
    {
      key: "displayName",
      header: "User",
      render: (member) => (
        <Box>
          <Typography variant="body2" fontWeight={500}>
            {member.displayName}
          </Typography>
          {member.username && member.username !== member.displayName && (
            <Typography variant="caption" color="text.secondary">
              {member.username}
            </Typography>
          )}
        </Box>
      ),
    },
    {
      key: "role",
      header: "Role",
      render: (member) => (
        <Select
          value={member.role}
          size="small"
          variant="standard"
          onChange={(e) =>
            handleRoleChange(member, e.target.value as NamespaceRole)
          }
          sx={{ fontSize: "0.875rem" }}
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
      key: "created",
      header: "Created",
      render: (member) => (
        <Typography variant="caption" color="text.disabled">
          <Timestamp date={member.createdAt} />
        </Typography>
      ),
    },
    {
      key: "actions",
      header: "",
      align: "right",
      render: (member) => (
        <ActionIconButton
          icon={Trash2}
          tooltip="Remove member"
          color="error"
          onClick={() => handleRemove(member)}
        />
      ),
    },
  ];

  return (
    <Box sx={{ display: "flex", flexDirection: "column", gap: 2 }}>
      <Box
        sx={{
          display: "flex",
          alignItems: "center",
          justifyContent: "flex-end",
          mb: 2,
        }}
      >
        <Button
          variant="outlined"
          size="small"
          startIcon={<Plus size={14} />}
          onClick={() => setAddOpen(true)}
        >
          Add Member
        </Button>
      </Box>

      {loading ? (
        <Box sx={{ display: "flex", justifyContent: "center", py: 4 }}>
          <CircularProgress size={24} />
        </Box>
      ) : members.length === 0 ? (
        <Typography variant="body2" color="text.secondary">
          No members found.
        </Typography>
      ) : (
        <DataTable<NamespaceMemberDto>
          columns={memberColumns}
          rows={members}
          keyFn={(member) => member.userId}
          ariaLabel="Namespace members"
        />
      )}

      <AppDialog
        open={addOpen}
        onClose={() => {
          setAddOpen(false);
          setAddError(null);
        }}
        title="Add Member"
        description="Select an existing Plugwerk user to grant a role within this namespace."
        actionLabel="Add Member"
        onAction={handleAdd}
        actionDisabled={!newUser}
        actionLoading={addSaving}
      >
        <Box sx={{ display: "flex", flexDirection: "column", gap: 2 }}>
          {addError && <Alert severity="error">{addError}</Alert>}
          <Autocomplete
            options={userOptions}
            value={newUser}
            onChange={(_, value) => setNewUser(value)}
            getOptionLabel={(option) => option.label}
            isOptionEqualToValue={(a, b) => a.userId === b.userId}
            renderInput={(params) => (
              <TextField {...params} label="User" size="small" />
            )}
          />
          <FormControl size="small" fullWidth>
            <InputLabel id="add-member-role-label">Role</InputLabel>
            <Select
              labelId="add-member-role-label"
              value={newRole}
              label="Role"
              onChange={(e) => setNewRole(e.target.value as NamespaceRole)}
            >
              {Object.values(NamespaceRoleEnum).map((role) => (
                <MenuItem key={role} value={role}>
                  {ROLE_LABELS[role] ?? role}
                </MenuItem>
              ))}
            </Select>
          </FormControl>
        </Box>
      </AppDialog>
    </Box>
  );
}
