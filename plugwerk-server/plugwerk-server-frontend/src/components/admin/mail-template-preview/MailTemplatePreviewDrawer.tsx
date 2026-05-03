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
import { useCallback, useEffect, useMemo, useState } from "react";
import {
  Alert,
  Box,
  Button,
  CircularProgress,
  Drawer,
  IconButton,
  Stack,
  Tab,
  Tabs,
  TextField,
  Typography,
  alpha,
  useTheme,
} from "@mui/material";
import { ChevronDown, ChevronUp, RefreshCw, Variable, X } from "lucide-react";
import axios from "axios";
import { tokens } from "../../../theme/tokens";
import { useEmailTemplatesStore } from "../../../stores/emailTemplatesStore";
import type { MailTemplatePreviewResponse } from "../../../api/generated/model/mail-template-preview-response";

interface MailTemplatePreviewDrawerProps {
  open: boolean;
  onClose: () => void;
  templateKey: string;
  templateFriendlyName: string;
  /** Current draft from the editor — re-rendered on every refresh. */
  draft: {
    subject: string;
    bodyPlain: string;
    bodyHtml: string | null;
  };
  /** Names from the registry; the sample-vars form fields one input per entry. */
  placeholders: readonly string[];
}

type TabKey = "plain" | "html";

function extractApiError(error: unknown): string {
  if (axios.isAxiosError(error)) {
    const message = (error.response?.data as { message?: string } | undefined)
      ?.message;
    if (typeof message === "string" && message.length > 0) return message;
    return error.message;
  }
  if (error instanceof Error) return error.message;
  return "Preview failed.";
}

/**
 * Right-side drawer that calls `POST /admin/email/templates/{key}/preview`
 * and renders the response.
 *
 * - Plaintext output goes into a `<pre>` so whitespace and line breaks
 *   survive intact
 * - HTML output renders inside a sandboxed `<iframe srcDoc>` — no
 *   `allow-scripts`, no `allow-same-origin`, no `allow-forms`. Even if an
 *   admin pastes `<script>` tags into the body, the preview is inert
 * - Sample-vars form lets the operator override individual placeholder
 *   values; clicking Refresh re-runs the preview against the new map
 *
 * The preview fires once when the drawer opens with the registry-default
 * sample vars. Subsequent refreshes are explicit (button click) so the
 * admin sees what they asked for, not a flickering live render.
 */
