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
import { useEffect, useState, type FormEvent } from "react";
import { Link as RouterLink } from "react-router-dom";
import {
  Alert,
  Box,
  Button,
  CircularProgress,
  Link,
  TextField,
  Typography,
} from "@mui/material";
import { Mail } from "lucide-react";
import axios from "axios";
import { AuthCard } from "../components/auth/AuthCard";
import { authPasswordResetApi } from "../api/config";
import { useConfigStore } from "../stores/configStore";

/**
 * Public forgot-password entry page (#421).
 *
 * Mounted on `/forgot-password`. Visible only when the operator has
 * flipped `auth.password_reset_enabled` on; the link on the login
 * page is gated by the same flag, and direct navigation here when
 * the feature is off renders a NotFound-style copy that mirrors the
 * backend's 404 disguise.
 *
 * Anti-enumeration shape: regardless of whether the supplied
 * username/email matches an INTERNAL user, the success path renders
 * the same "if an account exists, we sent a link" confirmation. The
 * legitimate owner gets the email; an attacker probing addresses
 * gets nothing distinguishable. Only true infrastructure failures
 * (SMTP not configured, rate limit hit) surface a different state.
 */
export function ForgotPasswordPage() {
  const fetchConfig = useConfigStore((s) => s.fetchConfig);
  const passwordResetEnabled = useConfigStore((s) => s.passwordResetEnabled);
  const configLoaded = useConfigStore((s) => s.loaded);

  const [usernameOrEmail, setUsernameOrEmail] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [submitted, setSubmitted] = useState(false);
  const [topLevelError, setTopLevelError] = useState<string | null>(null);
  const [disabledOnServer, setDisabledOnServer] = useState(false);

  useEffect(() => {
    void fetchConfig();
  }, [fetchConfig]);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    if (!usernameOrEmail.trim()) return;
    setTopLevelError(null);
    setSubmitting(true);
    try {
      await authPasswordResetApi.forgotPassword({
        forgotPasswordRequest: {
          usernameOrEmail: usernameOrEmail.trim(),
        },
      });
      setSubmitted(true);
    } catch (err) {
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
              ? `Too many attempts. Try again in ${retry} seconds.`
              : "Too many attempts. Please wait a moment.",
          );
          return;
        }
        if (status === 503) {
          setTopLevelError(
            (err.response?.data as { message?: string } | undefined)?.message ??
              "Password reset is temporarily unavailable. Please try again later.",
          );
          return;
        }
      }
      // Anything else: still pretend it worked. The operator sees the real
      // failure in the server log; the user sees the same neutral
      // confirmation as the success path.
      setSubmitted(true);
    } finally {
      setSubmitting(false);
    }
  }

  // Server-off / config-off → render the same not-found-style copy as if
  // the route never existed. Keeps the UI consistent with the backend's
  // 404 disguise on /auth/forgot-password.
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

  if (submitted) {
    return (
      <AuthCard
        title="Check your inbox"
        subtitle="If the account exists, a reset link is on its way"
      >
        <Box
          sx={{
            display: "flex",
            flexDirection: "column",
            alignItems: "center",
            gap: 2,
            textAlign: "center",
          }}
        >
          <Box
            sx={{
              width: 56,
              height: 56,
              borderRadius: "50%",
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
              bgcolor: "primary.main",
              color: "primary.contrastText",
            }}
            aria-hidden="true"
          >
            <Mail size={28} />
          </Box>
          <Typography variant="body1">
            If an account exists for that username or email, a reset link is on
            its way.
          </Typography>
          <Typography variant="body2" sx={{
            color: "text.secondary"
          }}>
            Click the link in the email to set a new password. The link expires
            after a short while; request a new one if it's stale.
          </Typography>
          <Typography
            variant="caption"
            sx={{
              color: "text.disabled",
              mt: 1
            }}>
            No email? Check your spam folder. If the address you supplied isn't
            registered, no message is sent — verify the spelling or contact your
            administrator.
          </Typography>
        </Box>
        <Box sx={{ textAlign: "center", mt: 2 }}>
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

  return (
    <AuthCard
      title="Reset password"
      subtitle="Enter your username or email to receive a reset link"
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
          label="Username or email"
          required
          size="small"
          autoFocus
          autoComplete="username"
          value={usernameOrEmail}
          onChange={(e) => setUsernameOrEmail(e.target.value)}
          disabled={submitting}
          slotProps={{ htmlInput: { "aria-label": "Username or email" } }}
        />
        <Button
          type="submit"
          variant="contained"
          size="large"
          fullWidth
          disabled={submitting || !usernameOrEmail.trim()}
          startIcon={
            submitting ? (
              <CircularProgress size={16} color="inherit" />
            ) : undefined
          }
        >
          {submitting ? "Sending…" : "Send reset link"}
        </Button>
      </Box>
      <Typography
        variant="caption"
        sx={{
          color: "text.disabled",
          textAlign: "center"
        }}>
        Remember your password?{" "}
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
