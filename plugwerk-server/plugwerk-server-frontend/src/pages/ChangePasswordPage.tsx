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
import { Box, TextField, Button, Alert } from "@mui/material";
import axios from "axios";
import { AuthCard } from "../components/auth/AuthCard";
import { authApi } from "../api/config";
import { useAuthStore } from "../stores/authStore";

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
          type="password"
          value={currentPassword}
          onChange={(e) => setCurrentPassword(e.target.value)}
          required
          size="small"
          autoComplete="current-password"
          autoFocus
        />
        <TextField
          label="New Password"
          type="password"
          value={newPassword}
          onChange={(e) => setNewPassword(e.target.value)}
          required
          size="small"
          autoComplete="new-password"
          helperText="At least 12 characters"
        />
        <TextField
          label="Confirm New Password"
          type="password"
          value={confirmPassword}
          onChange={(e) => setConfirmPassword(e.target.value)}
          required
          size="small"
          autoComplete="new-password"
        />
        <Button
          type="submit"
          variant="contained"
          size="large"
          disabled={loading}
          fullWidth
        >
          {loading ? "Saving…" : "Set New Password"}
        </Button>
      </Box>
    </AuthCard>
  );
}
