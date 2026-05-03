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
import { useEffect, useMemo, useRef, useState, type RefObject } from "react";
import {
  Alert,
  Box,
  Button,
  Chip,
  CircularProgress,
  Collapse,
  Dialog,
  DialogActions,
  DialogContent,
  DialogContentText,
  DialogTitle,
  FormControlLabel,
  IconButton,
  Stack,
  Switch,
  TextField,
  Typography,
  alpha,
  useTheme,
} from "@mui/material";
import {
  AlertTriangle,
  ArrowLeft,
  ChevronDown,
  ChevronUp,
  Code2,
  FileText,
  RotateCcw,
  Variable,
} from "lucide-react";
import axios from "axios";
import { useNavigate, useParams } from "react-router-dom";
import { Section } from "../../components/common/Section";
import { useEmailTemplatesStore } from "../../stores/emailTemplatesStore";
import { useUiStore } from "../../stores/uiStore";
import { tokens } from "../../theme/tokens";
import type { MailTemplateResponse } from "../../api/generated/model/mail-template-response";
import { MailTemplateResponseSourceEnum } from "../../api/generated/model/mail-template-response";

const MUSTACHE_REFERENCE_URL = "https://mustache.github.io/mustache.5.html";

type DraftState = {
  subject: string;
  bodyPlain: string;
  bodyHtml: string | null;
};

function extractApiError(error: unknown): string {
  if (axios.isAxiosError(error)) {
    const message = (error.response?.data as { message?: string } | undefined)
      ?.message;
    if (typeof message === "string" && message.length > 0) return message;
    return error.message;
  }
  if (error instanceof Error) return error.message;
  return "Unknown error";
}

function diffsFromTemplate(template: MailTemplateResponse): DraftState {
  return {
    subject: template.subject,
    bodyPlain: template.bodyPlain,
    bodyHtml: template.bodyHtml ?? null,
  };
}

function isDirty(draft: DraftState, template: MailTemplateResponse): boolean {
  return (
    draft.subject !== template.subject ||
    draft.bodyPlain !== template.bodyPlain ||
    (draft.bodyHtml ?? null) !== (template.bodyHtml ?? null)
  );
}

/**
 * Single-template editor (#438).
 *
 * Surfaces three fields against the registered placeholder set: subject,
 * plaintext body (always required), HTML body (optional, opt-in via the
 * "Add HTML alternative" toggle). Each field is paired with a collapsible
 * "Compare with default" panel so admins can see what they're diverging
 * from without leaving the page.
 *
 * Mustache placeholder chips at the top are clickable — clicking a chip
 * inserts `{{name}}` (or `{{{name}}}` for the HTML body when shift-held)
 * into the most recently focused field at the cursor position. Saves a
 * lot of typo-prone hand-typing of `{{verificationLink}}`.
 */
