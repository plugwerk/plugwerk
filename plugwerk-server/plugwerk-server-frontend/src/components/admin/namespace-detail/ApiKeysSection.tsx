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
  TextField,
  Alert,
  CircularProgress,
  Chip,
} from "@mui/material";
import { Plus, Trash2, Copy, Check } from "lucide-react";
import { AppDialog } from "../../common/AppDialog";
import { DataTable } from "../../common/DataTable";
import type { DataColumn } from "../../common/DataTable";
import { ActionIconButton } from "../../common/ActionIconButton";
import { accessKeysApi } from "../../../api/config";
import { isAxiosError } from "axios";
import type { AccessKeyDto } from "../../../api/generated/model";
import { Timestamp } from "../../common/Timestamp";
import { useUiStore } from "../../../stores/uiStore";

export function ApiKeysSection({ slug }: { slug: string }) {
  const addToast = useUiStore((s) => s.addToast);
  const [keys, setKeys] = useState<AccessKeyDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [createOpen, setCreateOpen] = useState(false);
  const [keyName, setKeyName] = useState("");
  const [expiresAt, setExpiresAt] = useState("");
  const [creating, setCreating] = useState(false);
  const [createError, setCreateError] = useState<string | null>(null);
  const [newKey, setNewKey] = useState<string | null>(null);
  const [copied, setCopied] = useState(false);

  const loadKeys = useCallback(async () => {
    setLoading(true);
    try {
      const res = await accessKeysApi.listAccessKeys({ ns: slug });
      setKeys(res.data);
    } catch {
      setKeys([]);
    } finally {
      setLoading(false);
    }
  }, [slug]);

  useEffect(() => {
    loadKeys();
  }, [loadKeys]);

  async function handleCreate() {
    if (!keyName.trim()) return;
    setCreating(true);
    setCreateError(null);
    try {
      const parsedExpiry = expiresAt
        ? new Date(expiresAt).toISOString()
        : undefined;
      const res = await accessKeysApi.createAccessKey({
        ns: slug,
        accessKeyCreateRequest: {
          name: keyName.trim(),
          expiresAt: parsedExpiry,
        },
      });
      setNewKey(res.data.key);
      setKeyName("");
      setExpiresAt("");
      setCreateOpen(false);
      loadKeys();
    } catch (error: unknown) {
      if (isAxiosError(error) && error.response?.status === 409) {
        setCreateError(
          `An API key named "${keyName.trim()}" already exists in this namespace.`,
        );
      } else {
        const msg = isAxiosError(error)
          ? (error.response?.data?.message ?? error.message)
          : "Failed to create API key.";
        setCreateError(msg);
      }
    } finally {
      setCreating(false);
    }
  }

  async function handleRevoke(keyId: string) {
    try {
      await accessKeysApi.revokeAccessKey({ ns: slug, keyId });
      setKeys((prev) => prev.filter((k) => k.id !== keyId));
      addToast({ message: "API key revoked.", type: "success" });
    } catch {
      addToast({ message: "Failed to revoke API key.", type: "error" });
    }
  }

  function handleCopyKey() {
    if (!newKey) return;
    navigator.clipboard.writeText(newKey);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  }

  const apiKeyColumns: DataColumn<AccessKeyDto>[] = [
    {
      key: "name",
      header: "Name",
      render: (key) => (
        <Typography variant="body2">{key.name || "—"}</Typography>
      ),
    },
    {
      key: "keyPrefix",
      header: "Key Prefix",
      render: (key) => (
        <Typography variant="caption" sx={{ fontFamily: "monospace" }}>
          {key.keyPrefix ?? "—"}
        </Typography>
      ),
    },
    {
      key: "status",
      header: "Status",
      render: (key) => (
        <Chip
          label={key.revoked ? "revoked" : "active"}
          size="small"
          color={key.revoked ? "default" : "success"}
        />
      ),
    },
    {
      key: "expires",
      header: "Expires",
      render: (key) =>
        key.expiresAt ? (
          <Typography variant="caption" sx={{
            color: "text.disabled"
          }}>
            <Timestamp date={key.expiresAt} />
          </Typography>
        ) : (
          <Typography variant="caption" sx={{
            color: "text.disabled"
          }}>
            Never
          </Typography>
        ),
    },
    {
      key: "created",
      header: "Created",
      render: (key) => (
        <Typography variant="caption" sx={{
          color: "text.disabled"
        }}>
          <Timestamp date={key.createdAt} />
        </Typography>
      ),
    },
    {
      key: "actions",
      header: "",
      align: "right",
      render: (key) =>
        !key.revoked ? (
          <ActionIconButton
            icon={Trash2}
            tooltip="Revoke key"
            color="error"
            onClick={() => handleRevoke(key.id)}
          />
        ) : null,
    },
  ];

  return (
    <Box sx={{ display: "flex", flexDirection: "column", gap: 2 }}>
      <Box sx={{ display: "flex", alignItems: "center", gap: 2, mb: 2 }}>
        <Typography
          variant="caption"
          sx={{
            color: "text.secondary",
            flex: 1
          }}>
          API keys provide programmatic access for CI/CD pipelines and the SDK.
          The key is shown only once after creation.
        </Typography>
        <Button
          variant="outlined"
          size="small"
          startIcon={<Plus size={14} />}
          onClick={() => setCreateOpen(true)}
          sx={{ flexShrink: 0 }}
        >
          Generate Key
        </Button>
      </Box>
      {newKey && (
        <Alert
          severity="success"
          action={
            <Button
              size="small"
              startIcon={copied ? <Check size={14} /> : <Copy size={14} />}
              onClick={handleCopyKey}
            >
              {copied ? "Copied" : "Copy"}
            </Button>
          }
          onClose={() => setNewKey(null)}
        >
          <Typography
            variant="caption"
            sx={{ fontFamily: "monospace", wordBreak: "break-all" }}
          >
            {newKey}
          </Typography>
        </Alert>
      )}
      {loading ? (
        <Box sx={{ display: "flex", justifyContent: "center", py: 4 }}>
          <CircularProgress size={24} />
        </Box>
      ) : keys.length === 0 ? (
        <Typography variant="body2" sx={{
          color: "text.secondary"
        }}>
          No API keys configured.
        </Typography>
      ) : (
        <DataTable<AccessKeyDto>
          columns={apiKeyColumns}
          rows={keys}
          keyFn={(key) => key.id}
          ariaLabel="API keys"
          rowSx={(key) => (key.revoked ? { opacity: 0.5 } : undefined)}
        />
      )}
      <AppDialog
        open={createOpen}
        onClose={() => {
          setCreateOpen(false);
          setCreateError(null);
        }}
        title="Generate API Key"
        description="Create a new API key for programmatic access. The key is shown only once after creation."
        actionLabel="Generate Key"
        onAction={handleCreate}
        actionDisabled={!keyName.trim()}
        actionLoading={creating}
      >
        <Box sx={{ display: "flex", flexDirection: "column", gap: 2 }}>
          {createError && <Alert severity="error">{createError}</Alert>}
          <TextField
            label="Name"
            value={keyName}
            onChange={(e) => {
              setKeyName(e.target.value);
              setCreateError(null);
            }}
            size="small"
            required
            // Deliberately no `autoFocus` (issue #405). MUI Dialog's focus-
            // trap turns a child autoFocus into an immediate blur on the
            // input, which would mark the field user-interacted before the
            // operator typed anything once touched-gated validation is
            // added.
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
  );
}
