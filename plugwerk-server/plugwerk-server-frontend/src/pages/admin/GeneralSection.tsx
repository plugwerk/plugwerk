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
  Divider,
  FormControl,
  FormControlLabel,
  FormHelperText,
  InputLabel,
  MenuItem,
  Select,
  Switch,
  TextField,
  Typography,
} from "@mui/material";
import type { ApplicationSettingDto } from "../../api/generated/model/application-setting-dto";
import { useSettingsStore } from "../../stores/settingsStore";
import { useUiStore } from "../../stores/uiStore";

/**
 * Sparse map of user-edited fields, keyed by setting key. Only contains keys whose value
 * differs from the canonical [ApplicationSettingDto.value] that was last loaded from the
 * server. Cleared after a successful save or an explicit discard.
 */
type DraftMap = Record<string, string>;

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

export function GeneralSection() {
  const settings = useSettingsStore((s) => s.settings);
  const loaded = useSettingsStore((s) => s.loaded);
  const loading = useSettingsStore((s) => s.loading);
  const saving = useSettingsStore((s) => s.saving);
  const loadSettings = useSettingsStore((s) => s.load);
  const updateSettings = useSettingsStore((s) => s.update);
  const addToast = useUiStore((s) => s.addToast);

  const [draft, setDraft] = useState<DraftMap>({});
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

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

  const restartPendingKeys = useMemo(
    () => settings.filter((s) => s.restartPending).map((s) => s.key),
    [settings],
  );

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
          continue;
        }
        if (typeof s.maxInt === "number" && parsed > s.maxInt) {
          errors[s.key] = `Must be <= ${s.maxInt}`;
          continue;
        }
      } else if (s.valueType === "STRING" && value.trim().length === 0) {
        errors[s.key] = "Must not be blank";
      } else if (
        s.valueType === "ENUM" &&
        s.allowedValues &&
        !s.allowedValues.includes(value)
      ) {
        errors[s.key] = `Must be one of ${s.allowedValues.join(", ")}`;
      } else if (
        s.valueType === "BOOLEAN" &&
        value !== "true" &&
        value !== "false"
      ) {
        errors[s.key] = "Must be true or false";
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
      addToast({
        type: "success",
        message: "Settings saved.",
      });
    } catch (err) {
      const message =
        err instanceof Error ? err.message : "Failed to save settings.";
      addToast({ type: "error", message });
    }
  }

  function handleDiscard() {
    setDraft({});
    setFieldErrors({});
  }

  const sortedSettings = useMemo(
    () => [...settings].sort((a, b) => a.key.localeCompare(b.key)),
    [settings],
  );

  return (
    <Box sx={{ display: "flex", flexDirection: "column", gap: 3 }}>
      <Box>
        <Typography variant="h2" gutterBottom>
          General Settings
        </Typography>
        <Divider sx={{ mb: 2 }} />
        <Typography variant="body2" color="text.secondary">
          These settings are stored in the database and shared across all users.
          Changes marked as requiring a restart take effect after the next
          server restart.
        </Typography>
      </Box>

      {restartPendingKeys.length > 0 && (
        <Alert severity="warning" role="alert">
          The following setting
          {restartPendingKeys.length === 1 ? " has" : "s have"} been changed
          since the server started and will only take effect after a restart:{" "}
          <strong>{restartPendingKeys.join(", ")}</strong>
        </Alert>
      )}

      {!loaded && loading && (
        <Box sx={{ display: "flex", alignItems: "center", gap: 1 }}>
          <CircularProgress size={18} />
          <Typography variant="body2">Loading settings…</Typography>
        </Box>
      )}

      {loaded && settings.length === 0 && (
        <Alert severity="info">No application settings are available.</Alert>
      )}

      {loaded &&
        sortedSettings.map((setting) => (
          <SettingField
            key={setting.key}
            setting={setting}
            value={draft[setting.key] ?? setting.value}
            onChange={(v) => handleFieldChange(setting.key, v)}
            error={fieldErrors[setting.key]}
            disabled={saving}
          />
        ))}

      <Box sx={{ display: "flex", gap: 2, mt: 1 }}>
        <Button
          variant="contained"
          onClick={handleSave}
          disabled={!hasChanges || saving || !loaded}
        >
          {saving ? "Saving…" : "Save Changes"}
        </Button>
        <Button
          variant="text"
          onClick={handleDiscard}
          disabled={!hasChanges || saving}
        >
          Discard
        </Button>
      </Box>
    </Box>
  );
}

interface SettingFieldProps {
  readonly setting: ApplicationSettingDto;
  readonly value: string;
  readonly onChange: (next: string) => void;
  readonly error: string | undefined;
  readonly disabled: boolean;
}

function formatLabel(key: string): string {
  return key
    .split(".")
    .pop()!
    .split("_")
    .map((p) => p.charAt(0).toUpperCase() + p.slice(1))
    .join(" ");
}

function SettingField({
  setting,
  value,
  onChange,
  error,
  disabled,
}: SettingFieldProps) {
  const label = formatLabel(setting.key);
  const helperText = error ?? setting.description ?? undefined;

  if (setting.valueType === "BOOLEAN") {
    const checked = value === "true";
    return (
      <FormControl error={Boolean(error)}>
        <FormControlLabel
          control={
            <Switch
              checked={checked}
              onChange={(e) => onChange(e.target.checked ? "true" : "false")}
              disabled={disabled}
              inputProps={{ "aria-label": label }}
            />
          }
          label={label}
        />
        {helperText && <FormHelperText>{helperText}</FormHelperText>}
      </FormControl>
    );
  }

  if (
    setting.valueType === "ENUM" &&
    setting.allowedValues &&
    setting.allowedValues.length > 0
  ) {
    return (
      <FormControl size="small" sx={{ minWidth: 240 }} error={Boolean(error)}>
        <InputLabel id={`label-${setting.key}`}>{label}</InputLabel>
        <Select
          labelId={`label-${setting.key}`}
          label={label}
          value={value}
          onChange={(e) => onChange(e.target.value)}
          disabled={disabled}
          inputProps={{ "aria-label": label }}
        >
          {setting.allowedValues.map((v) => (
            <MenuItem key={v} value={v}>
              {v}
            </MenuItem>
          ))}
        </Select>
        {helperText && <FormHelperText>{helperText}</FormHelperText>}
      </FormControl>
    );
  }

  if (setting.valueType === "INTEGER") {
    return (
      <TextField
        label={label}
        type="number"
        size="small"
        value={value}
        onChange={(e) => onChange(e.target.value)}
        error={Boolean(error)}
        helperText={helperText}
        disabled={disabled}
        inputProps={{
          min: setting.minInt,
          max: setting.maxInt,
          "aria-label": label,
        }}
        sx={{ maxWidth: 320 }}
      />
    );
  }

  return (
    <TextField
      label={label}
      size="small"
      value={value}
      onChange={(e) => onChange(e.target.value)}
      error={Boolean(error)}
      helperText={helperText}
      disabled={disabled}
      inputProps={{ "aria-label": label }}
      sx={{ maxWidth: 480 }}
    />
  );
}