export function EmailTemplateEditPage() {
  const params = useParams<{ key: string }>();
  const navigate = useNavigate();
  const templateKey = params.key ?? "";

  const templates = useEmailTemplatesStore((s) => s.templates);
  const loaded = useEmailTemplatesStore((s) => s.loaded);
  const loading = useEmailTemplatesStore((s) => s.loading);
  const saving = useEmailTemplatesStore((s) => s.saving);
  const load = useEmailTemplatesStore((s) => s.load);
  const update = useEmailTemplatesStore((s) => s.update);
  const reset = useEmailTemplatesStore((s) => s.reset);
  const addToast = useUiStore((s) => s.addToast);

  const template = useMemo(
    () => templates.find((t) => t.key === templateKey) ?? null,
    [templates, templateKey],
  );

  const [draft, setDraft] = useState<DraftState | null>(null);
  const [resetDialogOpen, setResetDialogOpen] = useState(false);
  const [showHtmlEditor, setShowHtmlEditor] = useState(false);

  const subjectRef = useRef<HTMLInputElement>(null);
  const bodyPlainRef = useRef<HTMLTextAreaElement>(null);
  const bodyHtmlRef = useRef<HTMLTextAreaElement>(null);
  const lastFocusedRef = useRef<"subject" | "bodyPlain" | "bodyHtml">(
    "bodyPlain",
  );

  useEffect(() => {
    if (!loaded && !loading) {
      void load().catch(() => {
        addToast({
          type: "error",
          message: "Failed to load mail templates.",
        });
      });
    }
  }, [loaded, loading, load, addToast]);

  // Sync draft from server snapshot on first render and after every save/reset
  // — but only when there are no in-flight user edits, so a background refresh
  // does not stomp typed-but-unsaved input.
  useEffect(() => {
    if (template && draft === null) {
      setDraft(diffsFromTemplate(template));
      setShowHtmlEditor(template.bodyHtml != null);
    }
  }, [template, draft]);

  if (!loaded && loading) {
    return (
      <Box sx={{ display: "flex", alignItems: "center", gap: 1, py: 4 }}>
        <CircularProgress size={18} />
        <Typography variant="body2">Loading template…</Typography>
      </Box>
    );
  }

  if (loaded && !template) {
    return (
      <Stack spacing={2}>
        <Alert severity="warning">
          No template registered with key{" "}
          <Box
            component="span"
            sx={{
              fontFamily: "ui-monospace, SFMono-Regular, Menlo, monospace",
            }}
          >
            {templateKey}
          </Box>
          . The registry is closed; only seeded templates can be edited.
        </Alert>
        <Box>
          <Button
            variant="text"
            startIcon={<ArrowLeft size={16} />}
            onClick={() => navigate("/admin/email/templates")}
            sx={{ borderRadius: tokens.radius.btn }}
          >
            Back to templates
          </Button>
        </Box>
      </Stack>
    );
  }

  if (!template || draft === null) {
    return null;
  }

  const dirty = isDirty(draft, template);
  const isCustomised =
    template.source === MailTemplateResponseSourceEnum.Database;
  const hasDefaultHtml = template.defaultBodyHtml != null;

  function setField<K extends keyof DraftState>(
    field: K,
    value: DraftState[K],
  ) {
    setDraft((prev) => (prev ? { ...prev, [field]: value } : prev));
  }

  function handleInsertPlaceholder(name: string, asRaw: boolean = false) {
    const target = lastFocusedRef.current;
    const refMap: Record<
      typeof target,
      RefObject<HTMLInputElement | HTMLTextAreaElement | null>
    > = {
      subject: subjectRef,
      bodyPlain: bodyPlainRef,
      bodyHtml: bodyHtmlRef,
    };
    const el = refMap[target].current;
    if (!el) return;
    const insertion =
      asRaw && target === "bodyHtml" ? `{{{${name}}}}` : `{{${name}}}`;

    const start = el.selectionStart ?? el.value.length;
    const end = el.selectionEnd ?? el.value.length;
    const current = el.value;
    const next = current.slice(0, start) + insertion + current.slice(end);

    if (target === "subject") setField("subject", next);
    else if (target === "bodyPlain") setField("bodyPlain", next);
    else if (target === "bodyHtml") setField("bodyHtml", next);

    // Restore caret just after the inserted token so successive clicks chain.
    requestAnimationFrame(() => {
      el.focus();
      const caret = start + insertion.length;
      el.setSelectionRange(caret, caret);
    });
  }

  async function handleSave() {
    if (!draft || !template) return;
    if (!draft.subject.trim()) {
      addToast({ type: "error", message: "Subject is required." });
      return;
    }
    if (!draft.bodyPlain.trim()) {
      addToast({ type: "error", message: "Plaintext body is required." });
      return;
    }
    // `undefined` (omit field) and explicit `null` are both treated as
    // "no HTML" by the backend; the generated TS client types bodyHtml as
    // optional (`string | undefined`) so we omit it rather than send null.
    const payloadHtml =
      showHtmlEditor && draft.bodyHtml?.trim() ? draft.bodyHtml : undefined;
    try {
      const next = await update(template.key, {
        subject: draft.subject,
        bodyPlain: draft.bodyPlain,
        bodyHtml: payloadHtml,
      });
      setDraft(diffsFromTemplate(next));
      setShowHtmlEditor(next.bodyHtml != null);
      addToast({ type: "success", message: "Template saved." });
    } catch (err) {
      addToast({ type: "error", message: extractApiError(err) });
    }
  }

  function handleDiscard() {
    setDraft(diffsFromTemplate(template!));
    setShowHtmlEditor(template!.bodyHtml != null);
  }

  async function handleResetConfirm() {
    if (!template) return;
    setResetDialogOpen(false);
    try {
      const next = await reset(template.key);
      // Re-seed the local draft from the post-reset snapshot so the form
      // shows the default values immediately. The mount-time `useEffect`
      // only seeds when `draft === null`, deliberately so background
      // refreshes do not stomp typed-but-unsaved input — that means reset,
      // like save, must wire its own draft refresh.
      setDraft(diffsFromTemplate(next));
      setShowHtmlEditor(next.bodyHtml != null);
      addToast({ type: "success", message: "Template reset to default." });
    } catch (err) {
      addToast({ type: "error", message: extractApiError(err) });
    }
  }

  return (
    <Stack spacing={3}>
      <Box>
        <Button
          variant="text"
          startIcon={<ArrowLeft size={16} />}
          onClick={() => navigate("/admin/email/templates")}
          sx={{
            borderRadius: tokens.radius.btn,
            ml: -1,
            color: "text.secondary",
            mb: 1,
          }}
          size="small"
        >
          All templates
        </Button>
        <Stack
          direction="row"
          alignItems="center"
          spacing={1.5}
          flexWrap="wrap"
          rowGap={1}
        >
          <Typography variant="h2" sx={{ lineHeight: 1.1 }}>
            {template.friendlyName}
          </Typography>
          {isCustomised && (
            <Chip
              label="Customised"
              size="small"
              sx={{
                bgcolor: tokens.badge.tag.bg,
                color: tokens.badge.tag.text,
                fontWeight: 600,
              }}
            />
          )}
        </Stack>
        <Typography
          variant="caption"
          color="text.secondary"
          sx={{
            fontFamily: "ui-monospace, SFMono-Regular, Menlo, monospace",
            display: "block",
            mt: 0.5,
          }}
        >
          {template.key} · {template.locale}
        </Typography>
      </Box>

      <PlaceholderReference
        placeholders={template.placeholders}
        onInsert={handleInsertPlaceholder}
      />

      <Section
        contentGap={1.5}
        icon={<FileText size={18} />}
        title="Subject"
        description="Single-line message subject. Mustache variables resolve at send time."
      >
        <TextField
          size="small"
          fullWidth
          value={draft.subject}
          onChange={(e) => setField("subject", e.target.value)}
          onFocus={() => {
            lastFocusedRef.current = "subject";
          }}
          inputRef={subjectRef}
          disabled={saving}
          inputProps={{ "aria-label": "Subject" }}
          sx={{
            "& .MuiInputBase-input": {
              fontFamily: "ui-monospace, SFMono-Regular, Menlo, monospace",
              fontSize: "0.9rem",
            },
          }}
        />
        <DefaultDiff label="Default subject" value={template.defaultSubject} />
      </Section>

      <Section
        contentGap={1.5}
        icon={<FileText size={18} />}
        title="Plaintext body"
        description="Always required. Sent as text/plain to mail clients without HTML support."
      >
        <TextField
          multiline
          fullWidth
          minRows={12}
          maxRows={30}
          value={draft.bodyPlain}
          onChange={(e) => setField("bodyPlain", e.target.value)}
          onFocus={() => {
            lastFocusedRef.current = "bodyPlain";
          }}
          inputRef={bodyPlainRef}
          disabled={saving}
          inputProps={{ "aria-label": "Plaintext body" }}
          sx={{
            "& .MuiInputBase-input": {
              fontFamily: "ui-monospace, SFMono-Regular, Menlo, monospace",
              fontSize: "0.85rem",
              lineHeight: 1.55,
            },
          }}
        />
        <DefaultDiff
          label="Default plaintext body"
          value={template.defaultBodyPlain}
          monospace
          multiline
        />
      </Section>

      <Section
        contentGap={1.5}
        icon={<Code2 size={18} />}
        title="HTML body"
        description={
          showHtmlEditor
            ? "Optional. When set, sends multipart/alternative so HTML-capable clients render the rich version."
            : "Optional. Toggle on to add an HTML alternative for HTML-capable clients."
        }
      >
        <FormControlLabel
          sx={{ mt: -0.5 }}
          control={
            <Switch
              checked={showHtmlEditor}
              onChange={(e) => {
                const next = e.target.checked;
                setShowHtmlEditor(next);
                if (next && !draft.bodyHtml) {
                  // Seed the editor with the enum default when adding HTML
                  // for the first time — gives the admin a working starting
                  // point rather than a blank canvas.
                  setField("bodyHtml", template.defaultBodyHtml ?? "");
                }
                if (!next) {
                  setField("bodyHtml", null);
                }
              }}
              disabled={saving}
              inputProps={{ "aria-label": "Add HTML alternative" }}
            />
          }
          label={showHtmlEditor ? "HTML alternative enabled" : "Plaintext only"}
        />
        <Collapse in={showHtmlEditor} timeout={200} unmountOnExit>
          <Stack spacing={1.5} sx={{ mt: 1 }}>
            <TextField
              multiline
              fullWidth
              minRows={14}
              maxRows={36}
              value={draft.bodyHtml ?? ""}
              onChange={(e) => setField("bodyHtml", e.target.value)}
              onFocus={() => {
                lastFocusedRef.current = "bodyHtml";
              }}
              inputRef={bodyHtmlRef}
              disabled={saving}
              placeholder={
                hasDefaultHtml
                  ? "Toggling on seeded the enum default — edit to taste."
                  : "<p>Hello {{username}}, ...</p>"
              }
              inputProps={{ "aria-label": "HTML body" }}
              sx={{
                "& .MuiInputBase-input": {
                  fontFamily: "ui-monospace, SFMono-Regular, Menlo, monospace",
                  fontSize: "0.85rem",
                  lineHeight: 1.55,
                },
              }}
            />
            {hasDefaultHtml && (
              <DefaultDiff
                label="Default HTML body"
                value={template.defaultBodyHtml ?? ""}
                monospace
                multiline
              />
            )}
          </Stack>
        </Collapse>
      </Section>

      <Box
        sx={{
          display: "flex",
          flexDirection: { xs: "column", sm: "row" },
          gap: 2,
          justifyContent: "space-between",
          alignItems: { xs: "stretch", sm: "center" },
        }}
      >
        <Button
          variant="text"
          color="error"
          startIcon={<RotateCcw size={16} />}
          onClick={() => setResetDialogOpen(true)}
          disabled={!isCustomised || saving}
          sx={{ borderRadius: tokens.radius.btn, alignSelf: "flex-start" }}
        >
          Reset to default
        </Button>
        <Box sx={{ display: "flex", gap: 2, justifyContent: "flex-end" }}>
          <Button
            variant="text"
            onClick={handleDiscard}
            disabled={!dirty || saving}
            sx={{ borderRadius: tokens.radius.btn }}
          >
            Discard
          </Button>
          <Button
            variant="contained"
            onClick={handleSave}
            disabled={!dirty || saving}
            sx={{ borderRadius: tokens.radius.btn }}
          >
            {saving ? "Saving…" : "Save Changes"}
          </Button>
        </Box>
      </Box>

      <ResetConfirmDialog
        open={resetDialogOpen}
        templateName={template.friendlyName}
        onClose={() => setResetDialogOpen(false)}
        onConfirm={handleResetConfirm}
      />
    </Stack>
  );
}

