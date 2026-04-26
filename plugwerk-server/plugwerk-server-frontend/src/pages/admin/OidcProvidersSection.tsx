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
  Select,
  MenuItem,
  FormControl,
  InputLabel,
  CircularProgress,
  Chip,
  Switch,
} from "@mui/material";
import { Plus, Trash2 } from "lucide-react";
import { AppDialog } from "../../components/common/AppDialog";
import { DataTable } from "../../components/common/DataTable";
import type { DataColumn } from "../../components/common/DataTable";
import { ActionIconButton } from "../../components/common/ActionIconButton";
import { oidcProvidersApi } from "../../api/config";
import { useUiStore } from "../../stores/uiStore";
import type {
  OidcProviderDto,
  OidcProviderType,
} from "../../api/generated/model";

const PROVIDER_TYPE_LABELS: Record<string, string> = {
  GENERIC_OIDC: "Generic OIDC",
  KEYCLOAK: "Keycloak",
  GITHUB: "GitHub",
  GOOGLE: "Google",
  FACEBOOK: "Facebook",
};

const ISSUER_REQUIRED_TYPES = new Set(["GENERIC_OIDC", "KEYCLOAK"]);

export function OidcProvidersSection() {
  const [providers, setProviders] = useState<OidcProviderDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [dialogOpen, setDialogOpen] = useState(false);
  const [name, setName] = useState("");
  const [providerType, setProviderType] =
    useState<OidcProviderType>("GENERIC_OIDC");
  const [clientId, setClientId] = useState("");
  const [clientSecret, setClientSecret] = useState("");
  const [issuerUri, setIssuerUri] = useState("");
  const [scope, setScope] = useState("openid profile email");
  const [saving, setSaving] = useState(false);
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
    } catch {
      addToast({
        message: `Failed to update provider "${provider.name}".`,
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
    } catch {
      addToast({
        message: `Failed to delete provider "${provider.name}".`,
        type: "error",
      });
    }
  }

  async function handleCreate() {
    if (!name.trim() || !clientId.trim() || !clientSecret.trim()) return;
    if (ISSUER_REQUIRED_TYPES.has(providerType) && !issuerUri.trim()) return;
    setSaving(true);
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
      });
      setProviders((prev) => [...prev, res.data]);
      addToast({
        message: `Provider "${res.data.name}" created.`,
        type: "success",
      });
      setDialogOpen(false);
      setName("");
      setClientId("");
      setClientSecret("");
      setIssuerUri("");
      setScope("openid profile email");
      setProviderType("GENERIC_OIDC");
    } catch {
      addToast({ message: "Failed to create OIDC provider.", type: "error" });
    } finally {
      setSaving(false);
    }
  }

  const issuerRequired = ISSUER_REQUIRED_TYPES.has(providerType);

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
        <ActionIconButton
          icon={Trash2}
          tooltip="Delete"
          color="error"
          onClick={() => handleDelete(provider)}
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
            OIDC Providers
          </Typography>
          <Divider sx={{ mb: 3 }} />
        </Box>
        <Button
          variant="outlined"
          size="small"
          startIcon={<Plus size={14} />}
          onClick={() => setDialogOpen(true)}
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

      <AppDialog
        open={dialogOpen}
        onClose={() => setDialogOpen(false)}
        title="Add OIDC Provider"
        description="Configure an external identity provider for single sign-on. The provider is disabled by default after creation."
        actionLabel="Create Provider"
        onAction={handleCreate}
        actionDisabled={
          !name.trim() ||
          !clientId.trim() ||
          !clientSecret.trim() ||
          (issuerRequired && !issuerUri.trim())
        }
        actionLoading={saving}
        maxWidth={600}
      >
        <Box sx={{ display: "flex", flexDirection: "column", gap: 2 }}>
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
              onChange={(e) =>
                setProviderType(e.target.value as OidcProviderType)
              }
            >
              {Object.entries(PROVIDER_TYPE_LABELS).map(([value, label]) => (
                <MenuItem key={value} value={value}>
                  {label}
                </MenuItem>
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
      </AppDialog>
    </Box>
  );
}
