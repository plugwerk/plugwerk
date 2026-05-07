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
import { Box, Link, Typography } from "@mui/material";
import { Mail } from "lucide-react";
import { Link as RouterLink, useLocation } from "react-router-dom";
import { AuthCard } from "../components/auth/AuthCard";

interface PendingState {
  email?: string;
}

/**
 * Waiting state shown immediately after a successful self-registration
 * with verification required (#420). The previous page (RegisterPage)
 * passes the submitted email via router state so we can show "Check
 * your inbox at alice@example.com" instead of a generic "check your
 * email". The token-callback for the link itself lives in
 * [VerifyEmailCallbackPage] under `/verify-email`.
 */
export function VerifyEmailPendingPage() {
  const location = useLocation();
  const state = (location.state ?? null) as PendingState | null;
  const email = state?.email;

  return (
    <AuthCard
      title="Check your inbox"
      subtitle="If the address is new, we just sent a verification link"
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
          {email ? (
            <>
              If{" "}
              <Box component="strong" sx={{ fontWeight: 600 }}>
                {email}
              </Box>{" "}
              isn&apos;t already registered, a verification link is on its way.
            </>
          ) : (
            "If the address you provided isn't already registered, a verification link is on its way."
          )}
        </Typography>
        <Typography
          variant="body2"
          sx={{
            color: "text.secondary",
          }}
        >
          Click the link in the email to activate your account. The link expires
          in 24 hours.
        </Typography>
        <Typography
          variant="caption"
          sx={{
            color: "text.disabled",
            mt: 1,
          }}
        >
          No email? Check your spam folder. If the address is already in use, no
          new email is sent — try{" "}
          <Box component="strong" sx={{ fontWeight: 600 }}>
            signing in
          </Box>{" "}
          or use{" "}
          <Box component="strong" sx={{ fontWeight: 600 }}>
            forgot password
          </Box>
          .
        </Typography>
      </Box>
      <Box sx={{ textAlign: "center", mt: 2 }}>
        <Link
          component={RouterLink}
          to="/login"
          underline="hover"
          sx={{
            fontWeight: 600,
          }}
        >
          Back to login
        </Link>
      </Box>
    </AuthCard>
  );
}