interface PlaceholderReferenceProps {
  placeholders: string[];
  onInsert: (name: string, asRaw?: boolean) => void;
}

function PlaceholderReference({
  placeholders,
  onInsert,
}: PlaceholderReferenceProps) {
  const theme = useTheme();
  const isDark = theme.palette.mode === "dark";

  return (
    <Box
      sx={{
        border: "1px solid",
        borderColor: "divider",
        borderRadius: tokens.radius.card,
        background: isDark
          ? alpha(tokens.color.primary, 0.08)
          : tokens.badge.tag.bg,
        px: 3,
        py: 2,
      }}
    >
      <Stack
        direction="row"
        spacing={1.5}
        alignItems="flex-start"
        flexWrap="wrap"
      >
        <Box
          sx={{
            color: isDark
              ? tokens.color.primaryLight
              : tokens.color.primaryDark,
            display: "flex",
            mt: 0.25,
          }}
        >
          <Variable size={18} />
        </Box>
        <Box sx={{ flex: 1, minWidth: 240 }}>
          <Typography
            variant="subtitle2"
            sx={{
              color: isDark
                ? tokens.color.primaryLight
                : tokens.color.primaryDark,
              fontWeight: 600,
              mb: 0.5,
            }}
          >
            Available variables
          </Typography>
          <Typography variant="caption" color="text.secondary">
            Click a chip to insert{" "}
            <Box
              component="code"
              sx={{
                fontFamily: "ui-monospace, SFMono-Regular, Menlo, monospace",
                px: 0.5,
                borderRadius: 0.5,
                background: isDark
                  ? alpha("#ffffff", 0.08)
                  : alpha("#000000", 0.06),
              }}
            >
              {"{{name}}"}
            </Box>{" "}
            at the caret.{" "}
            <Box
              component="a"
              href={MUSTACHE_REFERENCE_URL}
              target="_blank"
              rel="noopener noreferrer"
              sx={{ color: "inherit", textDecoration: "underline" }}
            >
              Mustache syntax reference
            </Box>
            .
          </Typography>
          {placeholders.length > 0 ? (
            <Stack
              direction="row"
              spacing={0.75}
              flexWrap="wrap"
              rowGap={0.75}
              sx={{ mt: 1.5 }}
            >
              {placeholders.map((name) => (
                <Chip
                  key={name}
                  label={`{{${name}}}`}
                  size="small"
                  onClick={() => onInsert(name)}
                  sx={{
                    fontFamily:
                      "ui-monospace, SFMono-Regular, Menlo, monospace",
                    bgcolor: isDark
                      ? alpha("#ffffff", 0.06)
                      : tokens.color.white,
                    color: isDark
                      ? tokens.color.primaryLight
                      : tokens.color.primaryDark,
                    border: "1px solid",
                    borderColor: isDark
                      ? alpha(tokens.color.primaryLight, 0.4)
                      : alpha(tokens.color.primary, 0.35),
                    cursor: "pointer",
                    "&:hover": {
                      bgcolor: isDark
                        ? alpha("#ffffff", 0.12)
                        : alpha(tokens.color.primary, 0.08),
                    },
                  }}
                />
              ))}
            </Stack>
          ) : (
            <Typography
              variant="caption"
              color="text.secondary"
              sx={{ mt: 1.5, display: "block", fontStyle: "italic" }}
            >
              This template takes no variables.
            </Typography>
          )}
        </Box>
      </Stack>
    </Box>
  );
}

