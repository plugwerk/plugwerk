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
  Chip,
  CircularProgress,
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
import { Activity, Globe, Upload } from "lucide-react";
import { TimezoneSelect } from "../../components/common/TimezoneSelect";
import type { ApplicationSettingDto } from "../../api/generated/model/application-setting-dto";
import { Section } from "../../components/common/Section";
import { useSettingsStore } from "../../stores/settingsStore";
import { useUiStore } from "../../stores/uiStore";
import { tokens } from "../../theme/tokens";

type DraftMap = Record<string, string>;

const LANGUAGE_LABELS: Record<string, string> = {
  en: "English",
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
      addToast({ type: "success", message: "Settings saved." });
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

  function renderField(key: string) {
    const setting = byKey.get(key);
    if (!setting) return null;
    const value = effectiveValue(key);
    const error = fieldErrors[key];
    const helperText = error ?? setting.description ?? undefined;
    const label = formatLabel(key);

    if (setting.valueType === "BOOLEAN") {
      return (
        <FormControl key={key} error={Boolean(error)}>
          <FormControlLabel
            control={
              <Switch
                checked={value === "true"}
                onChange={(e) =>
                  handleFieldChange(key, e.target.checked ? "true" : "false")
                }
                disabled={saving}
                inputProps={{ "aria-label": label }}
              />
            }
            label={label}
          />
          {helperText && <FormHelperText>{helperText}</FormHelperText>}
        </FormControl>
      );
    }

    if (key === "general.timezone") {
      return (
        <FormControl key={key} error={Boolean(error)} sx={{ maxWidth: 480 }}>
          <TimezoneSelect
            value={value}
            onChange={(v) => handleFieldChange(key, v)}
            label={label}
            helperText={helperText}
            error={Boolean(error)}
            disabled={saving}
          />
        </FormControl>
      );
    }

    if (key === "general.default_language") {
      const allowed = setting.allowedValues ?? ["en", "de"];
      return (
        <FormControl
          key={key}
          size="small"
          sx={{ minWidth: 240 }}
          error={Boolean(error)}
        >
          <InputLabel id={`label-${key}`}>{label}</InputLabel>
          <Select
            labelId={`label-${key}`}
            label={label}
            value={value}
            onChange={(e) => handleFieldChange(key, e.target.value)}
            disabled={saving}
            inputProps={{ "aria-label": label }}
          >
            {allowed.map((v) => (
              <MenuItem key={v} value={v}>
                {LANGUAGE_LABELS[v] ?? v}
              </MenuItem>
            ))}
          </Select>
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
        <FormControl
          key={key}
          size="small"
          sx={{ minWidth: 240 }}
          error={Boolean(error)}
        >
          <InputLabel id={`label-${key}`}>{label}</InputLabel>
          <Select
            labelId={`label-${key}`}
            label={label}
            value={value}
            onChange={(e) => handleFieldChange(key, e.target.value)}
            disabled={saving}
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
        <Box
          key={key}
          sx={{ display: "flex", alignItems: "flex-start", gap: 1.5 }}
        >
          <TextField
            label={label}
            type="number"
            size="small"
            value={value}
            onChange={(e) => handleFieldChange(key, e.target.value)}
            error={Boolean(error)}
            helperText={helperText}
            disabled={saving}
            inputProps={{
              min: setting.minInt,
              max: setting.maxInt,
              "aria-label": label,
            }}
            sx={{ maxWidth: 320 }}
          />
          {setting.requiresRestart && (
            <Chip
              label="Requires restart"
              size="small"
              color="warning"
              variant="outlined"
              sx={{ height: 24, fontSize: "0.75rem", mt: 1 }}
            />
          )}
        </Box>
      );
    }

    return (
      <TextField
        key={key}
        label={label}
        size="small"
        value={value}
        onChange={(e) => handleFieldChange(key, e.target.value)}
        error={Boolean(error)}
        helperText={helperText}
        disabled={saving}
        inputProps={{ "aria-label": label }}
        sx={{ maxWidth: 480 }}
      />
    );
  }

  if (!loaded && loading) {
    return (
      <Box sx={{ display: "flex", alignItems: "center", gap: 1, py: 4 }}>
        <CircularProgress size={18} />
        <Typography variant="body2">Loading settings…</Typography>
      </Box>
    );
  }

  if (loaded && settings.length === 0) {
    return (
      <Alert severity="info">No application settings are available.</Alert>
    );
  }

  return (
    <Box sx={{ display: "flex", flexDirection: "column", gap: 3 }}>
      <Box>
        <Typography variant="h2" gutterBottom>
          General Settings
        </Typography>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
          These settings are stored in the database and apply to all users.
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

      <Box sx={{ display: "flex", flexDirection: "column", gap: 2.5 }}>
        {/* General */}
        <Section
          contentGap={2.5}
          icon={<Globe size={18} />}
          title="General"
          description="Application name and default language"
        >
          {renderField("general.site_name")}
          {renderField("general.default_language")}
          {renderField("general.timezone")}
        </Section>

        {/* Upload */}
        <Section
          contentGap={2.5}
          icon={<Upload size={18} />}
          title="Upload"
          description="Artifact upload constraints"
        >
          {renderField("upload.max_file_size_mb")}
        </Section>

        {/* Download Tracking */}
        <Section
          contentGap={2.5}
          icon={<Activity size={18} />}
          title="Download Tracking"
          description="Privacy settings for download event recording"
        >
          {renderField("tracking.enabled")}
          {renderField("tracking.capture_ip")}
          {renderField("tracking.anonymize_ip")}
          {renderField("tracking.capture_user_agent")}
        </Section>
      </Box>

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

function formatLabel(key: string): string {
  return key
    .split(".")
    .pop()!
    .split("_")
    .map((p) => p.charAt(0).toUpperCase() + p.slice(1))
    .join(" ");
}
