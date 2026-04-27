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
  Alert,
  Box,
  Checkbox,
  FormControl,
  FormControlLabel,
  InputLabel,
  MenuItem,
  Select,
  TextField,
  Tooltip,
  Typography,
} from "@mui/material";
import { Info } from "lucide-react";
import { AppDialog } from "../../components/common/AppDialog";
import type {
  OidcProviderCreateRequest,
  OidcProviderDto,
  OidcProviderType,
  OidcProviderUpdateRequest,
} from "../../api/generated/model";

const PROVIDER_TYPE_LABELS: Record<OidcProviderType, string> = {
  OIDC: "OIDC (Keycloak, Authentik, Auth0, …)",
  GITHUB: "GitHub",
  GOOGLE: "Google",
  FACEBOOK: "Facebook",
};

const ISSUER_REQUIRED_TYPES: ReadonlySet<OidcProviderType> = new Set(["OIDC"]);

const PROVIDER_TYPE_LOCK_TOOLTIP =
  "Provider type cannot be changed after creation. To switch types, delete and recreate the provider — this invalidates all existing sessions for users on this provider.";

interface OidcProviderFormDialogProps {
  open: boolean;
  mode: "create" | "edit";
  /** Required in edit mode; ignored in create mode. */
  initialValues?: OidcProviderDto;
  onClose: () => void;
  /**
   * Submit handler. In create mode receives a full {@link OidcProviderCreateRequest};
   * in edit mode receives an {@link OidcProviderUpdateRequest} containing only the
   * fields that changed (so unchanged secret stays untransmitted).
   */
  onSubmit: (
    payload: OidcProviderCreateRequest | OidcProviderUpdateRequest,
  ) => Promise<void>;
}

/** Default scope string used when the operator opens a fresh create dialog. */
const DEFAULT_SCOPE = "openid profile email";

/**
 * Creating + editing OIDC providers share enough form fields that one component
 * owns both modes — duplicating would let the two surfaces drift apart over time
 * and is a known source of "edit dialog forgot the field create added last week"
 * bugs.
 *
 * Differences between modes are constrained:
 *   - `providerType` is editable in create, locked-with-tooltip in edit
 *     (changing it would invalidate every previously issued token, see
 *     `PROVIDER_TYPE_LOCK_TOOLTIP`).
 *   - `clientSecret` is required in create, blank-means-keep in edit.
 *   - In edit mode a Client-ID change shows a warning + confirmation gate
 *     because existing access tokens carry the old `aud` claim and will
 *     start failing immediately on save.
 *   - In edit mode the submit payload is the diff (only changed fields).
 */
