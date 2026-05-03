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
import { useEffect, useRef, useState } from "react";
import { Box, Button, CircularProgress, Link, Typography } from "@mui/material";
import { AlertCircle, CheckCircle2 } from "lucide-react";
import {
  Link as RouterLink,
  useNavigate,
  useSearchParams,
} from "react-router-dom";
import axios from "axios";
import { AuthCard } from "../components/auth/AuthCard";
import { authRegistrationApi } from "../api/config";

type CallbackStatus = "loading" | "verified" | "invalid" | "missing";

function extractApiError(error: unknown): string {
  if (axios.isAxiosError(error)) {
    const message = (error.response?.data as { message?: string } | undefined)
      ?.message;
    if (typeof message === "string" && message.length > 0) return message;
    return error.message;
  }
  if (error instanceof Error) return error.message;
  return "Verification failed.";
}

/**
 * Handles the link a self-registered user clicks in their verification
 * email (#420). Reads `?token=…`, calls the backend verify endpoint, and
 * shows one of three terminal states: success (CTA back to login),
 * invalid/expired token (with the server-supplied reason), or missing
 * token (the URL was opened by hand or the email client mangled the
 * query string).
 *
 * Public route — no authentication required. The backend's 404 disguise
 * for the disabled state surfaces here as the "invalid token" branch
 * with the server message, which is acceptable because the user
 * landing here always has *some* link they're expecting to work.
 */
export function VerifyEmailCallbackPage() {
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const token = params.get("token");

  const [status, setStatus] = useState<CallbackStatus>(
    token ? "loading" : "missing",
  );
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  // Single-fire guard against React StrictMode's double-effect in dev: the
  // verify endpoint is single-use, so the second mount would otherwise see
  // its sibling already consumed the token and report "already used".
  // useRef survives the dev-only re-invocation (state is preserved across
  // StrictMode's remount of the same component instance) but resets on a
  // real navigation away. Production gets the same belt-and-suspenders.
  // Note: this does NOT defend against link-preview prefetchers (Outlook
  // safe-link, Slack/Teams) — those hit the URL before the user does and
  // would still burn the token. Future fix: 2-step UI with explicit POST.
  const fired = useRef(false);

  useEffect(() => {
    if (!token || fired.current) return;
    fired.current = true;
    // Deliberately no cleanup-cancellation: with the fired-guard above
    // there is exactly one in-flight request, so the classic
    // closure-cancelled pattern would just turn StrictMode's simulated
    // unmount into a permanent loading spinner (the cleanup sets
    // cancelled=true, the second effect bails on `fired`, the only
    // resolved promise sees `cancelled` and never updates state).
    // React 18+ silently no-ops setState on unmounted components.
    void (async () => {
      try {
        await authRegistrationApi.verifyEmail({ token });
        setStatus("verified");
      } catch (err) {
        setErrorMessage(extractApiError(err));
        setStatus("invalid");
      }
    })();
  }, [token]);

  if (status === "missing") {
    return (
      <AuthCard
        title="No verification token"
        subtitle="The link is missing the required `?token=…` parameter."
      >
        <Typography variant="body2" color="text.secondary">
          Open the link from your verification email instead. If you no longer
          have the email, register again to receive a new one.
        </Typography>
        <Box sx={{ textAlign: "center", mt: 2 }}>
          <Link
            component={RouterLink}
            to="/register"
            underline="hover"
            fontWeight={600}
          >
            Register
          </Link>
        </Box>
      </AuthCard>
    );
  }

  if (status === "loading") {
    return (
      <AuthCard title="Verifying your email" subtitle="One moment please">
        <Box
          sx={{
            display: "flex",
            justifyContent: "center",
            alignItems: "center",
            gap: 1.5,
            py: 2,
          }}
        >
          <CircularProgress size={20} />
          <Typography variant="body2">Verifying token…</Typography>
        </Box>
      </AuthCard>
    );
  }

  if (status === "verified") {
    return (
      <AuthCard title="Email verified" subtitle="Your account is now active">
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
              bgcolor: "success.main",
              color: "success.contrastText",
            }}
            aria-hidden="true"
          >
            <CheckCircle2 size={28} />
          </Box>
          <Typography variant="body1">
            You can now log in with the credentials you chose during
            registration.
          </Typography>
        </Box>
        <Box sx={{ textAlign: "center", mt: 2 }}>
          <Button
            variant="contained"
            size="large"
            onClick={() => navigate("/login", { replace: true })}
          >
            Go to login
          </Button>
        </Box>
      </AuthCard>
    );
  }

  // status === "invalid"
  return (
    <AuthCard
      title="Verification failed"
      subtitle="The link is invalid, expired, or already used"
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
            bgcolor: "warning.main",
            color: "warning.contrastText",
          }}
          aria-hidden="true"
        >
          <AlertCircle size={28} />
        </Box>
        <Typography variant="body2" color="text.secondary">
          {errorMessage ??
            "The verification link could not be processed. It may have expired or already been used."}
        </Typography>
        <Typography variant="caption" color="text.disabled">
          Register again to request a fresh verification email.
        </Typography>
      </Box>
      <Box
        sx={{
          textAlign: "center",
          mt: 2,
          display: "flex",
          gap: 2,
          justifyContent: "center",
        }}
      >
        <Link
          component={RouterLink}
          to="/register"
          underline="hover"
          fontWeight={600}
        >
          Register
        </Link>
        <Link
          component={RouterLink}
          to="/login"
          underline="hover"
          color="text.secondary"
        >
          Back to login
        </Link>
      </Box>
    </AuthCard>
  );
}