interface DefaultDiffProps {
  label: string;
  value: string;
  monospace?: boolean;
  multiline?: boolean;
}

function DefaultDiff({ label, value, monospace, multiline }: DefaultDiffProps) {
  const [open, setOpen] = useState(false);
  const theme = useTheme();
  const isDark = theme.palette.mode === "dark";

  return (
    <Box>
      <IconButton
        size="small"
        onClick={() => setOpen((v) => !v)}
        sx={{
          ml: -1,
          color: "text.secondary",
          fontSize: "0.75rem",
          fontWeight: 500,
          gap: 0.5,
          borderRadius: tokens.radius.btn,
          px: 1,
          "&:hover": { background: alpha(tokens.color.primary, 0.06) },
        }}
        aria-expanded={open}
        aria-label={open ? `Hide ${label}` : `Show ${label}`}
      >
        {open ? <ChevronUp size={14} /> : <ChevronDown size={14} />}
        <Typography variant="caption" sx={{ ml: 0.5 }}>
          {open ? "Hide" : "Compare with"} {label.toLowerCase()}
        </Typography>
      </IconButton>
      <Collapse in={open} timeout={180}>
        <Box
          component="pre"
          sx={{
            mt: 1,
            mb: 0,
            p: 2,
            border: "1px dashed",
            borderColor: "divider",
            borderRadius: tokens.radius.input,
            background: isDark ? alpha("#ffffff", 0.03) : tokens.color.gray10,
            color: "text.secondary",
            fontFamily: monospace
              ? "ui-monospace, SFMono-Regular, Menlo, monospace"
              : "inherit",
            fontSize: monospace ? "0.8rem" : "0.85rem",
            lineHeight: 1.55,
            whiteSpace: multiline ? "pre-wrap" : "pre",
            overflowX: "auto",
            maxHeight: multiline ? 320 : "auto",
          }}
        >
          {value}
        </Box>
      </Collapse>
    </Box>
  );
}

