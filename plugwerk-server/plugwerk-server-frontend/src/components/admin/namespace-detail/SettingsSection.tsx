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
  Button,
  TextField,
  Switch,
  FormControlLabel,
  CircularProgress,
} from "@mui/material";
import { namespacesApi } from "../../../api/config";
import { useUiStore } from "../../../stores/uiStore";

export function SettingsSection({ slug }: { slug: string }) {
  const addToast = useUiStore((s) => s.addToast);
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [publicCatalog, setPublicCatalog] = useState(false);
  const [autoApprove, setAutoApprove] = useState(false);
  const [saving, setSaving] = useState(false);
  const [loaded, setLoaded] = useState(false);

  useEffect(() => {
    async function load() {
      try {
        const res = await namespacesApi.listNamespaces();
        const ns = res.data.find((n) => n.slug === slug);
        if (ns) {
          setName(ns.name ?? "");
          setDescription(ns.description ?? "");
          setPublicCatalog(ns.publicCatalog ?? false);
          setAutoApprove(ns.autoApproveReleases ?? false);
        }
      } catch {
        /* ignore */
      }
      setLoaded(true);
    }
    load();
  }, [slug]);

  async function handleSave() {
    if (!name.trim()) return;
    setSaving(true);
    try {
      await namespacesApi.updateNamespace({
        ns: slug,
        namespaceUpdateRequest: {
          name: name.trim(),
          description: description.trim() || undefined,
          publicCatalog,
          autoApproveReleases: autoApprove,
        },
      });
      addToast({ message: "Namespace settings saved.", type: "success" });
    } catch {
      addToast({
        message: "Failed to save namespace settings.",
        type: "error",
      });
    } finally {
      setSaving(false);
    }
  }

  if (!loaded) return <CircularProgress size={24} />;

  return (
    <Box sx={{ display: "flex", flexDirection: "column", gap: 3 }}>
      <Box
        sx={{
          display: "grid",
          gridTemplateColumns: { xs: "1fr", sm: "1fr 1fr" },
          gap: 3,
        }}
      >
        <Box sx={{ display: "flex", flexDirection: "column", gap: 2 }}>
          <TextField
            label="Name"
            value={name}
            onChange={(e) => setName(e.target.value)}
            required
            size="small"
            fullWidth
          />
          <TextField
            label="Description"
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            size="small"
            multiline
            minRows={2}
            fullWidth
          />
        </Box>
        <Box sx={{ display: "flex", flexDirection: "column", gap: 1 }}>
          <FormControlLabel
            control={
              <Switch
                checked={publicCatalog}
                onChange={(e) => setPublicCatalog(e.target.checked)}
                size="small"
              />
            }
            label="Public Catalog"
          />
          <FormControlLabel
            control={
              <Switch
                checked={autoApprove}
                onChange={(e) => setAutoApprove(e.target.checked)}
                size="small"
              />
            }
            label="Auto-Approve Releases"
          />
        </Box>
      </Box>
      <Button
        variant="contained"
        onClick={handleSave}
        disabled={saving || !name.trim()}
        sx={{ alignSelf: "flex-start" }}
      >
        {saving ? "Saving\u2026" : "Save"}
      </Button>
    </Box>
  );
}
