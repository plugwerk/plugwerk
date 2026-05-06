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
import { useEffect, useRef, useState, type FormEvent } from "react";
import {
  Link as RouterLink,
  useNavigate,
  useSearchParams,
} from "react-router-dom";
import {
  Alert,
  Box,
  Button,
  CircularProgress,
  IconButton,
  InputAdornment,
  Link,
  TextField,
  Tooltip,
  Typography,
} from "@mui/material";
import { Eye, EyeOff } from "lucide-react";
import axios from "axios";
import { AuthCard } from "../components/auth/AuthCard";
import { authPasswordResetApi } from "../api/config";
import { useConfigStore } from "../stores/configStore";
import { useUiStore } from "../stores/uiStore";

const PASSWORD_MIN_LENGTH = 12;

interface FieldErrors {
  newPassword?: string;
  confirmPassword?: string;
}

function extractApiError(error: unknown): string {
  if (axios.isAxiosError(error)) {
    const message = (error.response?.data as { message?: string } | undefined)
      ?.message;
    if (typeof message === "string" && message.length > 0) return message;
    return error.message;
  }
  if (error instanceof Error) return error.message;
  return "Password reset failed.";
}

/**
 * Token-callback page reached from the reset email (#421).
 *
 * Reads `?token=…`, asks the user for a new password, and on success
 * redirects to `/login` with a success toast. The reset endpoint is
 * single-use, so a duplicate POST (StrictMode double-fire, browser
 * back/forth) would surface as "already used" — the form-level
 * `submitted` guard short-circuits this before the second request
 * leaves the page.
 *
 * Mirrors `ForgotPasswordPage`'s server-off disguise: when the
 * operator has the feature disabled, the page renders the same
 * not-found-style copy as if the route never existed.
 */
