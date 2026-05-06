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
  Button,
  CircularProgress,
  FormControl,
  FormControlLabel,
  FormHelperText,
  IconButton,
  InputAdornment,
  InputLabel,
  MenuItem,
  Select,
  Switch,
  TextField,
  Typography,
} from "@mui/material";
import { Eye, EyeOff, Mail, Send, Server, User } from "lucide-react";
import axios from "axios";
import { Section } from "../../components/common/Section";
import { adminEmailApi } from "../../api/config";
import type { ApplicationSettingDto } from "../../api/generated/model/application-setting-dto";
import { useSettingsStore } from "../../stores/settingsStore";
import { useAuthStore } from "../../stores/authStore";
import { useUiStore } from "../../stores/uiStore";
import { tokens } from "../../theme/tokens";

type DraftMap = Record<string, string>;

const SMTP_KEYS = [
  "smtp.enabled",
  "smtp.host",
  "smtp.port",
  "smtp.username",
  "smtp.password",
  "smtp.encryption",
  "smtp.from_address",
  "smtp.from_name",
] as const;

const ENCRYPTION_LABELS: Record<string, string> = {
  none: "None (plain SMTP)",
  starttls: "STARTTLS (port 587)",
  tls: "Implicit TLS (port 465)",
};

function computeDirtyPatch(
  settings: ApplicationSettingDto[],
  draft: DraftMap,
): Record<string, string> {
  const byKey = new Map(settings.map((s) => [s.key, s.value]));
  const patch: Record<string, string> = {};
  for (const [key, draftValue] of Object.entries(draft)) {
    const canonical = byKey.get(key);
    if (canonical !== undefined && draftValue !== canonical) {
      patch[key] = draftValue;
    }
  }
  return patch;
}

function extractApiError(error: unknown): string {
  if (axios.isAxiosError(error)) {
    const message = (error.response?.data as { message?: string } | undefined)
      ?.message;
    if (typeof message === "string" && message.length > 0) return message;
    return error.message;
  }
  if (error instanceof Error) return error.message;
  return "Unknown error";
}

