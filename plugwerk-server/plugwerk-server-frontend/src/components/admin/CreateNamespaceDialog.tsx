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
import { useEffect, useMemo, useState } from "react";
import {
  Box,
  FormControlLabel,
  Switch,
  TextField,
  Typography,
} from "@mui/material";
import { namespacesApi } from "../../api/config";
import type { NamespaceSummary } from "../../api/generated/model";
import { isAxiosError } from "axios";
import { AppDialog } from "../common/AppDialog";

const SLUG_PATTERN = /^[a-z0-9][a-z0-9-]{0,62}[a-z0-9]$/;

interface CreateNamespaceDialogProps {
  open: boolean;
  onClose: () => void;
  onCreated: (ns: NamespaceSummary) => void;
  onError: (message: string) => void;
}

type FieldKey = "slug" | "name";

const INITIAL_TOUCHED: Record<FieldKey, boolean> = {
  slug: false,
  name: false,
};

export function CreateNamespaceDialog({
  open,
  onClose,
  onCreated,
  onError,
}: CreateNamespaceDialogProps) {
  const [slug, setSlug] = useState("");
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [publicCatalog, setPublicCatalog] = useState(false);
  const [autoApprove, setAutoApprove] = useState(false);
  const [saving, setSaving] = useState(false);
  // Server-side 409 ("namespace already exists") lives in its own state slot
  // so the inline error message remains visible regardless of whether the
  // operator has interacted with the field. The touched/submitAttempted gate
  // below applies only to client-side format / required validation.
  const [slugServerError, setSlugServerError] = useState<string | null>(null);

  // Touched-gate (issue #405). A field's error becomes visible once the
  // operator has either blurred it or attempted submit — not on mount.
  // Without this gate every required field would render red the moment the
  // dialog opens, reading as "validation failed" before the operator typed
  // anything.
  const [touched, setTouched] =
    useState<Record<FieldKey, boolean>>(INITIAL_TOUCHED);
  const [submitAttempted, setSubmitAttempted] = useState(false);
  const markTouched = (key: FieldKey) =>
    setTouched((prev) => (prev[key] ? prev : { ...prev, [key]: true }));

  // Reset interaction state on every (re)open so a freshly-opened dialog is
  // calm rather than carrying touched flags from a prior cancelled flow.
  useEffect(() => {
    if (!open) return;
    setTouched(INITIAL_TOUCHED);
    setSubmitAttempted(false);
    setSlugServerError(null);
  }, [open]);

  // Pure derived validation — every error is computed from current state, no
  // imperative side effects in onChange handlers. Output strings are the
  // text rendered to the operator; visibility is the touched/submit gate's
  // job, not this layer's.
  const errors = useMemo(() => {
    const trimmedSlug = slug.trim();
    const trimmedName = name.trim();
    return {
      slug:
        trimmedSlug.length === 0
          ? "Required."
          : !SLUG_PATTERN.test(trimmedSlug)
            ? "Must be lowercase alphanumeric with hyphens, 2–64 characters."
            : null,
      name: trimmedName.length === 0 ? "Required." : null,
    };
  }, [slug, name]);

  /**
   * Visible-error gate — returns the client validation error only after the
   * field has been blurred or after a submit attempt. Server-side errors
   * (e.g. the 409 conflict on `slug`) bypass this gate and render
   * unconditionally; they are surfaced via their own state slot.
   */
  const visibleError = (key: FieldKey): string | null =>
    touched[key] || submitAttempted ? errors[key] : null;

  const hasErrors = Object.values(errors).some((e) => e !== null);
  const saveDisabled = hasErrors;

  function handleClose() {
    setSlug("");
    setName("");
    setDescription("");
    setPublicCatalog(false);
    setAutoApprove(false);
    setSlugServerError(null);
    setTouched(INITIAL_TOUCHED);
    setSubmitAttempted(false);
    onClose();
  }

  async function handleCreate() {
    // Promote interaction state so any latent errors become visible — a user
    // who clicks Create without focusing every field still gets every
    // relevant red marker.
    setSubmitAttempted(true);
    if (saveDisabled) return;
    setSaving(true);
    setSlugServerError(null);
    try {
      const res = await namespacesApi.createNamespace({
        namespaceCreateRequest: {
          slug: slug.trim(),
          name: name.trim(),
          description: description.trim() || undefined,
          publicCatalog,
          autoApproveReleases: autoApprove,
        },
      });
      onCreated(res.data);
      handleClose();
    } catch (error: unknown) {
      if (isAxiosError(error) && error.response?.status === 409) {
        // Surfaces inline next to the slug field — that is the conflicting
        // value the operator can edit immediately. Stays visible even if
        // the field has not been touched (server-side errors are not
        // gated by interaction).
        setSlugServerError("Namespace already exists.");
      } else {
        onError("Failed to create namespace.");
      }
    } finally {
      setSaving(false);
    }
  }

  // Slug field surfaces server 409 unconditionally (overrides the gated
  // client error so the conflict message is never hidden by the touched
  // gate); when no server error is present the visible client error
  // applies.
  const slugError = slugServerError ?? visibleError("slug");
  const nameError = visibleError("name");

  return (
    <AppDialog
      open={open}
      onClose={handleClose}
      title="Create Namespace"
      description="A namespace groups plugins, members, and API keys under a shared scope."
      actionLabel="Create"
      onAction={handleCreate}
      actionDisabled={saveDisabled}
      actionLoading={saving}
    >
      <Box sx={{ display: "flex", flexDirection: "column", gap: 2 }}>
        <TextField
          label="Slug"
          value={slug}
          onChange={(e) => {
            setSlug(e.target.value);
            // Clear the server-side error as soon as the operator edits the
            // slug — they have acknowledged the conflict by changing the
            // value, the message becomes stale.
            if (slugServerError) setSlugServerError(null);
          }}
          onBlur={() => markTouched("slug")}
          required
          size="small"
          // Deliberately no `autoFocus` — MUI Dialog's focus-trap relocates
          // focus to the dialog container right after a child autoFocus
          // fires, which counts as a `blur` on the input and would mark
          // the field user-touched before the operator did anything.
          error={slugError !== null}
          helperText={
            slugError ?? "Lowercase alphanumeric with hyphens, 2–64 characters."
          }
        />
        <TextField
          label="Name"
          value={name}
          onChange={(e) => setName(e.target.value)}
          onBlur={() => markTouched("name")}
          required
          size="small"
          error={nameError !== null}
          helperText={
            nameError ?? "Human-readable display name for this namespace."
          }
        />
        <TextField
          label="Description"
          value={description}
          onChange={(e) => setDescription(e.target.value)}
          size="small"
          multiline
          minRows={2}
        />
        <Box sx={{ display: "flex", flexDirection: "column", gap: 1.5 }}>
          <FormControlLabel
            control={
              <Switch
                checked={publicCatalog}
                onChange={(e) => setPublicCatalog(e.target.checked)}
                size="small"
              />
            }
            label={
              <Box>
                <Typography variant="body2">Public Catalog</Typography>
                <Typography
                  variant="caption"
                  sx={{
                    color: "text.secondary",
                  }}
                >
                  Allow unauthenticated users to browse the plugin catalog.
                </Typography>
              </Box>
            }
            sx={{ mx: 0, alignItems: "flex-start" }}
          />
          <FormControlLabel
            control={
              <Switch
                checked={autoApprove}
                onChange={(e) => setAutoApprove(e.target.checked)}
                size="small"
              />
            }
            label={
              <Box>
                <Typography variant="body2">Auto-Approve Releases</Typography>
                <Typography
                  variant="caption"
                  sx={{
                    color: "text.secondary",
                  }}
                >
                  Uploaded releases are published immediately without manual
                  review.
                </Typography>
              </Box>
            }
            sx={{ mx: 0, alignItems: "flex-start" }}
          />
        </Box>
      </Box>
    </AppDialog>
  );
}