export function ResetPasswordPage() {
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const token = params.get("token");

  const fetchConfig = useConfigStore((s) => s.fetchConfig);
  const passwordResetEnabled = useConfigStore((s) => s.passwordResetEnabled);
  const configLoaded = useConfigStore((s) => s.loaded);
  const addToast = useUiStore((s) => s.addToast);

  const [newPassword, setNewPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [showNew, setShowNew] = useState(false);
  const [showConfirm, setShowConfirm] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});
  const [topLevelError, setTopLevelError] = useState<string | null>(null);
  const [disabledOnServer, setDisabledOnServer] = useState(false);

  // Single-fire guard against duplicate submits (StrictMode-safe disciplina,
  // and prevents the second click from racing the first while the request
  // is still in flight). Mirrors VerifyEmailCallbackPage's pattern.
  const submitted = useRef(false);

  useEffect(() => {
    void fetchConfig();
  }, [fetchConfig]);

  function validateLocally(): FieldErrors {
    const errors: FieldErrors = {};
    if (newPassword.length < PASSWORD_MIN_LENGTH) {
      errors.newPassword = `Password must be at least ${PASSWORD_MIN_LENGTH} characters.`;
    }
    if (confirmPassword !== newPassword) {
      errors.confirmPassword = "Passwords do not match.";
    }
    return errors;
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    if (!token || submitted.current) return;
    const localErrors = validateLocally();
    if (Object.keys(localErrors).length > 0) {
      setFieldErrors(localErrors);
      return;
    }
    setFieldErrors({});
    setTopLevelError(null);
    setSubmitting(true);
    submitted.current = true;
    try {
      await authPasswordResetApi.resetPassword({
        resetPasswordRequest: {
          token,
          newPassword,
        },
      });
      addToast({
        type: "success",
        message: "Password updated. Please sign in with your new password.",
      });
      navigate("/login", { replace: true });
    } catch (err) {
      // Reset the guard so the user can retry after fixing the cause
      // (typed the wrong new password, etc.).
      submitted.current = false;
      if (axios.isAxiosError(err)) {
        const status = err.response?.status;
        if (status === 404) {
          setDisabledOnServer(true);
          return;
        }
        if (status === 429) {
          const retry = err.response?.headers["retry-after"];
          setTopLevelError(
            retry
              ? `Too many attempts on this link. Try again in ${retry} seconds.`
              : "Too many attempts on this link. Please wait a moment.",
          );
          return;
        }
      }
      setTopLevelError(extractApiError(err));
    } finally {
      setSubmitting(false);
    }
  }

  if (disabledOnServer || (configLoaded && !passwordResetEnabled)) {
    return (
      <AuthCard
        title="Password reset unavailable"
        subtitle="Self-service password reset is not enabled on this server."
      >
        <Typography variant="body2" sx={{
          color: "text.secondary"
        }}>
          Contact your administrator to have your password reset.
        </Typography>
        <Box sx={{ textAlign: "center", mt: 1 }}>
          <Link
            component={RouterLink}
            to="/login"
            underline="hover"
            sx={{
              fontWeight: 600
            }}
          >
            Back to login
          </Link>
        </Box>
      </AuthCard>
    );
  }

  if (!token) {
    return (
      <AuthCard
        title="No reset token"
        subtitle="The link is missing the required `?token=…` parameter."
      >
        <Typography variant="body2" sx={{
          color: "text.secondary"
        }}>
          Open the link from your reset email instead. If you no longer have the
          email, request a new reset link.
        </Typography>
        <Box sx={{ textAlign: "center", mt: 2 }}>
          <Link
            component={RouterLink}
            to="/forgot-password"
            underline="hover"
            sx={{
              fontWeight: 600
            }}
          >
            Request a new link
          </Link>
        </Box>
      </AuthCard>
    );
  }

  return (
    <AuthCard
      title="Set new password"
      subtitle="Enter and confirm your new password"
    >
      <Box
        component="form"
        noValidate
        onSubmit={handleSubmit}
        sx={{ display: "flex", flexDirection: "column", gap: 2 }}
      >
        {topLevelError && (
          <Alert severity="error" role="alert">
            {topLevelError}
          </Alert>
        )}
        <TextField
          label="New Password"
          type={showNew ? "text" : "password"}
          required
          size="small"
          autoFocus
          autoComplete="new-password"
          value={newPassword}
          onChange={(e) => setNewPassword(e.target.value)}
          disabled={submitting}
          error={Boolean(fieldErrors.newPassword)}
          helperText={
            fieldErrors.newPassword ??
            `Minimum ${PASSWORD_MIN_LENGTH} characters.`
          }
          inputProps={{ "aria-label": "New Password" }}
          slotProps={{
            input: {
              endAdornment: (
                <InputAdornment position="end">
                  <Tooltip title={showNew ? "Hide password" : "Show password"}>
                    <IconButton
                      aria-label={showNew ? "Hide password" : "Show password"}
                      onClick={() => setShowNew((v) => !v)}
                      edge="end"
                      size="small"
                    >
                      {showNew ? <EyeOff size={16} /> : <Eye size={16} />}
                    </IconButton>
                  </Tooltip>
                </InputAdornment>
              ),
            },
          }}
        />
        <TextField
          label="Confirm Password"
          type={showConfirm ? "text" : "password"}
          required
          size="small"
          autoComplete="new-password"
          value={confirmPassword}
          onChange={(e) => setConfirmPassword(e.target.value)}
          disabled={submitting}
          error={Boolean(fieldErrors.confirmPassword)}
          helperText={fieldErrors.confirmPassword}
          inputProps={{ "aria-label": "Confirm Password" }}
          slotProps={{
            input: {
              endAdornment: (
                <InputAdornment position="end">
                  <Tooltip
                    title={showConfirm ? "Hide password" : "Show password"}
                  >
                    <IconButton
                      aria-label={
                        showConfirm ? "Hide password" : "Show password"
                      }
                      onClick={() => setShowConfirm((v) => !v)}
                      edge="end"
                      size="small"
                    >
                      {showConfirm ? <EyeOff size={16} /> : <Eye size={16} />}
                    </IconButton>
                  </Tooltip>
                </InputAdornment>
              ),
            },
          }}
        />
        <Button
          type="submit"
          variant="contained"
          size="large"
          fullWidth
          disabled={submitting}
          startIcon={
            submitting ? (
              <CircularProgress size={16} color="inherit" />
            ) : undefined
          }
        >
          {submitting ? "Updating…" : "Set Password"}
        </Button>
      </Box>
      <Typography
        variant="caption"
        sx={{
          color: "text.disabled",
          textAlign: "center"
        }}>
        Remembered after all?{" "}
        <Link
          component={RouterLink}
          to="/login"
          sx={{ color: "primary.main" }}
          underline="hover"
        >
          Back to login
        </Link>
      </Typography>
    </AuthCard>
  );
}