export function EmailServerPage() {
  const settings = useSettingsStore((s) => s.settings);
  const loaded = useSettingsStore((s) => s.loaded);
  const loading = useSettingsStore((s) => s.loading);
  const saving = useSettingsStore((s) => s.saving);
  const loadSettings = useSettingsStore((s) => s.load);
  const updateSettings = useSettingsStore((s) => s.update);
  const userEmail = useAuthStore((s) => s.email);
  const addToast = useUiStore((s) => s.addToast);

  const [draft, setDraft] = useState<DraftMap>({});
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [showPassword, setShowPassword] = useState(false);
  // Default the test recipient to the logged-in admin's email when known.
  // Initialised synchronously from the auth store at first render rather
  // than via a useEffect — an effect that re-fills the input on every empty
  // state would race with user.clear() in tests and reset typed input.
  const [testTarget, setTestTarget] = useState(userEmail ?? "");
  const [testing, setTesting] = useState(false);
  const [testResult, setTestResult] = useState<
    | { kind: "success"; message: string }
    | { kind: "error"; message: string }
    | null
  >(null);

  useEffect(() => {
    if (!loaded && !loading) {
      void loadSettings().catch(() => {
        addToast({
          type: "error",
          message: "Failed to load application settings.",
        });
      });
    }
  }, [loaded, loading, loadSettings, addToast]);

  const dirtyPatch = useMemo(
    () => computeDirtyPatch(settings, draft),
    [settings, draft],
  );
  const hasChanges = Object.keys(dirtyPatch).length > 0;

  const byKey = useMemo(() => {
    const map = new Map<string, ApplicationSettingDto>();
    for (const s of settings) map.set(s.key, s);
    return map;
  }, [settings]);

  function effectiveValue(key: string): string {
    return draft[key] ?? byKey.get(key)?.value ?? "";
  }

  function handleFieldChange(key: string, rawValue: string): void {
    setDraft((prev) => ({ ...prev, [key]: rawValue }));
    setFieldErrors((prev) => {
      if (!(key in prev)) return prev;
      const next = { ...prev };
      delete next[key];
      return next;
    });
  }

  function validateLocally(): Record<string, string> {
    const errors: Record<string, string> = {};
    for (const s of settings) {
      if (!SMTP_KEYS.includes(s.key as (typeof SMTP_KEYS)[number])) continue;
      if (!(s.key in draft)) continue;
      const value = draft[s.key];
      if (s.valueType === "INTEGER") {
        const parsed = Number.parseInt(value, 10);
        if (Number.isNaN(parsed) || String(parsed) !== value.trim()) {
          errors[s.key] = "Must be an integer";
          continue;
        }
        if (typeof s.minInt === "number" && parsed < s.minInt) {
          errors[s.key] = `Must be >= ${s.minInt}`;
        } else if (typeof s.maxInt === "number" && parsed > s.maxInt) {
          errors[s.key] = `Must be <= ${s.maxInt}`;
        }
      } else if (
        s.valueType === "BOOLEAN" &&
        value !== "true" &&
        value !== "false"
      ) {
        errors[s.key] = "Must be true or false";
      } else if (
        s.valueType === "ENUM" &&
        s.allowedValues &&
        !s.allowedValues.includes(value)
      ) {
        errors[s.key] = `Must be one of ${s.allowedValues.join(", ")}`;
      }
    }
    return errors;
  }

  async function handleSave() {
    const localErrors = validateLocally();
    if (Object.keys(localErrors).length > 0) {
      setFieldErrors(localErrors);
      addToast({
        type: "error",
        message: "Please fix the highlighted fields before saving.",
      });
      return;
    }
    if (!hasChanges) return;
    try {
      await updateSettings(dirtyPatch);
      setDraft({});
      setFieldErrors({});
      addToast({ type: "success", message: "SMTP settings saved." });
    } catch (err) {
      addToast({ type: "error", message: extractApiError(err) });
    }
  }

  function handleDiscard() {
    setDraft({});
    setFieldErrors({});
    setTestResult(null);
  }

  async function handleTestConnection() {
    if (!testTarget.trim()) {
      setTestResult({
        kind: "error",
        message: "Enter a recipient email address first.",
      });
      return;
    }
    if (hasChanges) {
      setTestResult({
        kind: "error",
        message: "Save your changes before sending a test message.",
      });
      return;
    }
    setTesting(true);
    setTestResult(null);
    try {
      const response = await adminEmailApi.sendTestEmail({
        sendTestEmailRequest: { target: testTarget.trim() },
      });
      setTestResult({
        kind: "success",
        message:
          response.data.message ?? `Test email sent to ${testTarget.trim()}`,
      });
    } catch (err) {
      setTestResult({ kind: "error", message: extractApiError(err) });
    } finally {
      setTesting(false);
    }
  }

  if (!loaded && loading) {
    return (
      <Box sx={{ display: "flex", alignItems: "center", gap: 1, py: 4 }}>
        <CircularProgress size={18} />
        <Typography variant="body2">Loading SMTP settings…</Typography>
      </Box>
    );
  }

  const enabledValue = effectiveValue("smtp.enabled") === "true";

  return (
    <Box sx={{ display: "flex", flexDirection: "column", gap: 3 }}>
      <Section
        contentGap={2.5}
        icon={<Server size={18} />}
        title="Connection"
        description="Hostname, port, encryption, and credentials for the SMTP relay."
      >
        <TextField
          label="Host"
          size="small"
          value={effectiveValue("smtp.host")}
          onChange={(e) => handleFieldChange("smtp.host", e.target.value)}
          error={Boolean(fieldErrors["smtp.host"])}
          helperText={
            fieldErrors["smtp.host"] ?? byKey.get("smtp.host")?.description
          }
          disabled={saving}
          placeholder="smtp.example.com"
          sx={{ maxWidth: 480 }}
          inputProps={{ "aria-label": "Host" }}
        />
        <TextField
          label="Port"
          type="number"
          size="small"
          value={effectiveValue("smtp.port")}
          onChange={(e) => handleFieldChange("smtp.port", e.target.value)}
          error={Boolean(fieldErrors["smtp.port"])}
          helperText={
            fieldErrors["smtp.port"] ?? byKey.get("smtp.port")?.description
          }
          disabled={saving}
          inputProps={{ min: 1, max: 65535, "aria-label": "Port" }}
          sx={{ maxWidth: 200 }}
        />
        <FormControl
          size="small"
          sx={{ maxWidth: 320 }}
          error={Boolean(fieldErrors["smtp.encryption"])}
        >
          <InputLabel id="label-smtp-encryption">Encryption</InputLabel>
          <Select
            labelId="label-smtp-encryption"
            label="Encryption"
            value={effectiveValue("smtp.encryption")}
            onChange={(e) =>
              handleFieldChange("smtp.encryption", e.target.value)
            }
            disabled={saving}
            inputProps={{ "aria-label": "Encryption" }}
          >
            {(
              byKey.get("smtp.encryption")?.allowedValues ?? [
                "none",
                "starttls",
                "tls",
              ]
            ).map((v) => (
              <MenuItem key={v} value={v}>
                {ENCRYPTION_LABELS[v] ?? v}
              </MenuItem>
            ))}
          </Select>
          {byKey.get("smtp.encryption")?.description && (
            <FormHelperText>
              {byKey.get("smtp.encryption")?.description}
            </FormHelperText>
          )}
        </FormControl>
        <TextField
          label="Username"
          size="small"
          value={effectiveValue("smtp.username")}
          onChange={(e) => handleFieldChange("smtp.username", e.target.value)}
          helperText={
            byKey.get("smtp.username")?.description ??
            "Leave blank for unauthenticated relays."
          }
          disabled={saving}
          sx={{ maxWidth: 480 }}
          inputProps={{ "aria-label": "Username" }}
        />
        <TextField
          label="Password"
          size="small"
          type={showPassword ? "text" : "password"}
          value={effectiveValue("smtp.password")}
          onChange={(e) => handleFieldChange("smtp.password", e.target.value)}
          helperText={
            byKey.get("smtp.password")?.description ??
            "Stored encrypted at rest. Leave the masked value to keep the existing password."
          }
          disabled={saving}
          sx={{ maxWidth: 480 }}
          inputProps={{ "aria-label": "Password" }}
          InputProps={{
            endAdornment: (
              <InputAdornment position="end">
                <IconButton
                  aria-label={showPassword ? "Hide password" : "Show password"}
                  onClick={() => setShowPassword((v) => !v)}
                  edge="end"
                  size="small"
                >
                  {showPassword ? <EyeOff size={16} /> : <Eye size={16} />}
                </IconButton>
              </InputAdornment>
            ),
          }}
        />
      </Section>
      <Section
        contentGap={2.5}
        icon={<User size={18} />}
        title="Identity"
        description="From-address shown to recipients in the message header."
      >
        <TextField
          label="From address"
          size="small"
          type="email"
          value={effectiveValue("smtp.from_address")}
          onChange={(e) =>
            handleFieldChange("smtp.from_address", e.target.value)
          }
          error={Boolean(fieldErrors["smtp.from_address"])}
          helperText={
            fieldErrors["smtp.from_address"] ??
            byKey.get("smtp.from_address")?.description
          }
          disabled={saving}
          placeholder="noreply@example.com"
          sx={{ maxWidth: 480 }}
          inputProps={{ "aria-label": "From address" }}
        />
        <TextField
          label="From name"
          size="small"
          value={effectiveValue("smtp.from_name")}
          onChange={(e) => handleFieldChange("smtp.from_name", e.target.value)}
          helperText={byKey.get("smtp.from_name")?.description}
          disabled={saving}
          sx={{ maxWidth: 480 }}
          inputProps={{ "aria-label": "From name" }}
        />
      </Section>
      <Section
        contentGap={2.5}
        icon={<Mail size={18} />}
        title="Status"
        description="Master switch for outgoing email and a one-shot test send."
      >
        <FormControl error={Boolean(fieldErrors["smtp.enabled"])}>
          <FormControlLabel
            control={
              <Switch
                checked={enabledValue}
                onChange={(e) =>
                  handleFieldChange(
                    "smtp.enabled",
                    e.target.checked ? "true" : "false",
                  )
                }
                disabled={saving}
                inputProps={{ "aria-label": "SMTP enabled" }}
              />
            }
            label="SMTP enabled"
          />
          <FormHelperText>
            {byKey.get("smtp.enabled")?.description}
          </FormHelperText>
        </FormControl>

        <Box
          sx={{
            display: "flex",
            flexDirection: { xs: "column", sm: "row" },
            gap: 2,
            alignItems: { xs: "stretch", sm: "flex-end" },
            mt: 1,
          }}
        >
          <TextField
            label="Test recipient"
            size="small"
            type="email"
            value={testTarget}
            onChange={(e) => setTestTarget(e.target.value)}
            disabled={testing}
            sx={{ maxWidth: 360, flex: 1 }}
            inputProps={{ "aria-label": "Test recipient" }}
          />
          <Button
            variant="outlined"
            startIcon={
              testing ? <CircularProgress size={16} /> : <Send size={16} />
            }
            onClick={handleTestConnection}
            disabled={testing || !enabledValue}
            sx={{ borderRadius: tokens.radius.btn }}
          >
            {testing ? "Sending…" : "Send Test Email"}
          </Button>
        </Box>
        {!enabledValue && (
          <Typography variant="caption" sx={{
            color: "text.secondary"
          }}>
            Enable SMTP and save your changes before sending a test message.
          </Typography>
        )}

        {testResult && (
          <Alert
            severity={testResult.kind === "success" ? "success" : "error"}
            role="status"
          >
            {testResult.message}
          </Alert>
        )}
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
          disabled={!hasChanges || saving || !loaded}
          sx={{ borderRadius: tokens.radius.btn }}
        >
          {saving ? "Saving…" : "Save Changes"}
        </Button>
      </Box>
    </Box>
  );
}
