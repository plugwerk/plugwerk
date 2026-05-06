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
import { Link as RouterLink, useNavigate, useLocation } from "react-router-dom";
import {
  Alert,
  Box,
  Button,
  CircularProgress,
  Divider,
  IconButton,
  InputAdornment,
  Link,
  TextField,
  Tooltip,
  Typography,
} from "@mui/material";
import { Eye, EyeOff } from "lucide-react";
import { AuthCard } from "../components/auth/AuthCard";
import { OidcProviderButton } from "../components/auth/OidcProviderButton";
import { useAuthStore } from "../stores/authStore";
import { useConfigStore } from "../stores/configStore";
import { safeRedirectPath } from "../utils/safeRedirectPath";

export function LoginPage() {
  const { login } = useAuthStore();
  const fetchConfig = useConfigStore((s) => s.fetchConfig);
  const oidcProviders = useConfigStore((s) => s.oidcProviders);
  const selfRegistrationEnabled = useConfigStore(
    (s) => s.selfRegistrationEnabled,
  );
  const passwordResetEnabled = useConfigStore((s) => s.passwordResetEnabled);
  const navigate = useNavigate();
  const location = useLocation();
  const from = new URLSearchParams(location.search).get("from") ?? "/";

  // /config is public and cheap; the configStore caches it after the first
  // call so this is a no-op on subsequent renders. We need it here so the
  // OIDC provider buttons appear without the user having to load any other
  // page first.
  useEffect(() => {
    fetchConfig();
  }, [fetchConfig]);

  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!username.trim() || !password) {
      setError("Please enter username and password.");
      return;
    }
    setError(null);
    setLoading(true);
    try {
      await login(username.trim(), password);
      await useAuthStore.getState().initNamespace();
      const { passwordChangeRequired, namespace } = useAuthStore.getState();
      if (passwordChangeRequired) {
        navigate("/change-password", { replace: true });
      } else if (!namespace) {
        navigate("/onboarding", { replace: true });
      } else {
        // Two independent guards on the attacker-controllable `?from=` value:
        // 1. safeRedirectPath rejects open-redirect inputs like //evil.com,
        //    https://evil.com, javascript:… and any non-same-origin target
        //    (TS-004 / #274).
        // 2. Saved namespace URLs from a previous session may point at a
        //    namespace the user no longer has access to — drop them.
        const validatedFrom = safeRedirectPath(from);
        const safeFrom = validatedFrom.startsWith("/namespaces/")
          ? "/"
          : validatedFrom;
        navigate(safeFrom, { replace: true });
      }
    } catch {
      setError("Invalid username or password.");
    } finally {
      setLoading(false);
    }
  }

  return (
    <AuthCard title="Welcome back" subtitle="Sign in to your account">
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
          label="Username"
          value={username}
          onChange={(e) => setUsername(e.target.value)}
          required
          autoComplete="username"
          size="small"
          autoFocus
        />
        <TextField
          label="Password"
          type={showPassword ? "text" : "password"}
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          required
          autoComplete="current-password"
          size="small"
          slotProps={{
            input: {
              endAdornment: (
                <InputAdornment position="end">
                  <Tooltip
                    title={showPassword ? "Hide password" : "Show password"}
                    placement="top"
                    arrow
                  >
                    <IconButton
                      onClick={() => setShowPassword((s) => !s)}
                      onMouseDown={(e) => e.preventDefault()}
                      aria-label={
                        showPassword ? "Hide password" : "Show password"
                      }
                      aria-pressed={showPassword}
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
        {passwordResetEnabled && (
          // Same gating pattern as the Sign-up link: rendered only when the
          // operator has switched the feature on (#421). The backend 404s
          // /auth/forgot-password and /auth/reset-password independently,
          // so flipping this client-side cannot bypass the gate.
          (<Box sx={{ mt: -1, textAlign: "right" }}>
            <Link
              component={RouterLink}
              to="/forgot-password"
              underline="hover"
              variant="body2"
              sx={{
                color: "text.secondary"
              }}
            >
              Forgot password?
            </Link>
          </Box>)
        )}
        <Button
          type="submit"
          variant="contained"
          size="large"
          disabled={loading}
          fullWidth
          // Reserve the spinner slot in the start position so the button
          // width does not jitter on click — the inline spinner replaces
          // an invisible 18×18 placeholder rather than expanding the
          // button mid-press.
          startIcon={
            loading ? <CircularProgress size={16} color="inherit" /> : undefined
          }
        >
          {loading ? "Signing in…" : "Sign In"}
        </Button>
        {selfRegistrationEnabled && (
          // Rendered conditionally on the public /config flag (#420). The
          // backend independently 404s POST /auth/register when the flag is
          // off, so flipping this client-side cannot bypass the gate.
          (<Typography
            variant="body2"
            sx={{
              color: "text.secondary",
              textAlign: "center"
            }}>Don't have an account?{" "}
            <Link
              component={RouterLink}
              to="/register"
              underline="hover"
              sx={{
                fontWeight: 600
              }}
            >
              Sign up
            </Link>
          </Typography>)
        )}
      </Box>
      {oidcProviders.length > 0 && (
        <Box sx={{ display: "flex", flexDirection: "column", gap: 2, mt: 1 }}>
          <Divider>
            <Typography variant="caption" sx={{
              color: "text.secondary"
            }}>
              or
            </Typography>
          </Divider>
          {oidcProviders.map((provider) => (
            <OidcProviderButton key={provider.id} provider={provider} />
          ))}
        </Box>
      )}
    </AuthCard>
  );
}