interface ResetConfirmDialogProps {
  open: boolean;
  templateName: string;
  onClose: () => void;
  onConfirm: () => void;
}

function ResetConfirmDialog({
  open,
  templateName,
  onClose,
  onConfirm,
}: ResetConfirmDialogProps) {
  return (
    <Dialog
      open={open}
      onClose={onClose}
      aria-labelledby="reset-template-title"
      PaperProps={{
        sx: { borderRadius: tokens.radius.dialog, maxWidth: 480 },
      }}
    >
      <DialogTitle
        id="reset-template-title"
        sx={{ display: "flex", alignItems: "center", gap: 1.5, pb: 1 }}
      >
        <Box sx={{ color: tokens.color.danger, display: "flex" }}>
          <AlertTriangle size={20} />
        </Box>
        Reset “{templateName}” to default?
      </DialogTitle>
      <DialogContent>
        <DialogContentText>
          This removes your override and reverts the template to its seeded enum
          default. Future code releases that change the default will then take
          effect automatically. The action cannot be undone — you would need to
          re-enter your custom subject and body to restore them.
        </DialogContentText>
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2.5 }}>
        <Button
          onClick={onClose}
          variant="text"
          sx={{ borderRadius: tokens.radius.btn }}
        >
          Cancel
        </Button>
        <Button
          onClick={onConfirm}
          variant="contained"
          color="error"
          sx={{ borderRadius: tokens.radius.btn }}
          autoFocus
        >
          Reset template
        </Button>
      </DialogActions>
    </Dialog>
  );
}