export function OidcProviderFormDialog({
  open,
  mode,
  initialValues,
  onClose,
  onSubmit,
}: OidcProviderFormDialogProps) {
  const isEdit = mode === "edit";

  // Local form state — re-initialised from `initialValues` on every open so
  // the form does not bleed values across edit→create or edit→edit-different-provider.
  const [name, setName] = useState("");
  const [providerType, setProviderType] = useState<OidcProviderType>("OIDC");
  const [clientId, setClientId] = useState("");
  const [clientSecret, setClientSecret] = useState("");
  const [issuerUri, setIssuerUri] = useState("");
  const [scope, setScope] = useState(DEFAULT_SCOPE);
  const [acknowledgeClientIdChange, setAcknowledgeClientIdChange] =
    useState(false);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (!open) return;
    if (isEdit && initialValues) {
      setName(initialValues.name);
      setProviderType(initialValues.providerType);
      setClientId(initialValues.clientId);
      setIssuerUri(initialValues.issuerUri ?? "");
      setScope(initialValues.scope ?? DEFAULT_SCOPE);
    } else {
      setName("");
      setProviderType("OIDC");
      setClientId("");
      setIssuerUri("");
      setScope(DEFAULT_SCOPE);
    }
    // Secret is never pre-filled — empty IS the affordance ("leave blank to keep").
    setClientSecret("");
    setAcknowledgeClientIdChange(false);
  }, [open, isEdit, initialValues]);

  const issuerRequired = ISSUER_REQUIRED_TYPES.has(providerType);

  // Validation — applied per-field. Errors are surfaced as MUI helperText below
  // each field and as a disabled Save button until they're resolved.
  const errors = useMemo(() => {
    const trimmedName = name.trim();
    const trimmedClientId = clientId.trim();
    const trimmedIssuer = issuerUri.trim();
    const trimmedScope = scope.trim();

    return {
      name: trimmedName.length === 0 ? "Required." : null,
      clientId: trimmedClientId.length === 0 ? "Required." : null,
      clientSecret:
        // Create: required, ≥8. Edit: only validate when the user typed something.
        (!isEdit && clientSecret.length === 0) ||
        (clientSecret.length > 0 && clientSecret.length < 8)
          ? "At least 8 characters."
          : null,
      issuerUri:
        issuerRequired && trimmedIssuer.length === 0
          ? "Required for OIDC providers."
          : trimmedIssuer.length > 0 && !/^https?:\/\//.test(trimmedIssuer)
            ? "Must start with http:// or https://."
            : null,
      scope:
        trimmedScope.length === 0
          ? "Required."
          : providerType === "OIDC" &&
              !trimmedScope.split(/\s+/).includes("openid")
            ? "Scope for OIDC providers must include 'openid'."
            : null,
    };
  }, [
    isEdit,
    clientSecret,
    name,
    clientId,
    issuerUri,
    scope,
    providerType,
    issuerRequired,
  ]);

  const clientIdChanged =
    isEdit &&
    initialValues != null &&
    clientId.trim() !== initialValues.clientId;

  const hasErrors = Object.values(errors).some((e) => e !== null);
  const blockedByClientIdAck = clientIdChanged && !acknowledgeClientIdChange;
  const saveDisabled = hasErrors || blockedByClientIdAck;

  /**
   * Computes the diff between current form state and `initialValues` for an
   * edit submission. Only changed fields are emitted, and `clientSecret` is
   * included only when non-empty (empty == "keep existing"). For create,
   * returns the full request shape.
   */
  function buildPayload():
    | OidcProviderCreateRequest
    | OidcProviderUpdateRequest {
    const trimmedName = name.trim();
    const trimmedClientId = clientId.trim();
    const trimmedIssuer = issuerUri.trim();
    const trimmedScope = scope.trim();

    if (!isEdit || !initialValues) {
      return {
        name: trimmedName,
        providerType,
        clientId: trimmedClientId,
        clientSecret: clientSecret,
        issuerUri: trimmedIssuer.length > 0 ? trimmedIssuer : undefined,
        scope: trimmedScope.length > 0 ? trimmedScope : undefined,
      };
    }

    const diff: OidcProviderUpdateRequest = {};
    if (trimmedName !== initialValues.name) diff.name = trimmedName;
    if (trimmedClientId !== initialValues.clientId)
      diff.clientId = trimmedClientId;
    if (clientSecret.length > 0) diff.clientSecret = clientSecret;
    if (trimmedIssuer !== (initialValues.issuerUri ?? "")) {
      diff.issuerUri = trimmedIssuer;
    }
    if (trimmedScope !== (initialValues.scope ?? "")) diff.scope = trimmedScope;
    return diff;
  }

  async function handleSubmit() {
    if (saveDisabled) return;
    setSubmitting(true);
    try {
      await onSubmit(buildPayload());
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <AppDialog
      open={open}
      onClose={onClose}
      title={
        isEdit
          ? `Edit OIDC Provider${initialValues ? `: ${initialValues.name}` : ""}`
          : "Add OIDC Provider"
      }
      description={
        isEdit
          ? "Patch any subset of fields. Provider type is locked — to switch types, delete and recreate. Leave the client secret blank to keep the current value."
          : "Configure an external identity provider for single sign-on. The provider is disabled by default after creation."
      }
      actionLabel={isEdit ? "Save Changes" : "Create Provider"}
      onAction={handleSubmit}
      actionDisabled={saveDisabled}
      actionLoading={submitting}
      maxWidth={620}
    >
      <Box sx={{ display: "flex", flexDirection: "column", gap: 3 }}>
        {/* ── Section: Identity ── */}
        <FormSection title="Identity">
          <TextField
            label="Display Name"
            value={name}
            onChange={(e) => setName(e.target.value)}
            required
            size="small"
            error={errors.name !== null}
            helperText={errors.name ?? "Shown on the login page."}
            inputProps={{ maxLength: 255 }}
            autoFocus={!isEdit}
          />

          <ProviderTypeField
            value={providerType}
            onChange={setProviderType}
            disabled={isEdit}
          />
        </FormSection>

        {/* ── Section: Credentials ── */}
        <FormSection title="Credentials">
          <TextField
            label="Client ID"
            value={clientId}
            onChange={(e) => setClientId(e.target.value)}
            required
            size="small"
            error={errors.clientId !== null}
            helperText={errors.clientId ?? undefined}
            inputProps={{ maxLength: 255 }}
          />

          {clientIdChanged && (
            <Alert severity="warning" sx={{ mt: -1 }}>
              <Typography variant="body2" sx={{ mb: 1 }}>
                Changing the Client ID invalidates every access token issued by
                this provider — users on this provider will need to re-login.
              </Typography>
              <FormControlLabel
                control={
                  <Checkbox
                    checked={acknowledgeClientIdChange}
                    onChange={(e) =>
                      setAcknowledgeClientIdChange(e.target.checked)
                    }
                    size="small"
                  />
                }
                label="I understand this will end active sessions for users on this provider."
              />
            </Alert>
          )}

          <TextField
            label="Client Secret"
            type="password"
            value={clientSecret}
            onChange={(e) => setClientSecret(e.target.value)}
            required={!isEdit}
            size="small"
            error={errors.clientSecret !== null}
            helperText={
              errors.clientSecret ??
              (isEdit
                ? "Leave blank to keep the current secret."
                : "At least 8 characters.")
            }
            placeholder={
              isEdit ? "Leave blank to keep current secret" : undefined
            }
          />
        </FormSection>

        {/* ── Section: Protocol ── */}
        <FormSection title="Protocol">
          {issuerRequired && (
            <TextField
              label="Issuer URI"
              value={issuerUri}
              onChange={(e) => setIssuerUri(e.target.value)}
              required
              size="small"
              placeholder="https://your-idp.example.com/realms/myrealm"
              error={errors.issuerUri !== null}
              helperText={
                errors.issuerUri ??
                "Discovered via /.well-known/openid-configuration."
              }
            />
          )}
          <TextField
            label="Scope"
            value={scope}
            onChange={(e) => setScope(e.target.value)}
            required
            size="small"
            error={errors.scope !== null}
            helperText={
              errors.scope ??
              "Space-separated. OIDC providers must include 'openid'."
            }
          />
        </FormSection>
      </Box>
    </AppDialog>
  );
}

