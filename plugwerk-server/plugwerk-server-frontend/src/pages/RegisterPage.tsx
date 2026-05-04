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
import { Link as RouterLink, useNavigate } from "react-router-dom";
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
import { authRegistrationApi } from "../api/config";
import { useConfigStore } from "../stores/configStore";
import { RegisterResponseStatusEnum } from "../api/generated/model/register-response";

const PASSWORD_MIN_LENGTH = 12;

interface FieldErrors {
  username?: string;
  email?: string;
  password?: string;
}

function extractApiError(error: unknown): string {
  if (axios.isAxiosError(error)) {
    const message = (error.response?.data as { message?: string } | undefined)
      ?.message;
    if (typeof message === "string" && message.length > 0) return message;
    return error.message;
  }
  if (error instanceof Error) return error.message;
  return "Registration failed.";
}

/**
 * Public self-registration form (#420).
 *
 * Mounted on `/register`. Visible only when the operator has flipped
 * `auth.self_registration_enabled` (the link on the login page is gated
 * by the same flag); when the setting is off the backend returns 404
 * and we surface the same "page not found" copy here so a deep-linked
 * URL doesn't reveal that the endpoint exists.
 *
 * Status flow:
 *   - 200 + `VERIFICATION_PENDING` → navigate to `/onboarding/verify-email`
 *     with the email in router state so the waiting page can show
 *     "check your inbox at alice@example.com".
 *   - 200 + `ACTIVE` → navigate straight to `/login` (operator turned
 *     verification off; account is enabled immediately).
 *   - 404 → render an inline "registration is disabled" notice.
 *   - 503 → infrastructure error (SMTP off or send failed); surface
 *     the server message verbatim.
 *   - 429 → rate limit hit; surface the cool-off hint.
 *   - 400 → bean-validation failure; surface field-level errors when
 *     the message is structured, otherwise show the raw text.
 */
export function RegisterPage() {
  const navigate = useNavigate();
  const fetchConfig = useConfigStore((s) => s.fetchConfig);
  const selfRegistrationEnabled = useConfigStore(
    (s) => s.selfRegistrationEnabled,
  );
  const configLoaded = useConfigStore((s) => s.loaded);

  const [username, setUsername] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});
  const [topLevelError, setTopLevelError] = useState<string | null>(null);
  const [disabledOnServer, setDisabledOnServer] = useState(false);

  useEffect(() => {
    void fetchConfig();
  }, [fetchConfig]);

  function validateLocally(): FieldErrors {
    const errors: FieldErrors = {};
    if (!username.trim()) errors.username = "Username is required.";
    if (!email.trim()) {
      errors.email = "Email is required.";
    } else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.trim())) {
      errors.email = "Enter a valid email address.";
    }
    if (password.length < PASSWORD_MIN_LENGTH) {
      errors.password = `Password must be at least ${PASSWORD_MIN_LENGTH} characters.`;
    }
    return errors;
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    const localErrors = validateLocally();
    if (Object.keys(localErrors).length > 0) {
      setFieldErrors(localErrors);
      return;
    }
    setFieldErrors({});
    setTopLevelError(null);
    setSubmitting(true);
    try {
      const response = await authRegistrationApi.register({
        registerRequest: {
          username: username.trim(),
          email: email.trim(),
          password,
        },
      });
      // Both ACTIVE and VERIFICATION_PENDING return 200 — the status
      // discriminator decides where to send the user next.
      if (response.data.status === RegisterResponseStatusEnum.Active) {
        // No verification needed (operator opted out). Send straight to
        // login with a pre-filled username so the next step is one
        // password entry.
        navigate("/login", {
          state: { registeredUsername: username.trim() },
          replace: true,
        });
        return;
      }
      navigate("/onboarding/verify-email", {
        state: { email: email.trim() },
        replace: true,
      });
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
              ? `Too many registration attempts. Try again in ${retry} seconds.`
              : "Too many registration attempts. Please wait a moment.",
          );
          return;
        }
      }
      setTopLevelError(extractApiError(err));
    } finally {
      setSubmitting(false);
    }
  }

  // Server-side disabled state: render the same "page not found"-style
  // copy as if the route were genuinely missing, matching the backend's
  // 404 disguise. Operators who turn the flag off after a deep link was
  // shared still see consistent behaviour.
  if (disabledOnServer || (configLoaded && !selfRegistrationEnabled)) {
    return (
      <AuthCard
        title="Registration unavailable"
        subtitle="Self-registration is not enabled on this server."
      >
        <Typography variant="body2" color="text.secondary">
          Contact your administrator to request an account.
        </Typography>
        <Box sx={{ textAlign: "center", mt: 1 }}>
          <Link
            component={RouterLink}
            to="/login"
            underline="hover"
            fontWeight={600}
          >
            Back to login
          </Link>
        </Box>
      </AuthCard>
    );
  }

  return (
    <AuthCard title="Create account" subtitle="Register to publish plugins">
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
          label="Username"
          required
          size="small"
          autoComplete="username"
          value={username}
          onChange={(e) => setUsername(e.target.value)}
          disabled={submitting}
          error={Boolean(fieldErrors.username)}
          helperText={fieldErrors.username}
          inputProps={{ "aria-label": "Username" }}
        />
        <TextField
          label="Email"
          type="email"
          required
          size="small"
          autoComplete="email"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          disabled={submitting}
          error={Boolean(fieldErrors.email)}
          helperText={fieldErrors.email}
          inputProps={{ "aria-label": "Email" }}
        />
        <TextField
          label="Password"
          type={showPassword ? "text" : "password"}
          required
          size="small"
          autoComplete="new-password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          disabled={submitting}
          error={Boolean(fieldErrors.password)}
          helperText={
            fieldErrors.password ?? `Minimum ${PASSWORD_MIN_LENGTH} characters.`
          }
          inputProps={{ "aria-label": "Password" }}
          slotProps={{
            input: {
              endAdornment: (
                <InputAdornment position="end">
                  <Tooltip
                    title={showPassword ? "Hide password" : "Show password"}
                  >
                    <IconButton
                      aria-label={
                        showPassword ? "Hide password" : "Show password"
                      }
                      onClick={() => setShowPassword((v) => !v)}
                      edge="end"
                      size="small"
                    >
                      {showPassword ? <EyeOff size={16} /> : <Eye size={16} />}
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
          {submitting ? "Creating account…" : "Create Account"}
        </Button>
      </Box>
      <Typography
        variant="caption"
        color="text.disabled"
        sx={{ textAlign: "center" }}
      >
        Already have an account?{" "}
        <Link
          component={RouterLink}
          to="/login"
          sx={{ color: "primary.main" }}
          underline="hover"
        >
          Log in
        </Link>
      </Typography>
    </AuthCard>
  );
}
