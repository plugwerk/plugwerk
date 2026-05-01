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
  createFilterOptions,
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
  UserDto,
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
  /**
   * Free-text search target — `displayName` plus `username`. Decoupled from
   * the visible label so the EXTERNAL provider hint (in parentheses) does
   * not become a filter target. Issue #412 explicitly asks for the hint to
   * be decorative; typing "Google" should not surface every Google user.
   */
  searchKey: string;
}

/**
 * Visible label for a user in the add-member picker. Three branches, in
 * decreasing priority:
 *
 *   - EXTERNAL with a known provider name → "Display Name (Provider)"
 *     (issue #412 — disambiguates two same-named users from different
 *     providers, e.g. a Google "Alice" and a Keycloak "Alice").
 *   - INTERNAL with a username distinct from displayName → existing
 *     "Display Name (username)" fallback for local-account-only deployments.
 *   - Otherwise → bare displayName.
 *
 * Defensive null-checks: an EXTERNAL user without `providerName` (which
 * shouldn't happen given the schema, but the API leaves it nullable) falls
 * through to the bare displayName branch rather than rendering "(null)".
 */
function buildUserOptionLabel(user: UserDto): string {
  if (user.source === "EXTERNAL" && user.providerName) {
    return `${user.displayName} (${user.providerName})`;
  }
  if (user.username && user.username !== user.displayName) {
    return `${user.displayName} (${user.username})`;
  }
  return user.displayName;
}

/**
 * Autocomplete filter that matches the user's typed query against
 * `displayName` + `username` only — never the visible label, which carries
 * the decorative provider-name suffix for EXTERNAL users (issue #412).
 */
const filterUserOptions = createFilterOptions<UserOption>({
  stringify: (option) => option.searchKey,
});

export function MembersSection({ slug }: { slug: string }) {
  const addToast = useUiStore((s) => s.addToast);
  const queryClient = useQueryClient();
  const [members, setMembers] = useState<NamespaceMemberDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [addOpen, setAddOpen] = useState(false);
  // Multi-select: a single Add-Member dialog can grant the same role to any
  // number of users at once. Most realistic case is "I just provisioned 5
  // OIDC users via SSO and want all of them in this namespace as MEMBERs"
  // — no value in clicking through five identical dialogs.
  const [newUsers, setNewUsers] = useState<UserOption[]>([]);
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
              label: buildUserOptionLabel(u),
              searchKey: `${u.displayName} ${u.username ?? ""}`.trim(),
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
    if (newUsers.length === 0) return;
    setAddSaving(true);
    setAddError(null);

    // Sequential adds rather than `Promise.all` — the server's per-row
    // 409 ("already a member") is friendlier to surface as the user sees
    // it, and a hammered loop on a misconfigured server can amplify into
    // dozens of identical 5xx responses. Sequential keeps the UI honest:
    // one row, one outcome, one slot in the result aggregate.
    type AddResult =
      | { kind: "ok"; option: UserOption; member: NamespaceMemberDto }
      | { kind: "fail"; option: UserOption; message: string };
    const results: AddResult[] = [];
    for (const option of newUsers) {
      try {
        const res = await namespaceMembersApi.addNamespaceMember({
          ns: slug,
          namespaceMemberCreateRequest: {
            userId: option.userId,
            role: newRole,
          },
        });
        results.push({ kind: "ok", option, member: res.data });
      } catch (error: unknown) {
        const message =
          isAxiosError(error) && error.response?.status === 409
            ? "already a member of this namespace"
            : "failed to add";
        results.push({ kind: "fail", option, message });
      }
    }

    const succeeded = results.flatMap((r) => (r.kind === "ok" ? [r] : []));
    const failed = results.flatMap((r) => (r.kind === "fail" ? [r] : []));

    if (succeeded.length > 0) {
      invalidateRoleCache();
      setMembers((prev) => [...prev, ...succeeded.map((r) => r.member)]);
      addToast({
        message:
          succeeded.length === 1
            ? `Member "${succeeded[0].option.label}" added.`
            : `${succeeded.length} members added.`,
        type: "success",
      });
    }

    if (failed.length === 0) {
      // Full success → reset and close. Same "fresh dialog every time" UX
      // we had with single-select.
      setAddOpen(false);
      setNewUsers([]);
      setNewRole(NamespaceRoleEnum.Member);
    } else {
      // Partial failure → keep the dialog open, leave only the failed
      // selections in the picker so the operator can see which rows still
      // need attention without losing context. The error banner surfaces
      // the per-row failure reason (typically "already a member").
      setNewUsers(failed.map((r) => r.option));
      setAddError(
        failed.length === 1
          ? `${failed[0].option.label}: ${failed[0].message}.`
          : `${failed.length} of ${results.length} users could not be added — ` +
              failed.map((r) => `${r.option.label} (${r.message})`).join("; "),
      );
    }

    setAddSaving(false);
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
          Add Members
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
          // Reset selection on close so a re-open starts fresh — leaving
          // chips behind from a cancelled flow would be confusing.
          setNewUsers([]);
        }}
        title="Add Members"
        description="Select one or more existing Plugwerk users to grant the same role within this namespace."
        actionLabel={
          newUsers.length > 1 ? `Add ${newUsers.length} Members` : "Add Member"
        }
        onAction={handleAdd}
        actionDisabled={newUsers.length === 0}
        actionLoading={addSaving}
      >
        <Box sx={{ display: "flex", flexDirection: "column", gap: 2 }}>
          {addError && <Alert severity="error">{addError}</Alert>}
          <Autocomplete
            multiple
            options={userOptions}
            value={newUsers}
            onChange={(_, value) => setNewUsers(value)}
            getOptionLabel={(option) => option.label}
            isOptionEqualToValue={(a, b) => a.userId === b.userId}
            filterOptions={filterUserOptions}
            // Keep the option list open after a selection so picking
            // multiple users in a row stays a single fluid interaction.
            disableCloseOnSelect
            renderInput={(params) => (
              <TextField
                {...params}
                label={newUsers.length > 0 ? "Users" : "Add users"}
                size="small"
                placeholder={
                  newUsers.length === 0 ? "Type to search…" : undefined
                }
              />
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
