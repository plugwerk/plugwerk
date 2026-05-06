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
  Alert,
  Box,
  IconButton,
  Popover,
  Stack,
  TextField,
  Tooltip,
  Typography,
  alpha,
  keyframes,
  useTheme,
} from "@mui/material";
import { AlertCircle, Loader2, RefreshCw, Variable } from "lucide-react";
import { tokens } from "../../../theme/tokens";
import type { PreviewStatus } from "../../../hooks/useMailTemplatePreview";
import type { MailTemplatePreviewResponse } from "../../../api/generated/model/mail-template-preview-response";

export type PreviewMode = "subject" | "plain" | "html";

interface MailTemplatePreviewPaneProps {
  mode: PreviewMode;
  result: MailTemplatePreviewResponse | null;
  status: PreviewStatus;
  error: string | null;
  /**
   * Min height in CSS units. Set to the same value as the paired editor's
   * `minHeight` prop so the two columns align side-by-side.
   */
  minHeight: string;
  ariaLabel: string;
  /** Page-shared sample variables — same map across every pane. */
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
 * One read-only render output paired side-by-side with its editor (#438).
 *
 * Three modes — `subject` shows the rendered single-line subject in a
 * monospace box, `plain` shows the rendered plaintext body in a `<pre>`,
 * `html` renders the body inside a sandboxed iframe (no scripts, no
 * same-origin, no forms — pasted `<script>` tags render as inert text).
 *
 * Each pane carries its own header with the live-preview lifecycle
 * status, a Refresh button, and a Sample-variables popover so the
 * controls are reachable without scrolling away from the editor pair
 * the operator is currently looking at. The shared draft state behind
 * the scenes still produces one API round trip per debounced edit
 * (centralised in [useMailTemplatePreview]) — three panes, one fetch.
 */
export function MailTemplatePreviewPane({
  mode,
  result,
  status,
  error,
  minHeight,
  ariaLabel,
  sampleVars,
  onSampleVarsChange,
  onRefresh,
  placeholders,
}: MailTemplatePreviewPaneProps) {
  const theme = useTheme();
  const isDark = theme.palette.mode === "dark";
  const showError = error != null && status === "error";
  const renderedValue = result
    ? mode === "subject"
      ? result.subject
      : mode === "plain"
        ? result.bodyPlain
        : (result.bodyHtml ?? null)
    : null;

  // Subject pane is single-line; the body height knob doesn't apply.
  const computedMinHeight = mode === "subject" ? "auto" : minHeight;

  const [varsAnchor, setVarsAnchor] = useState<HTMLElement | null>(null);
  const sortedVarKeys = useMemo(() => [...placeholders].sort(), [placeholders]);

  return (
    <Box
      role="region"
      aria-label={ariaLabel}
      sx={{
        display: "flex",
        flexDirection: "column",
        minHeight: computedMinHeight,
        border: "1px solid",
        borderColor: "divider",
        borderRadius: tokens.radius.input,
        overflow: "hidden",
        background: isDark
          ? alpha("#ffffff", 0.02)
          : alpha(tokens.color.gray10, 0.6),
      }}
    >
      <Stack
        direction="row"
        spacing={1}
        sx={{
          alignItems: "center",
          px: 1.5,
          py: 0.5,
          minHeight: 36,
          borderBottom: "1px solid",
          borderColor: "divider",

          background: isDark
            ? alpha("#ffffff", 0.04)
            : alpha(tokens.color.gray20, 0.4)
        }}>
        <Stack
          direction="row"
          spacing={0.75}
          sx={{
            alignItems: "center",
            flex: 1,
            minWidth: 0
          }}>
          <Typography
            variant="caption"
            sx={{
              fontWeight: 600,
              color: "text.secondary",
              textTransform: "uppercase",
              letterSpacing: 0.6,
              fontSize: "0.65rem",
              flexShrink: 0,
            }}
          >
            Live preview
          </Typography>
          <StatusBadge status={status} />
        </Stack>
        <Tooltip title="Sample variables">
          <span>
            <IconButton
              size="small"
              aria-label={`Sample variables (${Object.keys(sampleVars).length})`}
              onClick={(e) => setVarsAnchor(e.currentTarget)}
              sx={{ borderRadius: tokens.radius.btn }}
            >
              <Stack direction="row" spacing={0.375} sx={{
                alignItems: "center"
              }}>
                <Variable size={13} />
                <Typography
                  variant="caption"
                  sx={{
                    fontSize: "0.7rem",
                    fontWeight: 600,
                    color: "text.secondary",
                    lineHeight: 1,
                  }}
                >
                  {Object.keys(sampleVars).length}
                </Typography>
              </Stack>
            </IconButton>
          </span>
        </Tooltip>
        <Tooltip title="Refresh now">
          <span>
            <IconButton
              size="small"
              aria-label="Refresh preview"
              onClick={onRefresh}
              disabled={status === "syncing"}
              sx={{ borderRadius: tokens.radius.btn }}
            >
              <RefreshCw size={13} />
            </IconButton>
          </span>
        </Tooltip>
      </Stack>
      <Popover
        open={Boolean(varsAnchor)}
        anchorEl={varsAnchor}
        onClose={() => setVarsAnchor(null)}
        anchorOrigin={{ vertical: "bottom", horizontal: "right" }}
        transformOrigin={{ vertical: "top", horizontal: "right" }}
        slotProps={{
          paper: {
            sx: {
              p: 2,
              mt: 0.5,
              width: 320,
              borderRadius: tokens.radius.card,
              border: "1px solid",
              borderColor: "divider",
            },
          },
        }}
      >
        <Typography
          variant="subtitle2"
          sx={{
            fontWeight: 600,
            mb: 1.25
          }}>
          Sample variables
        </Typography>
        <Stack spacing={1.25}>
          {sortedVarKeys.map((name) => (
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
                  fontFamily: "ui-monospace, SFMono-Regular, Menlo, monospace",
                  fontSize: "0.78rem",
                },
                "& .MuiInputBase-input": {
                  fontFamily: "ui-monospace, SFMono-Regular, Menlo, monospace",
                  fontSize: "0.82rem",
                },
              }}
            />
          ))}
        </Stack>
        <Typography
          variant="caption"
          sx={{
            color: "text.secondary",
            display: "block",
            mt: 1.5,
            fontStyle: "italic"
          }}>
          Changes apply automatically after a short pause.
        </Typography>
      </Popover>
      <Box sx={{ flex: 1, minHeight: 0, position: "relative" }}>
        {showError ? (
          <Box sx={{ p: 1.5 }}>
            <Alert severity="error" variant="outlined">
              {error}
            </Alert>
          </Box>
        ) : !result ? (
          <Box
            sx={{
              p: 2,
              color: "text.disabled",
              fontSize: "0.85rem",
              fontStyle: "italic",
            }}
          >
            Rendering…
          </Box>
        ) : mode === "subject" ? (
          <Box
            sx={{
              px: 1.5,
              py: 1.25,
              fontFamily: "ui-monospace, SFMono-Regular, Menlo, monospace",
              fontSize: "0.9rem",
              color: "text.primary",
              overflow: "hidden",
              textOverflow: "ellipsis",
              whiteSpace: "nowrap",
            }}
            title={renderedValue ?? ""}
          >
            {renderedValue}
          </Box>
        ) : mode === "plain" ? (
          <Box
            component="pre"
            sx={{
              m: 0,
              p: 2,
              height: "100%",
              fontFamily: "ui-monospace, SFMono-Regular, Menlo, monospace",
              fontSize: "0.825rem",
              lineHeight: 1.55,
              whiteSpace: "pre-wrap",
              wordBreak: "break-word",
              color: "text.primary",
              overflow: "auto",
            }}
          >
            {renderedValue}
          </Box>
        ) : renderedValue ? (
          <Box
            component="iframe"
            title={ariaLabel}
            // sandbox left empty → most restrictive: no scripts, no
            // same-origin, no forms. Pasted <script> tags render as
            // inert text.
            sandbox=""
            srcDoc={renderedValue}
            sx={{
              display: "block",
              width: "100%",
              height: "100%",
              minHeight,
              border: 0,
              background: tokens.color.white,
            }}
          />
        ) : (
          <Box
            sx={{
              p: 2,
              color: "text.disabled",
              fontSize: "0.85rem",
              fontStyle: "italic",
            }}
          >
            No HTML body to preview.
          </Box>
        )}
      </Box>
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
      spacing={0.5}
      role="status"
      aria-live="polite"
      aria-label={`Preview status: ${meta.label}`}
      sx={{
        alignItems: "center",
        px: 0.875,
        py: 0.125,
        borderRadius: 999,
        background: isDark ? alpha(meta.color, 0.18) : alpha(meta.color, 0.12),
        color: meta.color,
        fontSize: "0.65rem",
        fontWeight: 700,
        letterSpacing: 0.5,
        textTransform: "uppercase",
        userSelect: "none",
        flexShrink: 0
      }}>
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
          width: 5,
          height: 5,
          borderRadius: "50%",
          background: "currentColor",
        }}
      />
    ),
  },
  syncing: {
    label: "Syncing",
    color: tokens.color.primary,
    icon: <Loader2 size={9} />,
  },
  live: {
    label: "Live",
    color: tokens.color.success,
    icon: (
      <Box
        sx={{
          width: 5,
          height: 5,
          borderRadius: "50%",
          background: "currentColor",
        }}
      />
    ),
  },
  error: {
    label: "Error",
    color: tokens.color.danger,
    icon: <AlertCircle size={9} />,
  },
};
