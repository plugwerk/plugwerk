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
import { Box, IconButton, TextField, Tooltip, Typography } from "@mui/material";
import { Check, Copy } from "lucide-react";
import { AppDialog } from "../common/AppDialog";
import { useUiStore } from "../../stores/uiStore";

interface AdminResetLinkDialogProps {
  /** Whether the dialog is visible. */
  open: boolean;
  /** Display name of the user the reset was triggered for; used in copy. */
  targetDisplayName: string;
  /** Absolute reset URL the operator must deliver out-of-band. */
  resetUrl: string;
  /** Closes the dialog. */
  onClose: () => void;
}

/**
 * Surfaces the admin-initiated reset URL when SMTP is unavailable (#450).
 *
 * Triggered after `POST /admin/users/{id}/reset-password` returns
 * `emailSent: false` — the server-side state changes (token, sessions
 * revoked) all happened, but the email could not be sent. The operator
 * needs the link to deliver out-of-band (Slack, phone, in-person).
 *
 * The URL is shown in a read-only `TextField` so the operator can pick
 * it up via the clipboard or by selecting + copying manually. The
 * dedicated copy button calls `navigator.clipboard.writeText` and surfaces
 * a transient toast plus an icon swap as confirmation.
 */
export function AdminResetLinkDialog({
  open,
  targetDisplayName,
  resetUrl,
  onClose,
}: AdminResetLinkDialogProps) {
  const [justCopied, setJustCopied] = useState(false);
  const addToast = useUiStore((s) => s.addToast);

  async function handleCopy() {
    try {
      await navigator.clipboard.writeText(resetUrl);
      setJustCopied(true);
      addToast({
        message: "Reset link copied to clipboard.",
        type: "success",
      });
      // Reset the icon swap after 1.5 s so a follow-up copy is obvious.
      setTimeout(() => setJustCopied(false), 1500);
    } catch {
      addToast({
        message: "Could not copy link automatically — please copy it manually.",
        type: "error",
      });
    }
  }

  return (
    <AppDialog
      open={open}
      onClose={onClose}
      title="SMTP unavailable — deliver this reset link"
      description={`The reset for "${targetDisplayName}" succeeded server-side: a single-use token was issued and every active session was revoked. SMTP is not configured (or the send failed), so the email could not be delivered. Copy the link below and deliver it to the user out-of-band — once they open it they can choose a new password.`}
      actionLabel="Done"
      onAction={onClose}
    >
      <Box sx={{ display: "flex", flexDirection: "column", gap: 1.5 }}>
        <Box sx={{ display: "flex", alignItems: "stretch", gap: 1 }}>
          <TextField
            value={resetUrl}
            // MUI 7: `slotProps.htmlInput` is the supported escape hatch for
            // attributes that need to land on the underlying `<input>`. The
            // legacy `inputProps` prop does not always propagate `readOnly`
            // reliably, which #450's test suite caught.
            slotProps={{
              htmlInput: { readOnly: true, "aria-label": "Reset link" },
            }}
            size="small"
            fullWidth
            // Single-line, no overflow truncation: long tokens should be
            // visible by horizontal scroll, not truncated, so a manual copy
            // out of the field always grabs the full URL.
          />
          <Tooltip title={justCopied ? "Copied" : "Copy to clipboard"}>
            <IconButton
              onClick={handleCopy}
              aria-label="Copy reset link to clipboard"
              size="small"
              sx={{ alignSelf: "center" }}
            >
              {justCopied ? <Check size={16} /> : <Copy size={16} />}
            </IconButton>
          </Tooltip>
        </Box>
        <Typography
          variant="caption"
          sx={{
            color: "text.secondary",
          }}
        >
          The link is single-use and time-limited. It will become invalid as
          soon as the user opens it and chooses a new password — or after the
          configured expiry passes.
        </Typography>
      </Box>
    </AppDialog>
  );
}
