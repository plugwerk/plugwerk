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
  AlertTitle,
  Box,
  Button,
  Checkbox,
  CircularProgress,
  FormControl,
  FormControlLabel,
  InputAdornment,
  InputLabel,
  Link,
  MenuItem,
  Select,
  Stack,
  TextField,
  Tooltip,
  Typography,
} from "@mui/material";
import { CheckCircle2, Info, Lock, PartyPopper, XCircle } from "lucide-react";
import { AppDialog } from "../../components/common/AppDialog";
import { oidcProvidersApi } from "../../api/config";
import { ProviderCallbackInstructions } from "./ProviderCallbackInstructions";
import type {
  OidcProviderCreateRequest,
  OidcProviderDto,
  OidcProviderType,
  OidcProviderUpdateRequest,
} from "../../api/generated/model";

const PROVIDER_TYPE_LABELS: Record<OidcProviderType, string> = {
  OIDC: "Generic OIDC (Keycloak, Authentik, Auth0, …)",
  GITHUB: "GitHub",
  GOOGLE: "Google",
  FACEBOOK: "Facebook",
  OAUTH2: "Generic OAuth2 (GitLab, Bitbucket, custom IdP, …)",
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
   *
   * Create mode: must resolve with the persisted {@link OidcProviderDto} so the
   * dialog can render the post-create success step (callback URL + setup
   * instructions). Returning `void` from create skips the success step — the
   * dialog stays on the form.
   *
   * Edit mode: return value is ignored.
   */
  onSubmit: (
    payload: OidcProviderCreateRequest | OidcProviderUpdateRequest,
  ) => Promise<OidcProviderDto | void>;
}

/** Default scope string used when the operator opens a fresh create dialog. */
const DEFAULT_SCOPE = "openid profile email";

// User-info attribute defaults — kept in sync with
// GenericOAuth2PrincipalAdapter.DEFAULT_*_ATTRIBUTE on the server. The form
// pre-fills these in create mode so the operator sees the effective value
// rather than guessing from a placeholder; the server-side defaults remain
// in place as defence-in-depth (e.g. a row written via direct SQL or a
// future API path that bypasses this form).
const DEFAULT_SUBJECT_ATTRIBUTE = "sub";
const DEFAULT_EMAIL_ATTRIBUTE = "email";
const DEFAULT_DISPLAY_NAME_ATTRIBUTE = "name";

// Provider types whose `scope` is hardcoded on the server. Mirrors
// `DbClientRegistrationRepository.effectiveScopes` — whatever the operator
// types into the scope field is overwritten with the value below at runtime
// because the OIDC scope vocabulary (openid / profile / email) does not match
// what these providers expect. Showing the field as editable would lie about
// what Plugwerk actually does.
//
// Why each is locked:
//   - GitHub: GitHub's OAuth2 implementation does not understand `openid` /
//     `profile` / `email`. We hardcode `read:user` (powers `/user`) and
//     `user:email` (recovers a private primary email).
//   - Facebook: Facebook expects its own permission set, not OIDC scopes.
//     `public_profile` is granted implicitly on every Facebook OAuth flow
//     and powers `/me`'s `name`; `email` is what Facebook App Review
//     approves and populates `/me`'s `email`.
const LOCKED_SCOPES: Partial<Record<OidcProviderType, string>> = {
  GITHUB: "read:user user:email",
  FACEBOOK: "email public_profile",
};

/**
 * Per-locked-provider explanation surfaced in the helper text. Tells the
 * operator *why* the field is locked rather than just disabling it silently.
 */
