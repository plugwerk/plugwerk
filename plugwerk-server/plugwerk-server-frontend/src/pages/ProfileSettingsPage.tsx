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
import { useEffect, useState } from "react";
import {
  Box,
  Button,
  Container,
  FormControl,
  InputLabel,
  MenuItem,
  Select,
  Typography,
} from "@mui/material";
import { User, Globe, FolderOpen, Lock, Palette } from "lucide-react";
import { TimezoneSelect } from "../components/common/TimezoneSelect";
import { Link } from "react-router-dom";
import { Section } from "../components/common/Section";
import { useAuthStore } from "../stores/authStore";
import { useNamespaceStore } from "../stores/namespaceStore";
import { useUserSettingsStore } from "../stores/userSettingsStore";
import { tokens } from "../theme/tokens";
import { useUiStore } from "../stores/uiStore";

interface InfoRowProps {
  label: string;
  value: string;
}

function InfoRow({ label, value }: InfoRowProps) {
  return (
    <Box sx={{ display: "flex", alignItems: "baseline", gap: 2, py: 0.75 }}>
      <Typography
        variant="caption"
        color="text.secondary"
        sx={{ minWidth: 80, flexShrink: 0, fontWeight: 500 }}
      >
        {label}
      </Typography>
      <Typography variant="body2">{value}</Typography>
    </Box>
  );
}

export function ProfileSettingsPage() {
  const { username, namespace, setNamespace } = useAuthStore();
  const { namespaces } = useNamespaceStore();
  const { addToast } = useUiStore();
  const {
    settings,
    loaded,
    loading: settingsLoading,
    saving,
    load: loadSettings,
    update: updateSettings,
  } = useUserSettingsStore();

  const [language, setLanguage] = useState("en");
  const [timezone, setTimezone] = useState("");
  const [theme, setThemeValue] = useState("system");
  const [defaultNs, setDefaultNs] = useState(namespace ?? "");

  useEffect(() => {
    loadSettings().catch(() => {});
  }, [loadSettings]);

  useEffect(() => {
    if (loaded) {
      setLanguage(settings.preferred_language ?? "en");
      setTimezone(settings.timezone ?? "");
      setThemeValue(settings.theme ?? "system");
      setDefaultNs(settings.default_namespace ?? namespace ?? "");
    }
  }, [loaded, settings, namespace]);

  async function handleSave() {
    try {
      await updateSettings({
        preferred_language: language,
        timezone: timezone,
        theme: theme,
        default_namespace: defaultNs,
      });
      if (defaultNs && defaultNs !== namespace) {
        setNamespace(defaultNs);
      }
      addToast({ type: "success", message: "Profile settings saved." });
    } catch {
      addToast({ type: "error", message: "Failed to save profile settings." });
    }
  }

  return (
    <Box component="main" id="main-content" sx={{ flex: 1, py: 4 }}>
      <Container maxWidth="sm">
        <Typography variant="h1" sx={{ mb: 0.5 }}>
          Profile Settings
        </Typography>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 4 }}>
          Manage your account preferences and workspace configuration.
        </Typography>

        <Box sx={{ display: "flex", flexDirection: "column", gap: 2.5 }}>
          {/* Personal Information */}
          <Section icon={<User size={18} />} title="Personal Information">
            <InfoRow label="Username" value={username ?? "—"} />
            <InfoRow label="Email" value="Not set" />
          </Section>

          {/* Security */}
          <Section
            icon={<Lock size={18} />}
            title="Security"
            description="Manage your password"
          >
            <Button
              component={Link}
              to="/change-password"
              variant="outlined"
              size="small"
              sx={{ borderRadius: tokens.radius.btn }}
            >
              Change Password
            </Button>
          </Section>

          {/* Language & Region */}
          <Section
            icon={<Globe size={18} />}
            title="Language & Region"
            description="Override the system defaults set by the administrator"
          >
            <Box
              sx={{ display: "flex", flexDirection: "column", gap: 2, mb: 1 }}
            >
              <FormControl size="small" sx={{ minWidth: 220 }}>
                <InputLabel>Language</InputLabel>
                <Select
                  value={language}
                  label="Language"
                  onChange={(e) => setLanguage(e.target.value)}
                >
                  <MenuItem value="en">English</MenuItem>
                </Select>
              </FormControl>
              <TimezoneSelect
                value={timezone}
                onChange={setTimezone}
                label="Timezone"
                helperText="Leave empty to use the system default timezone."
                allowEmpty
                sx={{ maxWidth: 480 }}
              />
            </Box>
          </Section>

          {/* Theme */}
          <Section
            icon={<Palette size={18} />}
            title="Theme"
            description="Choose your preferred color scheme"
          >
            <FormControl size="small" sx={{ minWidth: 220 }}>
              <InputLabel>Theme</InputLabel>
              <Select
                value={theme}
                label="Theme"
                onChange={(e) => setThemeValue(e.target.value)}
              >
                <MenuItem value="system">System</MenuItem>
                <MenuItem value="light">Light</MenuItem>
                <MenuItem value="dark">Dark</MenuItem>
              </Select>
            </FormControl>
          </Section>

          {/* Default Namespace */}
          <Section
            icon={<FolderOpen size={18} />}
            title="Default Namespace"
            description="Used by default for catalog and upload operations"
          >
            <FormControl size="small" sx={{ minWidth: 220 }}>
              <InputLabel>Namespace</InputLabel>
              <Select
                value={defaultNs}
                label="Namespace"
                onChange={(e) => setDefaultNs(e.target.value)}
              >
                {namespaces.map((ns) => (
                  <MenuItem key={ns.slug} value={ns.slug}>
                    {ns.name} ({ns.slug})
                  </MenuItem>
                ))}
              </Select>
            </FormControl>
          </Section>

          <Box sx={{ display: "flex", justifyContent: "flex-end" }}>
            <Button
              variant="contained"
              sx={{ borderRadius: tokens.radius.btn }}
              onClick={handleSave}
              disabled={saving || settingsLoading}
            >
              {saving ? "Saving…" : "Save Changes"}
            </Button>
          </Box>
        </Box>
      </Container>
    </Box>
  );
}