export function MailTemplatePreviewDrawer({
  open,
  onClose,
  templateKey,
  templateFriendlyName,
  draft,
  placeholders,
}: MailTemplatePreviewDrawerProps) {
  const theme = useTheme();
  const isDark = theme.palette.mode === "dark";
  const preview = useEmailTemplatesStore((s) => s.preview);

  const [tab, setTab] = useState<TabKey>("plain");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<MailTemplatePreviewResponse | null>(
    null,
  );
  const [sampleVars, setSampleVars] = useState<Record<string, string>>({});
  const [varsExpanded, setVarsExpanded] = useState(false);

  // Build the request body from the latest draft + the (possibly empty)
  // sample-vars overrides. `null` for `bodyHtml` becomes `undefined` so
  // the generated TS client omits the field rather than sending JSON null,
  // matching the contract on the backend (both are treated equally there).
  const buildRequest = useCallback(
    (overrides: Record<string, string>) => ({
      subject: draft.subject,
      bodyPlain: draft.bodyPlain,
      bodyHtml: draft.bodyHtml ?? undefined,
      sampleVars: Object.keys(overrides).length > 0 ? overrides : undefined,
    }),
    [draft.subject, draft.bodyPlain, draft.bodyHtml],
  );

  const runPreview = useCallback(
    async (overrides: Record<string, string>) => {
      setLoading(true);
      setError(null);
      try {
        const next = await preview(templateKey, buildRequest(overrides));
        setResult(next);
        // Seed the editable sample-vars form from the server's effective
        // map on the first successful preview, so the inputs show what
        // values were actually used (and the operator can tweak them).
        setSampleVars((prev) =>
          Object.keys(prev).length === 0 ? { ...next.sampleVars } : prev,
        );
        // Auto-switch to the HTML tab when the draft has an HTML body
        // and the operator hasn't explicitly switched away — most of
        // the value of the preview is verifying the HTML render.
      } catch (err) {
        setError(extractApiError(err));
        setResult(null);
      } finally {
        setLoading(false);
      }
    },
    [preview, templateKey, buildRequest],
  );

  // Fire the initial preview when the drawer opens, and again whenever
  // the draft text changes while the drawer is open (the admin edited
  // and clicked Preview again on a fresh open).
  useEffect(() => {
    if (open) {
      void runPreview(sampleVars);
    }
    // Intentionally not including sampleVars in the dep array — refresh
    // is triggered manually via the Refresh button after the operator
    // edits the form. We do re-run when the draft text changes (an open
    // drawer is the operator's preview window; let it stay accurate).
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, draft.subject, draft.bodyPlain, draft.bodyHtml]);

  // Default tab to HTML iff the draft has an HTML body, otherwise plain.
  // Done as an effect rather than initial state so it follows the toggle
  // in the parent — turning HTML off mid-session falls back to plain.
  useEffect(() => {
    if (!open) return;
    setTab(draft.bodyHtml ? "html" : "plain");
  }, [open, draft.bodyHtml]);

  const hasHtml = result?.bodyHtml != null && result.bodyHtml.length > 0;
  const sampleVarKeys = useMemo(() => [...placeholders].sort(), [placeholders]);

  return (
    <Drawer
      anchor="right"
      open={open}
      onClose={onClose}
      PaperProps={{
        sx: {
          width: { xs: "100%", sm: 560, md: 640 },
          background: isDark ? tokens.color.gray100 : tokens.color.white,
        },
      }}
    >
      <Stack sx={{ height: "100%" }}>
        <Box
          sx={{
            display: "flex",
            alignItems: "center",
            gap: 1.5,
            px: 3,
            py: 2,
            borderBottom: "1px solid",
            borderColor: "divider",
            background: isDark ? alpha("#ffffff", 0.03) : tokens.color.gray10,
          }}
        >
          <Box sx={{ flex: 1, minWidth: 0 }}>
            <Typography
              variant="caption"
              color="text.secondary"
              sx={{
                display: "block",
                textTransform: "uppercase",
                letterSpacing: 0.6,
              }}
            >
              Preview · {templateFriendlyName}
            </Typography>
            <Typography
              variant="subtitle1"
              fontWeight={600}
              sx={{
                lineHeight: 1.3,
                overflow: "hidden",
                textOverflow: "ellipsis",
                whiteSpace: "nowrap",
              }}
              title={result?.subject ?? draft.subject}
            >
              {result?.subject ?? draft.subject}
            </Typography>
          </Box>
          <IconButton
            size="small"
            aria-label="Refresh preview"
            onClick={() => runPreview(sampleVars)}
            disabled={loading}
            sx={{ borderRadius: tokens.radius.btn }}
          >
            {loading ? <CircularProgress size={16} /> : <RefreshCw size={16} />}
          </IconButton>
          <IconButton
            size="small"
            aria-label="Close preview"
            onClick={onClose}
            sx={{ borderRadius: tokens.radius.btn }}
          >
            <X size={16} />
          </IconButton>
        </Box>

        <Tabs
          value={tab}
          onChange={(_, value: TabKey) => setTab(value)}
          aria-label="Preview body type"
          sx={{ borderBottom: 1, borderColor: "divider", px: 1 }}
        >
          <Tab label="Plaintext" value="plain" />
          <Tab
            label="HTML"
            value="html"
            disabled={!hasHtml}
            sx={{ "&.Mui-disabled": { opacity: 0.4 } }}
          />
        </Tabs>

        <Box sx={{ flex: 1, overflow: "auto", p: 3 }}>
          {error && (
            <Alert severity="error" sx={{ mb: 2 }}>
              {error}
            </Alert>
          )}
          {!result && !error && loading && (
            <Stack
              direction="row"
              spacing={1}
              alignItems="center"
              sx={{ color: "text.secondary" }}
            >
              <CircularProgress size={16} />
              <Typography variant="body2">Rendering preview…</Typography>
            </Stack>
          )}
          {result && tab === "plain" && (
            <Box
              component="pre"
              sx={{
                m: 0,
                p: 2,
                borderRadius: tokens.radius.input,
                background: isDark
                  ? alpha("#ffffff", 0.03)
                  : tokens.color.gray10,
                fontFamily: "ui-monospace, SFMono-Regular, Menlo, monospace",
                fontSize: "0.875rem",
                lineHeight: 1.55,
                whiteSpace: "pre-wrap",
                wordBreak: "break-word",
                color: "text.primary",
              }}
            >
              {result.bodyPlain}
            </Box>
          )}
          {result && tab === "html" && hasHtml && (
            <Box
              sx={{
                border: "1px solid",
                borderColor: "divider",
                borderRadius: tokens.radius.input,
                overflow: "hidden",
                background: tokens.color.white,
              }}
            >
              <Box
                component="iframe"
                title="HTML preview"
                // sandbox left empty → most restrictive: no scripts, no
                // same-origin, no forms, no top-navigation. Pasted
                // <script> tags render as inert text.
                sandbox=""
                srcDoc={result.bodyHtml ?? ""}
                sx={{
                  display: "block",
                  width: "100%",
                  minHeight: 480,
                  border: 0,
                }}
              />
            </Box>
          )}
        </Box>

        <Box
          sx={{
            borderTop: "1px solid",
            borderColor: "divider",
            background: isDark ? alpha("#ffffff", 0.03) : tokens.color.gray10,
          }}
        >
          <Button
            fullWidth
            variant="text"
            onClick={() => setVarsExpanded((v) => !v)}
            startIcon={<Variable size={16} />}
            endIcon={
              varsExpanded ? <ChevronDown size={16} /> : <ChevronUp size={16} />
            }
            sx={{
              justifyContent: "flex-start",
              py: 1.5,
              px: 3,
              borderRadius: 0,
              color: "text.secondary",
              fontWeight: 500,
            }}
          >
            Sample variables ({Object.keys(sampleVars).length})
          </Button>
          {varsExpanded && (
            <Box sx={{ px: 3, pb: 3, pt: 0.5 }}>
              <Stack spacing={1.5}>
                {sampleVarKeys.map((name) => (
                  <TextField
                    key={name}
                    label={`{{${name}}}`}
                    size="small"
                    value={sampleVars[name] ?? ""}
                    onChange={(e) => {
                      const next = { ...sampleVars, [name]: e.target.value };
                      setSampleVars(next);
                    }}
                    sx={{
                      "& .MuiInputLabel-root": {
                        fontFamily:
                          "ui-monospace, SFMono-Regular, Menlo, monospace",
                      },
                    }}
                  />
                ))}
                <Button
                  variant="outlined"
                  startIcon={<RefreshCw size={16} />}
                  onClick={() => runPreview(sampleVars)}
                  disabled={loading}
                  sx={{
                    alignSelf: "flex-start",
                    borderRadius: tokens.radius.btn,
                  }}
                >
                  Refresh with these values
                </Button>
              </Stack>
            </Box>
          )}
        </Box>
      </Stack>
    </Drawer>
  );
}
