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
import { Alert, Box, Stack, Typography, alpha, useTheme } from "@mui/material";
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
}

/**
 * One read-only render output paired side-by-side with its editor (#438).
 *
 * Three modes — `subject` shows the rendered single-line subject in a
 * borrowed input-style box, `plain` shows the rendered plaintext body in
 * a `<pre>`, `html` renders the body inside a sandboxed iframe (no
 * scripts, no same-origin, no forms — pasted `<script>` tags render as
 * inert text).
 *
 * The pane intentionally has no toolbar of its own; status, refresh, and
 * sample-vars all live in the page-level [MailTemplatePreviewToolbar] so
 * the editor↔preview pairing reads as one unit per row.
 *
 * Width-overflowing draft input (long URLs, wide HTML lines) gets
 * clipped horizontally with overflow: auto so a 4000-character line can
 * never push the page-level grid sideways.
 */
export function MailTemplatePreviewPane({
  mode,
  result,
  status,
  error,
  minHeight,
  ariaLabel,
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
        alignItems="center"
        spacing={0.75}
        sx={{
          px: 1.5,
          py: 0.5,
          borderBottom: "1px solid",
          borderColor: "divider",
          background: isDark
            ? alpha("#ffffff", 0.04)
            : alpha(tokens.color.gray20, 0.4),
        }}
      >
        <Typography
          variant="caption"
          sx={{
            fontWeight: 600,
            color: "text.secondary",
            textTransform: "uppercase",
            letterSpacing: 0.6,
            fontSize: "0.65rem",
          }}
        >
          Preview
        </Typography>
      </Stack>
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
