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
import { useState } from "react";
import { useNavigate } from "react-router-dom";
import {
  Alert,
  Box,
  Button,
  CircularProgress,
  IconButton,
  InputAdornment,
  TextField,
  Tooltip,
} from "@mui/material";
import { Eye, EyeOff } from "lucide-react";
import axios from "axios";
import { AuthCard } from "../components/auth/AuthCard";
import { authApi } from "../api/config";
import { useAuthStore } from "../stores/authStore";

interface PasswordVisibilityAdornmentProps {
  visible: boolean;
  onToggle: () => void;
}

function PasswordVisibilityAdornment({
  visible,
  onToggle,
}: PasswordVisibilityAdornmentProps) {
  // Same eye-toggle pattern used by LoginPage / ResetPasswordPage so the
  // three auth surfaces stay visually consistent (issue #405 follow-up).
  // onMouseDown.preventDefault keeps the field focused when the button is
  // clicked — without it, MUI's TextField loses the cursor mid-type.
  return (
    <InputAdornment position="end">
      <Tooltip title={visible ? "Hide password" : "Show password"}>
        <IconButton
          onClick={onToggle}
          onMouseDown={(e) => e.preventDefault()}
          aria-label={visible ? "Hide password" : "Show password"}
          aria-pressed={visible}
          edge="end"
          size="small"
        >
          {visible ? <EyeOff size={16} /> : <Eye size={16} />}
        </IconButton>
      </Tooltip>
    </InputAdornment>
  );
}

function parseError(err: unknown): string {
  if (axios.isAxiosError(err) && err.response?.status === 429) {
    const retryAfter = err.response.headers["retry-after"];
    const seconds = Number.parseInt(retryAfter ?? "", 10);
    if (Number.isFinite(seconds) && seconds > 0) {
      return `Too many password-change attempts. Please try again in ${seconds} seconds.`;
    }
    return "Too many password-change attempts. Please try again later.";
  }
  return "Failed to change password. Please check your current password.";
}

export function ChangePasswordPage() {
  const navigate = useNavigate();
  const { username, clearPasswordChangeRequired } = useAuthStore();

  const [currentPassword, setCurrentPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [showCurrent, setShowCurrent] = useState(false);
  const [showNew, setShowNew] = useState(false);
  const [showConfirm, setShowConfirm] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (newPassword.length < 12) {
      setError("New password must be at least 12 characters.");
      return;
    }
    if (newPassword !== confirmPassword) {
      setError("Passwords do not match.");
      return;
    }
    setError(null);
    setLoading(true);
    try {
      await authApi.changePassword({
        changePasswordRequest: { currentPassword, newPassword },
      });
      clearPasswordChangeRequired();
      navigate("/", { replace: true });
    } catch (err: unknown) {
      setError(parseError(err));
    } finally {
      setLoading(false);
    }
  }

  return (
    <AuthCard
      title="Change your password"
      subtitle={`Signed in as ${username ?? "unknown"}. You must set a new password to continue.`}
    >
      {error && (
        <Alert severity="error" role="alert" onClose={() => setError(null)}>
          {error}
        </Alert>
      )}
      <Box
        component="form"
        onSubmit={handleSubmit}
        noValidate
        sx={{
          display: "flex",
          flexDirection: "column",
          gap: 2,
          // Mute the MUI default required-asterisk colour (issue #405). The
          // default `error.main` paints the asterisks bright red on mount,
          // which reads as "validation failed" before the operator typed
          // anything. Validation here runs in `handleSubmit` and surfaces
          // through a top-level Alert, not the field `error` prop.
          "& .MuiFormLabel-asterisk": { color: "text.secondary" },
        }}
      >
        <TextField
          label="Current Password"
          type={showCurrent ? "text" : "password"}
          value={currentPassword}
          onChange={(e) => setCurrentPassword(e.target.value)}
          required
          size="small"
          autoComplete="current-password"
          autoFocus
          slotProps={{
            input: {
              endAdornment: (
                <PasswordVisibilityAdornment
                  visible={showCurrent}
                  onToggle={() => setShowCurrent((v) => !v)}
                />
              ),
            },
          }}
        />
        <TextField
          label="New Password"
          type={showNew ? "text" : "password"}
          value={newPassword}
          onChange={(e) => setNewPassword(e.target.value)}
          required
          size="small"
          autoComplete="new-password"
          helperText="At least 12 characters"
          slotProps={{
            input: {
              endAdornment: (
                <PasswordVisibilityAdornment
                  visible={showNew}
                  onToggle={() => setShowNew((v) => !v)}
                />
              ),
            },
          }}
        />
        <TextField
          label="Confirm New Password"
          type={showConfirm ? "text" : "password"}
          value={confirmPassword}
          onChange={(e) => setConfirmPassword(e.target.value)}
          required
          size="small"
          autoComplete="new-password"
          slotProps={{
            input: {
              endAdornment: (
                <PasswordVisibilityAdornment
                  visible={showConfirm}
                  onToggle={() => setShowConfirm((v) => !v)}
                />
              ),
            },
          }}
        />
        <Button
          type="submit"
          variant="contained"
          size="large"
          disabled={loading}
          fullWidth
          // Reserve the spinner slot in the start position so the button
          // width does not jitter on click — same pattern as LoginPage.
          startIcon={
            loading ? <CircularProgress size={16} color="inherit" /> : undefined
          }
        >
          {loading ? "Saving…" : "Set New Password"}
        </Button>
      </Box>
    </AuthCard>
  );
}