const LOCKED_SCOPE_REASONS: Partial<Record<OidcProviderType, string>> = {
  FACEBOOK:
    "Fixed by Facebook — Plugwerk uses these scopes regardless of input " +
    "because Facebook expects its own permission set, not OIDC scopes.",
  GITHUB:
    "Fixed by GitHub — Plugwerk uses these scopes regardless of input " +
    "because GitHub does not understand the OIDC scope vocabulary.",
};

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
 *
 * For OAUTH2 the form additionally surfaces seven operator-supplied
 * fields (authorize/token/user-info/JWK URIs + subject/email/displayName
 * attribute names). They are conditionally rendered — invisible for the four
 * vendor + OIDC types — because they would only confuse operators who are
 * configuring a Google or GitHub login.
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
  // OAUTH2 fields. Empty string means "not set"; the build-payload
  // step turns empty into `undefined` so PATCH semantics survive.
  const [authorizationUri, setAuthorizationUri] = useState("");
  const [tokenUri, setTokenUri] = useState("");
  const [userInfoUri, setUserInfoUri] = useState("");
  const [jwkSetUri, setJwkSetUri] = useState("");
  const [subjectAttribute, setSubjectAttribute] = useState("");
  const [emailAttribute, setEmailAttribute] = useState("");
  const [displayNameAttribute, setDisplayNameAttribute] = useState("");
  const [acknowledgeClientIdChange, setAcknowledgeClientIdChange] =
    useState(false);
  const [submitting, setSubmitting] = useState(false);
  // Post-create success step. Set when `onSubmit` resolves with a DTO in
  // create mode — flips the dialog from "form" to "register the callback URL
  // at your provider" instructions. Stays null in edit mode (callback
  // instructions render inline as part of the form there).
  const [createdProvider, setCreatedProvider] =
    useState<OidcProviderDto | null>(null);

  // Discovery-probe state. The "Test discovery" button next to the Issuer URI
  // (when providerType === OIDC) and the "Try OIDC instead" steering button
  // (when providerType === OAUTH2) both feed into this. Result is a
  // small inline status block so the operator can confirm a working issuer
  // before saving.
  type DiscoveryStatus =
    | { kind: "idle" }
    | { kind: "probing" }
    | {
        kind: "success";
        authorizationUri?: string | null;
        tokenUri?: string | null;
        userInfoUri?: string | null;
        jwkSetUri?: string | null;
      }
    | { kind: "failure"; error: string };
  const [discoveryStatus, setDiscoveryStatus] = useState<DiscoveryStatus>({
    kind: "idle",
  });

  // Validation visibility: a field shows its error only after the user has
  // interacted with it (onBlur) OR after a submit attempt. Without this guard,
  // every field would be red the moment the dialog opens — a known anti-pattern
  // that signals "broken" instead of "please fill in".
  type FieldKey =
    | "name"
    | "clientId"
    | "clientSecret"
    | "issuerUri"
    | "scope"
    | "authorizationUri"
    | "tokenUri"
    | "userInfoUri"
    | "jwkSetUri";
  const initialTouched: Record<FieldKey, boolean> = {
    name: false,
    clientId: false,
    clientSecret: false,
    issuerUri: false,
    scope: false,
    authorizationUri: false,
    tokenUri: false,
    userInfoUri: false,
    jwkSetUri: false,
  };
  const [touched, setTouched] =
    useState<Record<FieldKey, boolean>>(initialTouched);
  const [submitAttempted, setSubmitAttempted] = useState(false);
  const markTouched = (key: FieldKey) =>
    setTouched((prev) => (prev[key] ? prev : { ...prev, [key]: true }));

  useEffect(() => {
    if (!open) return;
    if (isEdit && initialValues) {
      setName(initialValues.name);
      setProviderType(initialValues.providerType);
      setClientId(initialValues.clientId);
      setIssuerUri(initialValues.issuerUri ?? "");
      setScope(initialValues.scope ?? DEFAULT_SCOPE);
      setAuthorizationUri(initialValues.authorizationUri ?? "");
      setTokenUri(initialValues.tokenUri ?? "");
      setUserInfoUri(initialValues.userInfoUri ?? "");
      setJwkSetUri(initialValues.jwkSetUri ?? "");
      setSubjectAttribute(initialValues.subjectAttribute ?? "");
      setEmailAttribute(initialValues.emailAttribute ?? "");
      setDisplayNameAttribute(initialValues.displayNameAttribute ?? "");
    } else {
      setName("");
      setProviderType("OIDC");
      setClientId("");
      setIssuerUri("");
      setScope(DEFAULT_SCOPE);
      setAuthorizationUri("");
      setTokenUri("");
      setUserInfoUri("");
      setJwkSetUri("");
      // Attribute names: pre-fill with the OIDC convention so the operator
      // sees what the server will actually use, instead of guessing from a
      // placeholder. Custom providers (GitLab, Bitbucket) override; standard
      // providers leave them and the values get persisted explicitly.
      setSubjectAttribute(DEFAULT_SUBJECT_ATTRIBUTE);
      setEmailAttribute(DEFAULT_EMAIL_ATTRIBUTE);
      setDisplayNameAttribute(DEFAULT_DISPLAY_NAME_ATTRIBUTE);
    }
    // Secret is never pre-filled — empty IS the affordance ("leave blank to keep").
    setClientSecret("");
    setAcknowledgeClientIdChange(false);
    // Reset interaction state so a freshly-opened dialog is clean. The next
    // open of "Add Provider" should not inherit the touched/submit state of
    // the previous one.
    setTouched(initialTouched);
    setSubmitAttempted(false);
    setDiscoveryStatus({ kind: "idle" });
    setCreatedProvider(null);
    // initialTouched is a fresh object literal each render — including it in
    // the dep list would re-run the effect on every render. Stable reset is
    // the goal here, not reactive sync.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, isEdit, initialValues]);

  const issuerRequired = ISSUER_REQUIRED_TYPES.has(providerType);
  const isGeneric = providerType === "OAUTH2";
  const lockedScope = LOCKED_SCOPES[providerType];
  const isScopeLocked = lockedScope !== undefined;
  // The actual value rendered + submitted: the locked value when one is
  // defined, otherwise the operator's free-form input. A user editing a
  // GitHub provider that was created with a stale scope still sees the
  // locked value here — the form does not lie about what the server uses.
  const effectiveScope = lockedScope ?? scope;

  // Validation — applied per-field. Errors are surfaced as MUI helperText below
  // each field and as a disabled Save button until they're resolved.
  const errors = useMemo(() => {
    const trimmedName = name.trim();
    const trimmedClientId = clientId.trim();
    const trimmedIssuer = issuerUri.trim();
    const trimmedScope = effectiveScope.trim();
    const trimmedAuthUri = authorizationUri.trim();
    const trimmedTokenUri = tokenUri.trim();
    const trimmedUserInfoUri = userInfoUri.trim();
    const trimmedJwkSetUri = jwkSetUri.trim();

    const httpUriError = (value: string): string | null =>
      value.length > 0 && !/^https?:\/\//.test(value)
        ? "Must start with http:// or https://."
        : null;

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
          ? "Required for Generic OIDC providers."
          : trimmedIssuer.length > 0 && !/^https?:\/\//.test(trimmedIssuer)
            ? "Must start with http:// or https://."
            : null,
      scope: isScopeLocked
        ? null
        : trimmedScope.length === 0
          ? "Required."
          : providerType === "OIDC" &&
              !trimmedScope.split(/\s+/).includes("openid")
            ? "Scope for Generic OIDC providers must include 'openid'."
            : null,
      authorizationUri: isGeneric
        ? trimmedAuthUri.length === 0
          ? "Required for Generic OAuth2."
          : httpUriError(trimmedAuthUri)
        : null,
      tokenUri: isGeneric
        ? trimmedTokenUri.length === 0
          ? "Required for Generic OAuth2."
          : httpUriError(trimmedTokenUri)
        : null,
      userInfoUri: isGeneric
        ? trimmedUserInfoUri.length === 0
          ? "Required for Generic OAuth2."
          : httpUriError(trimmedUserInfoUri)
        : null,
      jwkSetUri: isGeneric ? httpUriError(trimmedJwkSetUri) : null,
    };
  }, [
    isEdit,
    clientSecret,
    name,
    clientId,
    issuerUri,
    effectiveScope,
    providerType,
    issuerRequired,
    isGeneric,
    isScopeLocked,
    authorizationUri,
    tokenUri,
    userInfoUri,
    jwkSetUri,
  ]);

  const clientIdChanged =
    isEdit &&
    initialValues != null &&
    clientId.trim() !== initialValues.clientId;

  const hasErrors = Object.values(errors).some((e) => e !== null);
  const blockedByClientIdAck = clientIdChanged && !acknowledgeClientIdChange;
  const saveDisabled = hasErrors || blockedByClientIdAck;

  /**
   * Visible-error gate — returns the error text only after the user has
   * either interacted with the field (onBlur) or attempted a submit. Until
   * then, fields render in their neutral state and show their default
   * helperText, so a freshly-opened dialog is calm rather than red.
   */
  const visibleError = (key: FieldKey): string | null =>
    touched[key] || submitAttempted ? errors[key] : null;

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
    const trimmedScope = effectiveScope.trim();
    const trimmedAuthUri = authorizationUri.trim();
    const trimmedTokenUri = tokenUri.trim();
    const trimmedUserInfoUri = userInfoUri.trim();
    const trimmedJwkSetUri = jwkSetUri.trim();
    const trimmedSubjectAttr = subjectAttribute.trim();
    const trimmedEmailAttr = emailAttribute.trim();
    const trimmedDisplayNameAttr = displayNameAttribute.trim();

    if (!isEdit || !initialValues) {
      return {
        name: trimmedName,
        providerType,
        clientId: trimmedClientId,
        clientSecret: clientSecret,
        issuerUri: trimmedIssuer.length > 0 ? trimmedIssuer : undefined,
        scope: trimmedScope.length > 0 ? trimmedScope : undefined,
        // OAUTH2 fields: send only when the user actually entered them.
        // Server enforces required-when-OAUTH2 for the three core URIs.
        authorizationUri:
          trimmedAuthUri.length > 0 ? trimmedAuthUri : undefined,
        tokenUri: trimmedTokenUri.length > 0 ? trimmedTokenUri : undefined,
        userInfoUri:
          trimmedUserInfoUri.length > 0 ? trimmedUserInfoUri : undefined,
        jwkSetUri: trimmedJwkSetUri.length > 0 ? trimmedJwkSetUri : undefined,
        subjectAttribute:
          trimmedSubjectAttr.length > 0 ? trimmedSubjectAttr : undefined,
        emailAttribute:
          trimmedEmailAttr.length > 0 ? trimmedEmailAttr : undefined,
        displayNameAttribute:
          trimmedDisplayNameAttr.length > 0
            ? trimmedDisplayNameAttr
            : undefined,
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
    // OAUTH2 field diffing — non-empty current vs initial, emit when
    // they differ. Service-layer rejects clearing the three core URIs back to
    // null on a OAUTH2 row, so we skip the empty-to-null transition
    // here (the user intent for "remove" is delete-and-recreate).
    if (
      trimmedAuthUri.length > 0 &&
      trimmedAuthUri !== (initialValues.authorizationUri ?? "")
    ) {
      diff.authorizationUri = trimmedAuthUri;
    }
    if (
      trimmedTokenUri.length > 0 &&
      trimmedTokenUri !== (initialValues.tokenUri ?? "")
    ) {
      diff.tokenUri = trimmedTokenUri;
    }
    if (
      trimmedUserInfoUri.length > 0 &&
      trimmedUserInfoUri !== (initialValues.userInfoUri ?? "")
    ) {
      diff.userInfoUri = trimmedUserInfoUri;
    }
    if (
      trimmedJwkSetUri.length > 0 &&
      trimmedJwkSetUri !== (initialValues.jwkSetUri ?? "")
    ) {
      diff.jwkSetUri = trimmedJwkSetUri;
    }
    if (
      trimmedSubjectAttr.length > 0 &&
      trimmedSubjectAttr !== (initialValues.subjectAttribute ?? "")
    ) {
      diff.subjectAttribute = trimmedSubjectAttr;
    }
    if (
      trimmedEmailAttr.length > 0 &&
      trimmedEmailAttr !== (initialValues.emailAttribute ?? "")
    ) {
      diff.emailAttribute = trimmedEmailAttr;
    }
    if (
      trimmedDisplayNameAttr.length > 0 &&
      trimmedDisplayNameAttr !== (initialValues.displayNameAttribute ?? "")
    ) {
      diff.displayNameAttribute = trimmedDisplayNameAttr;
    }
    return diff;
  }

  /**
   * Runs the OIDC discovery probe against the current Issuer URI. Two entry
   * points share this:
   *   1. "Test discovery" button while OIDC is selected — confirms a candidate
   *      issuer URI before saving.
   *   2. "Try OIDC instead" button on the OAUTH2 steering banner —
   *      switches the form to OIDC and probes whatever the operator already
   *      pasted into the Authorization URI field (best-guess starting point).
   *
   * Auto-fills `authorizationUri` / `tokenUri` / `userInfoUri` / `jwkSetUri`
   * on success so a follow-up "switch back to GENERIC" preserves the work.
   */
  async function probeDiscovery(candidateIssuerUri: string) {
    const trimmed = candidateIssuerUri.trim();
    if (trimmed.length === 0) {
      setDiscoveryStatus({
        kind: "failure",
        error: "Enter an issuer URI first.",
      });
      return;
    }
    setDiscoveryStatus({ kind: "probing" });
    try {
      const response = await oidcProvidersApi.discoverOidcEndpoints({
        oidcDiscoveryRequest: { issuerUri: trimmed },
      });
      const data = response.data;
      if (data.success) {
        setDiscoveryStatus({
          kind: "success",
          authorizationUri: data.authorizationUri,
          tokenUri: data.tokenUri,
          userInfoUri: data.userInfoUri,
          jwkSetUri: data.jwkSetUri,
        });
        // Pre-fill the GENERIC fields too. Even if the operator stays on OIDC
        // the values are inert (those fields only render for GENERIC), but if
        // they switch they get a head-start.
        if (data.authorizationUri) setAuthorizationUri(data.authorizationUri);
        if (data.tokenUri) setTokenUri(data.tokenUri);
        if (data.userInfoUri) setUserInfoUri(data.userInfoUri);
        if (data.jwkSetUri) setJwkSetUri(data.jwkSetUri);
      } else {
        setDiscoveryStatus({
          kind: "failure",
          error: data.error ?? "Discovery failed without a message.",
        });
      }
    } catch (err) {
      setDiscoveryStatus({
        kind: "failure",
        error: err instanceof Error ? err.message : "Network error.",
      });
    }
  }

  async function handleSubmit() {
    // Promote interaction state so any latent errors become visible — a user
    // who jumps straight to "Save" without focusing every field still gets
    // every relevant red marker.
    setSubmitAttempted(true);
    if (saveDisabled) return;
    setSubmitting(true);
    try {
      const result = await onSubmit(buildPayload());
      // In create mode, the parent returns the persisted provider so we can
      // render the callback-URL success step instead of closing immediately.
      // The operator's job is not done after Save — they still have to
      // register that URL at the upstream IdP — and the dialog is the only
      // place we have their attention for that information.
      if (!isEdit && result) {
        setCreatedProvider(result);
      }
    } finally {
      setSubmitting(false);
    }
  }

  // Three rendering modes layered on the same dialog shell:
  //   - edit         → the existing form, with a callback-URL reference card.
  //   - create-form  → the existing form, no callback URL yet (no UUID).
  //   - create-success → the form is replaced by a "you're nearly done"
  //                      success step that surfaces the callback URL the
  //                      operator now needs to paste at their IdP.
  const isSuccessStep = !isEdit && createdProvider !== null;

  return (
    <AppDialog
      open={open}
      onClose={onClose}
      title={
        isSuccessStep
          ? "Provider created — one step left"
          : isEdit
            ? `Edit OIDC Provider${initialValues ? `: ${initialValues.name}` : ""}`
            : "Add OIDC Provider"
      }
      description={
        isSuccessStep
          ? `"${createdProvider!.name}" is configured in Plugwerk. To finish setup, register the callback URL below at your identity provider.`
          : isEdit
            ? "Patch any subset of fields. Provider type is locked — to switch types, delete and recreate. Leave the client secret blank to keep the current value."
            : "Configure an external identity provider for single sign-on. The provider is disabled by default after creation."
      }
      actionLabel={
        isSuccessStep ? "Done" : isEdit ? "Save Changes" : "Create Provider"
      }
      onAction={isSuccessStep ? onClose : handleSubmit}
      actionDisabled={isSuccessStep ? false : saveDisabled}
      actionLoading={isSuccessStep ? false : submitting}
      cancelLabel={isSuccessStep ? "Close" : "Cancel"}
      maxWidth={isSuccessStep ? 560 : 620}
    >
      {isSuccessStep ? (
        <CreateSuccessStep provider={createdProvider!} />
      ) : (
        <Box
          sx={{
            display: "flex",
            flexDirection: "column",
            gap: 3,
            // Mute the MUI default required-asterisk colour. Default is
            // `theme.palette.error.main`, which reads as "validation error" on a
            // form that has not been touched yet — exactly the false alarm the
            // touched/submitAttempted gate above is meant to avoid. A required
            // asterisk in `text.secondary` still signals "required" without
            // shouting "broken".
            "& .MuiFormLabel-asterisk": { color: "text.secondary" },
          }}
        >
          {/* ── Section: Identity ── */}
          <FormSection title="Identity">
            <TextField
              label="Display Name"
              value={name}
              onChange={(e) => setName(e.target.value)}
              onBlur={() => markTouched("name")}
              required
              size="small"
              error={visibleError("name") !== null}
              helperText={visibleError("name") ?? "Shown on the login page."}
              inputProps={{ maxLength: 255 }}
              // Deliberately no `autoFocus`. Combining `autoFocus` with MUI's
              // Dialog focus-trap caused the input to fire `onBlur` immediately
              // after mount (focus enters the input, then the trap relocates
              // focus to the dialog container, which counts as a blur). The
              // touched-gate above then flagged the field as user-interacted
              // before the operator did anything, painting it red on open.
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
              onBlur={() => markTouched("clientId")}
              required
              size="small"
              error={visibleError("clientId") !== null}
              helperText={visibleError("clientId") ?? undefined}
              inputProps={{ maxLength: 255 }}
            />

            {clientIdChanged && (
              <Alert severity="warning" sx={{ mt: -1 }}>
                <Typography variant="body2" sx={{ mb: 1 }}>
                  Changing the Client ID invalidates every access token issued
                  by this provider — users on this provider will need to
                  re-login.
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
              onBlur={() => markTouched("clientSecret")}
              required={!isEdit}
              size="small"
              error={visibleError("clientSecret") !== null}
              helperText={
                visibleError("clientSecret") ??
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
              <Stack direction="row" spacing={1} sx={{
                alignItems: "flex-start"
              }}>
                <TextField
                  label="Issuer URI"
                  value={issuerUri}
                  onChange={(e) => {
                    setIssuerUri(e.target.value);
                    // Issuer changes invalidate any prior probe result — clear
                    // the banner so the operator does not act on stale info.
                    if (discoveryStatus.kind !== "idle") {
                      setDiscoveryStatus({ kind: "idle" });
                    }
                  }}
                  onBlur={() => markTouched("issuerUri")}
                  required
                  size="small"
                  fullWidth
                  placeholder="https://your-idp.example.com/realms/myrealm"
                  error={visibleError("issuerUri") !== null}
                  helperText={
                    visibleError("issuerUri") ??
                    "Discovered via /.well-known/openid-configuration."
                  }
                />
                <Button
                  variant="outlined"
                  size="small"
                  onClick={() => probeDiscovery(issuerUri)}
                  disabled={
                    discoveryStatus.kind === "probing" ||
                    issuerUri.trim().length === 0
                  }
                  sx={{ mt: 0.25, whiteSpace: "nowrap" }}
                >
                  {discoveryStatus.kind === "probing" ? (
                    <CircularProgress size={16} sx={{ mr: 1 }} />
                  ) : null}
                  Test discovery
                </Button>
              </Stack>
            )}
            {issuerRequired && (
              <DiscoveryStatusBanner status={discoveryStatus} />
            )}
            <TextField
              label="Scope"
              value={effectiveScope}
              onChange={(e) => {
                if (isScopeLocked) return;
                setScope(e.target.value);
              }}
              onBlur={() => markTouched("scope")}
              required={!isScopeLocked}
              size="small"
              disabled={isScopeLocked}
              error={visibleError("scope") !== null}
              helperText={
                visibleError("scope") ??
                (isScopeLocked
                  ? LOCKED_SCOPE_REASONS[providerType]
                  : "Space-separated. OIDC providers must include 'openid'.")
              }
              slotProps={{
                input: {
                  endAdornment: isScopeLocked ? (
                    <InputAdornment position="end">
                      <Tooltip
                        title="Locked — provider-specific scopes are fixed by the upstream"
                        arrow
                      >
                        <Box
                          component="span"
                          sx={{
                            display: "inline-flex",
                            alignItems: "center",
                            color: "text.disabled",
                            cursor: "help",
                          }}
                          aria-label="Scope is locked"
                        >
                          <Lock size={14} />
                        </Box>
                      </Tooltip>
                    </InputAdornment>
                  ) : undefined,
                },
              }}
              sx={{
                // Soften the disabled appearance so the field reads as
                // "informationally fixed", not "broken / unavailable". MUI's
                // default disabled treatment uses very low contrast which
                // hides the actual scope value the operator wants to see.
                ...(isScopeLocked && {
                  "& .MuiInputBase-input.Mui-disabled": {
                    WebkitTextFillColor: "var(--mui-palette-text-secondary)",
                    color: "text.secondary",
                  },
                  "& .MuiOutlinedInput-root.Mui-disabled .MuiOutlinedInput-notchedOutline":
                    {
                      borderStyle: "dashed",
                    },
                }),
              }}
            />
          </FormSection>

          {/* ── Section: Generic OAuth2 endpoints ── */}
          {isGeneric && (
            <FormSection title="Generic OAuth2 Endpoints">
              <Alert severity="info" sx={{ mb: 1 }}>
                <AlertTitle>Try Generic OIDC first</AlertTitle>
                Most modern providers (Keycloak, Authentik, Auth0, Google,
                GitLab, …) publish a discovery URL — picking{" "}
                <strong>Generic OIDC</strong> instead lets you configure them
                with one field instead of four.{" "}
                <Link
                  component="button"
                  type="button"
                  onClick={() => {
                    setProviderType("OIDC");
                    // Pre-seed the issuer URI from authorize/userInfo URI host
                    // when the operator has already typed something in those
                    // fields — common case is "I copied the authorize URL".
                    // The probe will tell them whether discovery actually works.
                    if (issuerUri.trim().length === 0) {
                      const seed = authorizationUri || userInfoUri || tokenUri;
                      try {
                        if (seed) {
                          const parsed = new URL(seed);
                          setIssuerUri(`${parsed.protocol}//${parsed.host}`);
                        }
                      } catch {
                        // Malformed URL → leave issuer blank, operator will type it.
                      }
                    }
                  }}
                >
                  Switch to Generic OIDC
                </Link>
                . Continue here only if your provider does not have OIDC
                discovery (Bitbucket, some custom enterprise IdPs).
              </Alert>
              <TextField
                label="Authorization URI"
                value={authorizationUri}
                onChange={(e) => setAuthorizationUri(e.target.value)}
                onBlur={() => markTouched("authorizationUri")}
                required
                size="small"
                placeholder="https://idp.example/oauth/authorize"
                error={visibleError("authorizationUri") !== null}
                helperText={
                  visibleError("authorizationUri") ??
                  "Where the browser is redirected to start the login flow."
                }
              />
              <TextField
                label="Token URI"
                value={tokenUri}
                onChange={(e) => setTokenUri(e.target.value)}
                onBlur={() => markTouched("tokenUri")}
                required
                size="small"
                placeholder="https://idp.example/oauth/token"
                error={visibleError("tokenUri") !== null}
                helperText={
                  visibleError("tokenUri") ??
                  "Where the server exchanges the authorization code for an access token."
                }
              />
              <TextField
                label="User-Info URI"
                value={userInfoUri}
                onChange={(e) => setUserInfoUri(e.target.value)}
                onBlur={() => markTouched("userInfoUri")}
                required
                size="small"
                placeholder="https://idp.example/api/me"
                error={visibleError("userInfoUri") !== null}
                helperText={
                  visibleError("userInfoUri") ??
                  "Where the server reads subject / email / display-name attributes."
                }
              />
              <TextField
                label="JWK Set URI (optional)"
                value={jwkSetUri}
                onChange={(e) => setJwkSetUri(e.target.value)}
                onBlur={() => markTouched("jwkSetUri")}
                size="small"
                placeholder="https://idp.example/.well-known/jwks.json"
                error={visibleError("jwkSetUri") !== null}
                helperText={
                  visibleError("jwkSetUri") ??
                  "Only needed if this provider issues JWT access tokens that Plugwerk validates."
                }
              />
            </FormSection>
          )}

          {/* ── Section: Generic OAuth2 attribute mapping ── */}
          {isGeneric && (
            <FormSection title="User-Info Attribute Mapping">
              <Alert severity="info" sx={{ mb: 1 }}>
                Names of the JSON keys the user-info response uses. Pre-filled
                with the OIDC convention — override if your provider uses
                different keys (GitLab uses <code>username</code> for the
                handle, for example).
              </Alert>
              <TextField
                label="Subject Attribute"
                value={subjectAttribute}
                onChange={(e) => setSubjectAttribute(e.target.value)}
                size="small"
                helperText="Stable user identifier returned by the user-info endpoint."
              />
              <TextField
                label="Email Attribute"
                value={emailAttribute}
                onChange={(e) => setEmailAttribute(e.target.value)}
                size="small"
                helperText="JSON key carrying the user's email address."
              />
              <TextField
                label="Display Name Attribute"
                value={displayNameAttribute}
                onChange={(e) => setDisplayNameAttribute(e.target.value)}
                size="small"
                helperText="JSON key carrying a human-readable display name."
              />
            </FormSection>
          )}

          {/* ── Section: Provider Setup (edit mode reference) ── */}
          {/*
           * Permanent callback-URL reference card for already-existing providers.
           * Operators come back to this dialog months after creating a provider
           * with no recollection of which URL they registered upstream — having
           * the URL one click away here saves a round-trip through the entity
           * UUID and the redirect-URI template.
           */}
          {isEdit && initialValues && (
            <FormSection title="Provider Setup">
              <ProviderCallbackInstructions
                providerId={initialValues.id}
                providerType={initialValues.providerType}
                variant="inline"
              />
            </FormSection>
          )}
        </Box>
      )}
    </AppDialog>
  );
}

interface CreateSuccessStepProps {
  provider: OidcProviderDto;
}

/**
 * Post-create success step — replaces the form once the provider row has been
 * persisted. The composition is intentional: the callback URL panel is the
 * hero of the dialog at this moment because that URL is the one piece of
 * information the operator needs to act on next, and missing it is the
 * single most common cause of a broken first login.
 */
function CreateSuccessStep({ provider }: CreateSuccessStepProps) {
  return (
    <Box sx={{ display: "flex", flexDirection: "column", gap: 2.5 }}>
      <Box
        sx={{
          display: "flex",
          alignItems: "center",
          gap: 1.5,
          color: "success.main",
        }}
      >
        <PartyPopper size={22} />
        <Typography variant="subtitle1" sx={{ fontWeight: 600 }}>
          Plugwerk side: complete
        </Typography>
      </Box>
      <Typography variant="body2" sx={{
        color: "text.secondary"
      }}>
        Your provider is registered and disabled. Once the upstream callback URL
        is set up, enable the provider from the providers list to start
        accepting logins.
      </Typography>
      <ProviderCallbackInstructions
        providerId={provider.id}
        providerType={provider.providerType}
        variant="success"
      />
      <Box
        sx={{
          mt: 0.5,
          p: 1.75,
          borderRadius: 1.25,
          border: 1,
          borderColor: "divider",
          bgcolor: "background.paper",
        }}
      >
        <Typography
          variant="overline"
          sx={{
            display: "block",
            color: "text.secondary",
            fontWeight: 600,
            letterSpacing: 0.6,
            mb: 0.5,
          }}
        >
          Next steps
        </Typography>
        <Box component="ol" sx={{ pl: 2.5, m: 0, "& li": { mb: 0.5 } }}>
          <Typography component="li" variant="body2">
            Copy the callback URL above and add it to your provider's authorized
            redirect URIs.
          </Typography>
          <Typography component="li" variant="body2">
            Come back to the providers list and enable{" "}
            <Box component="strong" sx={{ fontWeight: 600 }}>
              {provider.name}
            </Box>{" "}
            with the toggle.
          </Typography>
          <Typography component="li" variant="body2">
            Sign out and try the new provider button on the login page.
          </Typography>
        </Box>
      </Box>
    </Box>
  );
}

interface DiscoveryStatusBannerProps {
  status:
    | { kind: "idle" }
    | { kind: "probing" }
    | {
        kind: "success";
        authorizationUri?: string | null;
        tokenUri?: string | null;
        userInfoUri?: string | null;
        jwkSetUri?: string | null;
      }
    | { kind: "failure"; error: string };
}

/**
 * Inline result block for the "Test discovery" button. Idle and probing render
 * nothing visible (the spinner sits inside the button); success / failure
 * surface as a small Alert. Operators get the four discovered endpoints on
 * success so they can sanity-check that the issuer points at the right realm.
 */
function DiscoveryStatusBanner({ status }: DiscoveryStatusBannerProps) {
  if (status.kind === "idle" || status.kind === "probing") return null;
  if (status.kind === "failure") {
    return (
      <Alert
        severity="error"
        icon={<XCircle size={20} />}
        sx={{ mt: 1, alignItems: "flex-start" }}
      >
        <AlertTitle>Discovery failed</AlertTitle>
        <Typography variant="body2" sx={{ wordBreak: "break-word" }}>
          {status.error}
        </Typography>
      </Alert>
    );
  }
  return (
    <Alert
      severity="success"
      icon={<CheckCircle2 size={20} />}
      sx={{ mt: 1, alignItems: "flex-start" }}
    >
      <AlertTitle>Discovery succeeded</AlertTitle>
      <Box
        component="dl"
        sx={{
          display: "grid",
          gridTemplateColumns: "max-content 1fr",
          columnGap: 1.5,
          rowGap: 0.25,
          m: 0,
          fontSize: "0.85rem",
        }}
      >
        <DiscoveryEndpoint label="authorize" value={status.authorizationUri} />
        <DiscoveryEndpoint label="token" value={status.tokenUri} />
        <DiscoveryEndpoint label="user-info" value={status.userInfoUri} />
        <DiscoveryEndpoint label="JWK set" value={status.jwkSetUri} />
      </Box>
    </Alert>
  );
}

function DiscoveryEndpoint({
  label,
  value,
}: {
  label: string;
  value: string | null | undefined;
}) {
  return (
    <>
      <Box component="dt" sx={{ color: "text.secondary", fontWeight: 500 }}>
        {label}
      </Box>
      <Box
        component="dd"
        sx={{
          m: 0,
          fontFamily: "monospace",
          wordBreak: "break-all",
        }}
      >
        {value ?? <em style={{ opacity: 0.6 }}>not provided</em>}
      </Box>
    </>
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
        sx={{
          color: "text.secondary",
          letterSpacing: 0.6,
          lineHeight: 1
        }}>
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