interface FormSectionProps {
  title: string;
  children: React.ReactNode;
}

/**
 * Subtle section header — gives the form rhythm without dropping a heavy
 * Divider between every field group.
 */
function FormSection({ title, children }: FormSectionProps) {
  return (
    <Box sx={{ display: "flex", flexDirection: "column", gap: 2 }}>
      <Typography
        variant="overline"
        color="text.secondary"
        sx={{ letterSpacing: 0.6, lineHeight: 1 }}
      >
        {title}
      </Typography>
      {children}
    </Box>
  );
}

interface ProviderTypeFieldProps {
  value: OidcProviderType;
  onChange: (value: OidcProviderType) => void;
  disabled: boolean;
}

/**
 * Provider-Type select with the disabled-state wrapped in a Tooltip.
 * MUI Tooltip does not fire on a disabled child, so we wrap in a `<span>`.
 * The Info icon next to the label signals the locked state visually so the
 * disabled appearance does not read as "broken".
 */
function ProviderTypeField({
  value,
  onChange,
  disabled,
}: ProviderTypeFieldProps) {
  const select = (
    <FormControl size="small" required disabled={disabled} fullWidth>
      <InputLabel id="oidc-provider-type-label">Provider Type</InputLabel>
      <Select
        labelId="oidc-provider-type-label"
        value={value}
        label="Provider Type"
        onChange={(e) => onChange(e.target.value as OidcProviderType)}
        aria-describedby={disabled ? "oidc-provider-type-locked" : undefined}
      >
        {(
          Object.entries(PROVIDER_TYPE_LABELS) as [OidcProviderType, string][]
        ).map(([typeValue, label]) => (
          <MenuItem key={typeValue} value={typeValue}>
            {label}
          </MenuItem>
        ))}
      </Select>
    </FormControl>
  );

  if (!disabled) return select;

  return (
    <Box sx={{ display: "flex", alignItems: "center", gap: 1 }}>
      <Tooltip title={PROVIDER_TYPE_LOCK_TOOLTIP} arrow>
        <Box component="span" sx={{ flex: 1 }}>
          {select}
        </Box>
      </Tooltip>
      <Tooltip title={PROVIDER_TYPE_LOCK_TOOLTIP} arrow>
        <Box
          component="span"
          id="oidc-provider-type-locked"
          sx={{
            color: "text.disabled",
            display: "flex",
            alignItems: "center",
            cursor: "help",
          }}
          aria-label="Provider type is locked"
        >
          <Info size={16} />
        </Box>
      </Tooltip>
    </Box>
  );
}
