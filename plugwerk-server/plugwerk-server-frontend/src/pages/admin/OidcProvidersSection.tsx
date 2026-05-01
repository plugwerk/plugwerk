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
  Button,
  Divider,
  CircularProgress,
  Chip,
  Switch,
} from "@mui/material";
import { Pencil, Plus, Trash2 } from "lucide-react";
import { isAxiosError } from "axios";
import { DataTable } from "../../components/common/DataTable";
import type { DataColumn } from "../../components/common/DataTable";
import { ActionIconButton } from "../../components/common/ActionIconButton";
import { oidcProvidersApi } from "../../api/config";
import { useUiStore } from "../../stores/uiStore";
import type {
  OidcProviderCreateRequest,
  OidcProviderDto,
  OidcProviderType,
  OidcProviderUpdateRequest,
} from "../../api/generated/model";
import { OidcProviderFormDialog } from "./OidcProviderFormDialog";

const PROVIDER_TYPE_LABELS: Record<OidcProviderType, string> = {
  OIDC: "Generic OIDC",
  GITHUB: "GitHub",
  GOOGLE: "Google",
  FACEBOOK: "Facebook",
  OAUTH2: "Generic OAuth2",
};

/** Pulls a server-supplied error message out of an Axios error if present. */
function extractServerMessage(err: unknown, fallback: string): string {
  if (
    isAxiosError(err) &&
    typeof err.response?.data === "object" &&
    err.response?.data !== null &&
    "message" in err.response.data
  ) {
    const msg = (err.response.data as { message?: unknown }).message;
    if (typeof msg === "string" && msg.length > 0) return msg;
  }
  return fallback;
}

