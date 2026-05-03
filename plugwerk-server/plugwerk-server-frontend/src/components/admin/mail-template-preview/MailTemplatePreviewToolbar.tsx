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
import { useMemo, useState, type ReactNode } from "react";
import {
  Box,
  Button,
  Collapse,
  IconButton,
  Stack,
  TextField,
  Tooltip,
  Typography,
  alpha,
  keyframes,
  useTheme,
} from "@mui/material";
import {
  AlertCircle,
  ChevronDown,
  ChevronUp,
  Loader2,
  RefreshCw,
  Variable,
} from "lucide-react";
import { tokens } from "../../../theme/tokens";
import type { PreviewStatus } from "../../../hooks/useMailTemplatePreview";

interface MailTemplatePreviewToolbarProps {
  status: PreviewStatus;
  sampleVars: Record<string, string>;
  onSampleVarsChange: (next: Record<string, string>) => void;
  onRefresh: () => void;
  placeholders: readonly string[];
}

const livePulse = keyframes`
  0%   { transform: scale(1);    opacity: 1; }
  50%  { transform: scale(1.45); opacity: 0.6; }
  100% { transform: scale(1);    opacity: 1; }
`;

/**
 * One-stop toolbar above the editor/preview grid.
 *
 * Carries the page-level preview status (Live/Syncing/Stale/Error), an
 * explicit Refresh button (bypasses the debounce), and a collapsible
 * Sample-variables form. Everything that's *about* the preview lives here
 * so the individual panes can stay focused on their specific render output.
 *
 * Sample-vars editing pushes the new map straight up via [onSampleVarsChange]
 * — the parent merges it into the preview hook's input, the hook re-renders
 * after the same debounce window as draft edits. No "Apply" button needed:
 * the debounce is the apply.
 */
export function MailTemplatePreviewToolbar({
  status,
  sampleVars,
  onSampleVarsChange,
  onRefresh,
  placeholders,
}: MailTemplatePreviewToolbarProps) {
  const theme = useTheme();
  const isDark = theme.palette.mode === "dark";
  const [varsExpanded, setVarsExpanded] = useState(false);

  const sortedKeys = useMemo(() => [...placeholders].sort(), [placeholders]);

  return (
    <Box
      sx={{
        border: "1px solid",
        borderColor: "divider",
        borderRadius: tokens.radius.card,
        background: isDark ? alpha("#ffffff", 0.02) : tokens.color.white,
        overflow: "hidden",
      }}
    >
      <Stack
        direction="row"
        alignItems="center"
        spacing={1.5}
        sx={{ px: 2, py: 1.25 }}
      >
        <Typography variant="subtitle2" fontWeight={600} sx={{ flexShrink: 0 }}>
          Live preview
        </Typography>
        <StatusBadge status={status} />
        <Box sx={{ flex: 1 }} />
        <Button
          variant="text"
          size="small"
          onClick={() => setVarsExpanded((v) => !v)}
          startIcon={<Variable size={14} />}
          endIcon={
            varsExpanded ? <ChevronUp size={14} /> : <ChevronDown size={14} />
          }
          sx={{
            borderRadius: tokens.radius.btn,
            color: "text.secondary",
            fontSize: "0.78rem",
          }}
        >
          Sample variables ({Object.keys(sampleVars).length})
        </Button>
        <Tooltip title="Refresh now">
          <span>
            <IconButton
              size="small"
              aria-label="Refresh preview"
              onClick={onRefresh}
              disabled={status === "syncing"}
              sx={{ borderRadius: tokens.radius.btn }}
            >
              <RefreshCw size={14} />
            </IconButton>
          </span>
        </Tooltip>
      </Stack>
      <Collapse in={varsExpanded} timeout={180}>
        <Box
          sx={{
            px: 2,
            pb: 2,
            pt: 0,
            borderTop: "1px solid",
            borderColor: "divider",
            background: isDark ? alpha("#ffffff", 0.02) : tokens.color.gray10,
          }}
        >
          <Box
            sx={{
              display: "grid",
              gridTemplateColumns: {
                xs: "1fr",
                sm: "repeat(2, minmax(0, 1fr))",
              },
              gap: 1.25,
              pt: 2,
            }}
          >
            {sortedKeys.map((name) => (
              <TextField
                key={name}
                label={`{{${name}}}`}
                size="small"
                value={sampleVars[name] ?? ""}
                onChange={(e) =>
                  onSampleVarsChange({
                    ...sampleVars,
                    [name]: e.target.value,
                  })
                }
                sx={{
                  "& .MuiInputLabel-root": {
                    fontFamily:
                      "ui-monospace, SFMono-Regular, Menlo, monospace",
                  },
                }}
              />
            ))}
          </Box>
          <Typography
            variant="caption"
            color="text.secondary"
            sx={{ display: "block", mt: 1.25, fontStyle: "italic" }}
          >
            Changes apply automatically after a short pause. Click refresh to
            run immediately.
          </Typography>
        </Box>
      </Collapse>
    </Box>
  );
}

interface StatusBadgeProps {
  status: PreviewStatus;
}

function StatusBadge({ status }: StatusBadgeProps): ReactNode {
  const theme = useTheme();
  const isDark = theme.palette.mode === "dark";
  const meta = STATUS_META[status];
  if (status === "idle") return null;
  return (
    <Stack
      direction="row"
      alignItems="center"
      spacing={0.625}
      sx={{
        px: 1,
        py: 0.25,
        borderRadius: 999,
        background: isDark ? alpha(meta.color, 0.18) : alpha(meta.color, 0.12),
        color: meta.color,
        fontSize: "0.7rem",
        fontWeight: 600,
        letterSpacing: 0.3,
        textTransform: "uppercase",
        userSelect: "none",
        flexShrink: 0,
      }}
      role="status"
      aria-live="polite"
      aria-label={`Preview status: ${meta.label}`}
    >
      <Box
        sx={{
          display: "flex",
          alignItems: "center",
          color: "inherit",
          ...(status === "live" && {
            "& > *": {
              animation: `${livePulse} 1.4s ease-out`,
              animationIterationCount: 1,
            },
          }),
          ...(status === "syncing" && {
            "& > *": {
              animation: "spin 0.9s linear infinite",
              "@keyframes spin": {
                from: { transform: "rotate(0deg)" },
                to: { transform: "rotate(360deg)" },
              },
            },
          }),
        }}
      >
        {meta.icon}
      </Box>
      <Box component="span">{meta.label}</Box>
    </Stack>
  );
}

const STATUS_META: Record<
  PreviewStatus,
  { label: string; color: string; icon: ReactNode }
> = {
  idle: { label: "", color: tokens.color.gray60, icon: null },
  stale: {
    label: "Stale",
    color: tokens.color.gray60,
    icon: (
      <Box
        sx={{
          width: 6,
          height: 6,
          borderRadius: "50%",
          background: "currentColor",
        }}
      />
    ),
  },
  syncing: {
    label: "Syncing",
    color: tokens.color.primary,
    icon: <Loader2 size={10} />,
  },
  live: {
    label: "Live",
    color: tokens.color.success,
    icon: (
      <Box
        sx={{
          width: 6,
          height: 6,
          borderRadius: "50%",
          background: "currentColor",
        }}
      />
    ),
  },
  error: {
    label: "Error",
    color: tokens.color.danger,
    icon: <AlertCircle size={10} />,
  },
};
