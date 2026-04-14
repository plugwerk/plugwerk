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
import { Box, IconButton, Paper, Slide, Typography } from "@mui/material";
import { Info, CheckCircle, AlertTriangle, XCircle, X } from "lucide-react";
import { useUiStore, type ToastItem } from "../../stores/uiStore";
import { tokens } from "../../theme/tokens";

const iconMap = {
  info: <Info size={20} />,
  success: <CheckCircle size={20} />,
  warning: <AlertTriangle size={20} />,
  error: <XCircle size={20} />,
};

const colorMap = {
  info: tokens.color.primary,
  success: tokens.color.success,
  warning: tokens.color.warning,
  error: tokens.color.danger,
};

function ToastItem({ toast }: { toast: ToastItem }) {
  const { removeToast } = useUiStore();
  const accentColor = colorMap[toast.type];

  return (
    <Slide direction="down" in={true} mountOnEnter unmountOnExit>
      <Paper
        role="status"
        elevation={0}
        sx={{
          display: "flex",
          alignItems: "center",
          gap: 1.5,
          px: 2,
          py: 1.5,
          width: 380,
          bgcolor: "background.paper",
          border: "1px solid",
          borderColor: "divider",
          borderLeftColor: accentColor,
          borderLeftWidth: 3,
          borderLeftStyle: "solid",
          borderRadius: tokens.radius.dialog,
          boxShadow: tokens.shadow.modal,
        }}
      >
        <Box sx={{ color: accentColor, flexShrink: 0, display: "flex" }}>
          {iconMap[toast.type]}
        </Box>
        <Box sx={{ flex: 1, minWidth: 0 }}>
          {toast.title && (
            <Typography
              variant="body2"
              fontWeight={600}
              sx={{ lineHeight: 1.4 }}
            >
              {toast.title}
            </Typography>
          )}
          {toast.message && (
            <Typography
              variant="body2"
              color="text.secondary"
              sx={{ lineHeight: 1.4, mt: toast.title ? 0.25 : 0 }}
            >
              {toast.message}
            </Typography>
          )}
        </Box>
        <IconButton
          size="small"
          onClick={() => removeToast(toast.id)}
          aria-label="Dismiss notification"
          sx={{ color: "text.disabled", p: 0.5, flexShrink: 0 }}
        >
          <X size={16} />
        </IconButton>
      </Paper>
    </Slide>
  );
}

export function ToastRegion() {
  const { toasts } = useUiStore();

  return (
    <Box
      role="region"
      aria-label="Notifications"
      aria-live="polite"
      sx={{
        position: "fixed",
        top: 80,
        left: "50%",
        transform: "translateX(-50%)",
        zIndex: 2000,
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        gap: 1.5,
        pointerEvents: "none",
        "& > *": { pointerEvents: "auto" },
      }}
    >
      {toasts.map((t) => (
        <ToastItem key={t.id} toast={t} />
      ))}
    </Box>
  );
}