export function OidcProvidersSection() {
  const [providers, setProviders] = useState<OidcProviderDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [createOpen, setCreateOpen] = useState(false);
  const [editingProvider, setEditingProvider] =
    useState<OidcProviderDto | null>(null);
  const addToast = useUiStore((s) => s.addToast);

  useEffect(() => {
    async function load() {
      setLoading(true);
      try {
        const res = await oidcProvidersApi.listOidcProviders();
        setProviders(res.data);
      } catch {
        setProviders([]);
      } finally {
        setLoading(false);
      }
    }
    load();
  }, []);

  async function handleToggleEnabled(provider: OidcProviderDto) {
    try {
      const res = await oidcProvidersApi.updateOidcProvider({
        providerId: provider.id,
        oidcProviderUpdateRequest: { enabled: !provider.enabled },
      });
      setProviders((prev) =>
        prev.map((p) => (p.id === provider.id ? res.data : p)),
      );
      addToast({
        message: `Provider "${provider.name}" ${res.data.enabled ? "enabled" : "disabled"}.`,
        type: "success",
      });
    } catch (err) {
      addToast({
        message: extractServerMessage(
          err,
          `Failed to update provider "${provider.name}".`,
        ),
        type: "error",
      });
    }
  }

  async function handleDelete(provider: OidcProviderDto) {
    try {
      await oidcProvidersApi.deleteOidcProvider({ providerId: provider.id });
      setProviders((prev) => prev.filter((p) => p.id !== provider.id));
      addToast({
        message: `Provider "${provider.name}" deleted.`,
        type: "success",
      });
    } catch (err) {
      addToast({
        message: extractServerMessage(
          err,
          `Failed to delete provider "${provider.name}".`,
        ),
        type: "error",
      });
    }
  }

  /**
   * Create flow — appends new row on success. No optimistic update because the
   * server assigns the UUID, so we'd have nothing to merge until the response
   * comes back anyway.
   */
  async function handleCreate(payload: OidcProviderCreateRequest) {
    try {
      const res = await oidcProvidersApi.createOidcProvider({
        oidcProviderCreateRequest: payload,
      });
      setProviders((prev) => [...prev, res.data]);
      addToast({
        message: `Provider "${res.data.name}" created.`,
        type: "success",
      });
      setCreateOpen(false);
    } catch (err) {
      addToast({
        message: extractServerMessage(err, "Failed to create OIDC provider."),
        type: "error",
      });
    }
  }

  /**
   * Edit flow with optimistic update — merge the diff into the row before the
   * PATCH returns; on failure restore the previous row state and surface the
   * server's actual error message. `clientSecret` is in the request payload but
   * not in the response DTO, so it stays out of the optimistic merge.
   */
  async function handleEditSubmit(
    provider: OidcProviderDto,
    diff: OidcProviderUpdateRequest,
  ) {
    const previous = provider;
    const { clientSecret: _ignored, ...visibleDiff } = diff;
    const optimistic: OidcProviderDto = { ...provider, ...visibleDiff };
    setProviders((prev) =>
      prev.map((p) => (p.id === provider.id ? optimistic : p)),
    );
    try {
      const res = await oidcProvidersApi.updateOidcProvider({
        providerId: provider.id,
        oidcProviderUpdateRequest: diff,
      });
      setProviders((prev) =>
        prev.map((p) => (p.id === provider.id ? res.data : p)),
      );
      addToast({
        message: `Provider "${res.data.name}" updated.`,
        type: "success",
      });
      setEditingProvider(null);
    } catch (err) {
      setProviders((prev) =>
        prev.map((p) => (p.id === provider.id ? previous : p)),
      );
      addToast({
        message: extractServerMessage(err, "Failed to update provider."),
        type: "error",
      });
      // Re-throw so the form dialog can catch and stay open for the operator
      // to fix the error and retry.
      throw err;
    }
  }

  const oidcColumns: DataColumn<OidcProviderDto>[] = [
    {
      key: "name",
      header: "Name",
      render: (provider) => (
        <Typography variant="body2" fontWeight={500}>
          {provider.name}
        </Typography>
      ),
    },
    {
      key: "type",
      header: "Type",
      render: (provider) => (
        <Chip
          label={
            PROVIDER_TYPE_LABELS[provider.providerType] ?? provider.providerType
          }
          size="small"
        />
      ),
    },
    {
      key: "issuer",
      header: "Issuer / Client ID",
      render: (provider) =>
        provider.issuerUri ? (
          <Typography variant="caption" color="text.secondary">
            {provider.issuerUri}
          </Typography>
        ) : (
          <Typography variant="caption" color="text.disabled">
            {provider.clientId}
          </Typography>
        ),
    },
    {
      key: "enabled",
      header: "Enabled",
      render: (provider) => (
        <Switch
          checked={provider.enabled}
          size="small"
          onChange={() => handleToggleEnabled(provider)}
          inputProps={{ "aria-label": `Toggle ${provider.name}` }}
        />
      ),
    },
    {
      key: "actions",
      header: "",
      align: "right",
      render: (provider) => (
        <Box sx={{ display: "flex", gap: 0.5, justifyContent: "flex-end" }}>
          <ActionIconButton
            icon={Pencil}
            tooltip="Edit"
            onClick={() => setEditingProvider(provider)}
          />
          <ActionIconButton
            icon={Trash2}
            tooltip="Delete"
            color="error"
            onClick={() => handleDelete(provider)}
          />
        </Box>
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
            OIDC Providers
          </Typography>
          <Divider sx={{ mb: 3 }} />
        </Box>
        <Button
          variant="outlined"
          size="small"
          startIcon={<Plus size={14} />}
          onClick={() => setCreateOpen(true)}
        >
          Add Provider
        </Button>
      </Box>

      {loading ? (
        <Box sx={{ display: "flex", justifyContent: "center", py: 4 }}>
          <CircularProgress size={24} />
        </Box>
      ) : providers.length === 0 ? (
        <Typography variant="body2" color="text.secondary">
          No OIDC providers configured.
        </Typography>
      ) : (
        <DataTable<OidcProviderDto>
          columns={oidcColumns}
          rows={providers}
          keyFn={(provider) => provider.id}
          ariaLabel="OIDC providers"
        />
      )}

      <OidcProviderFormDialog
        open={createOpen}
        mode="create"
        onClose={() => setCreateOpen(false)}
        onSubmit={(payload) =>
          handleCreate(payload as OidcProviderCreateRequest)
        }
      />

      <OidcProviderFormDialog
        open={editingProvider !== null}
        mode="edit"
        initialValues={editingProvider ?? undefined}
        onClose={() => setEditingProvider(null)}
        onSubmit={(payload) =>
          handleEditSubmit(
            editingProvider!,
            payload as OidcProviderUpdateRequest,
          )
        }
      />
    </Box>
  );
}
