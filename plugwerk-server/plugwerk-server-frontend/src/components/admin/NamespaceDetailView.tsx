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
  TextField,
  Switch,
  FormControlLabel,
  FormHelperText,
  Chip,
  CircularProgress,
} from "@mui/material";
import {
  ArrowLeft,
  Settings,
  CheckCircle,
  Users,
  KeyRound,
} from "lucide-react";
import { useParams, Link } from "react-router-dom";
import { Section } from "../common/Section";
import { MembersSection } from "./namespace-detail/MembersSection";
import { ApiKeysSection } from "./namespace-detail/ApiKeysSection";
import { namespacesApi } from "../../api/config";
import { useUiStore } from "../../stores/uiStore";
import { tokens } from "../../theme/tokens";

export function NamespaceDetailView() {
  const { slug: slugParam } = useParams<{ slug: string }>();
  const slug = slugParam!;
  const addToast = useUiStore((s) => s.addToast);
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [publicCatalog, setPublicCatalog] = useState(false);
  const [autoApprove, setAutoApprove] = useState(false);
  const [saving, setSaving] = useState(false);
  const [loaded, setLoaded] = useState(false);

  const [initialName, setInitialName] = useState("");
  const [initialDescription, setInitialDescription] = useState("");
  const [initialPublicCatalog, setInitialPublicCatalog] = useState(false);
  const [initialAutoApprove, setInitialAutoApprove] = useState(false);

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
          setInitialName(ns.name ?? "");
          setInitialDescription(ns.description ?? "");
          setInitialPublicCatalog(ns.publicCatalog ?? false);
          setInitialAutoApprove(ns.autoApproveReleases ?? false);
        }
      } catch {
        /* ignore */
      }
      setLoaded(true);
    }
    load();
  }, [slug]);

  const hasChanges =
    name !== initialName ||
    description !== initialDescription ||
    publicCatalog !== initialPublicCatalog ||
    autoApprove !== initialAutoApprove;

  function handleDiscard() {
    setName(initialName);
    setDescription(initialDescription);
    setPublicCatalog(initialPublicCatalog);
    setAutoApprove(initialAutoApprove);
  }

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
      setInitialName(name.trim());
      setInitialDescription(description.trim());
      setInitialPublicCatalog(publicCatalog);
      setInitialAutoApprove(autoApprove);
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

  if (!loaded) {
    return (
      <Box sx={{ display: "flex", alignItems: "center", gap: 1, py: 4 }}>
        <CircularProgress size={18} />
        <Typography variant="body2">Loading namespace…</Typography>
      </Box>
    );
  }

  return (
    <Box sx={{ display: "flex", flexDirection: "column", gap: 3 }}>
      <Box>
        <Button
          component={Link}
          to="/admin/namespaces"
          size="small"
          startIcon={<ArrowLeft size={14} />}
          sx={{ mb: 1 }}
        >
          Back to Namespaces
        </Button>
        <Typography variant="h2" sx={{ mb: 0.5 }}>
          {slug}
        </Typography>
        <Typography variant="body2" color="text.secondary">
          Manage namespace settings, members, and API keys.
        </Typography>
      </Box>

      <Box sx={{ display: "flex", flexDirection: "column", gap: 2.5 }}>
        <Section
          icon={<Settings size={18} />}
          title="General"
          description="Namespace identity and visibility"
          contentGap={2}
        >
          <TextField
            label="Name"
            value={name}
            onChange={(e) => setName(e.target.value)}
            required
            size="small"
            fullWidth
            sx={{ maxWidth: 480 }}
          />
          <TextField
            label="Description"
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            size="small"
            multiline
            minRows={2}
            fullWidth
            sx={{ maxWidth: 480 }}
          />
          <Box sx={{ display: "flex", alignItems: "center", gap: 1.5 }}>
            <Typography
              variant="caption"
              color="text.secondary"
              sx={{ fontWeight: 500 }}
            >
              Slug
            </Typography>
            <Chip
              label={slug}
              size="small"
              variant="outlined"
              sx={{ fontFamily: "monospace", fontSize: "0.8rem" }}
            />
          </Box>
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
        </Section>

        <Section
          icon={<CheckCircle size={18} />}
          title="Review Workflow"
          description="Controls whether releases require manual review before publishing"
        >
          <FormControlLabel
            control={
              <Switch
                checked={autoApprove}
                onChange={(e) => setAutoApprove(e.target.checked)}
                size="small"
              />
            }
            label="Auto-approve releases"
          />
          <FormHelperText sx={{ ml: 0, mt: -0.5 }}>
            When enabled, uploaded releases are published immediately without
            manual review. Disable this for namespaces that require release
            approval from a namespace admin.
          </FormHelperText>
        </Section>

        <Section
          icon={<Users size={18} />}
          title="Members"
          description="Users with access to this namespace"
        >
          <MembersSection slug={slug} />
        </Section>

        <Section
          icon={<KeyRound size={18} />}
          title="Access Keys"
          description="API keys for CI/CD and automation"
        >
          <ApiKeysSection slug={slug} />
        </Section>

        <Box sx={{ display: "flex", justifyContent: "flex-end", gap: 2 }}>
          <Button
            variant="text"
            onClick={handleDiscard}
            disabled={!hasChanges || saving}
            sx={{ borderRadius: tokens.radius.btn }}
          >
            Discard
          </Button>
          <Button
            variant="contained"
            onClick={handleSave}
            disabled={!hasChanges || saving || !name.trim()}
            sx={{ borderRadius: tokens.radius.btn }}
          >
            {saving ? "Saving…" : "Save Changes"}
          </Button>
        </Box>
      </Box>
    </Box>
  );
}
