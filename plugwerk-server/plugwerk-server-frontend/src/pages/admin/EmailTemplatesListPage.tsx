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
import { useEffect } from "react";
import {
  Alert,
  Box,
  Chip,
  CircularProgress,
  Stack,
  Typography,
  alpha,
  useTheme,
} from "@mui/material";
import { ChevronRight, FileText } from "lucide-react";
import { useNavigate } from "react-router-dom";
import { useEmailTemplatesStore } from "../../stores/emailTemplatesStore";
import { useUiStore } from "../../stores/uiStore";
import { tokens } from "../../theme/tokens";
import type { MailTemplateResponse } from "../../api/generated/model/mail-template-response";
import { MailTemplateResponseSourceEnum } from "../../api/generated/model/mail-template-response";

/**
 * List view for the `/admin/email/templates` admin area (#438).
 *
 * The registry is closed — this page never renders an "add template" CTA.
 * Each row is a navigable, full-width press target (touch-friendly, also
 * keyboard-accessible via `role="button"`). The Customised chip is the
 * only signal of override state; rows that fall through to enum defaults
 * stay quiet and unlabelled, mirroring the editorial principle of "noise
 * only when there's something to say".
 */
export function EmailTemplatesListPage() {
  const navigate = useNavigate();
  const templates = useEmailTemplatesStore((s) => s.templates);
  const loaded = useEmailTemplatesStore((s) => s.loaded);
  const loading = useEmailTemplatesStore((s) => s.loading);
  const error = useEmailTemplatesStore((s) => s.error);
  const load = useEmailTemplatesStore((s) => s.load);
  const addToast = useUiStore((s) => s.addToast);

  useEffect(() => {
    if (!loaded && !loading) {
      void load().catch(() => {
        addToast({ type: "error", message: "Failed to load mail templates." });
      });
    }
  }, [loaded, loading, load, addToast]);

  if (!loaded && loading) {
    return (
      <Box sx={{ display: "flex", alignItems: "center", gap: 1, py: 4 }}>
        <CircularProgress size={18} />
        <Typography variant="body2">Loading templates…</Typography>
      </Box>
    );
  }

  if (error && templates.length === 0) {
    return <Alert severity="error">{error}</Alert>;
  }

  return (
    <Stack spacing={2}>
      {templates.map((template) => (
        <TemplateRow
          key={template.key}
          template={template}
          onClick={() =>
            navigate(
              `/admin/email/templates/${encodeURIComponent(template.key)}`,
            )
          }
        />
      ))}
    </Stack>
  );
}

interface TemplateRowProps {
  template: MailTemplateResponse;
  onClick: () => void;
}

function TemplateRow({ template, onClick }: TemplateRowProps) {
  const theme = useTheme();
  const isDark = theme.palette.mode === "dark";
  const isCustomised =
    template.source === MailTemplateResponseSourceEnum.Database;

  return (
    <Box
      role="button"
      tabIndex={0}
      onClick={onClick}
      onKeyDown={(e) => {
        if (e.key === "Enter" || e.key === " ") {
          e.preventDefault();
          onClick();
        }
      }}
      aria-label={`Edit ${template.friendlyName} template`}
      sx={{
        display: "grid",
        gridTemplateColumns: "minmax(0, 1fr) auto auto",
        alignItems: "center",
        columnGap: 3,
        rowGap: 1,
        px: 3,
        py: 2.25,
        cursor: "pointer",
        border: "1px solid",
        borderColor: "divider",
        borderRadius: tokens.radius.card,
        background: isDark ? alpha("#ffffff", 0.02) : tokens.color.white,
        transition:
          "transform 120ms ease, border-color 120ms ease, box-shadow 120ms ease",
        "&:hover, &:focus-visible": {
          borderColor: tokens.color.primary,
          boxShadow: tokens.shadow.cardHover,
          outline: "none",
          transform: "translateY(-1px)",
          "& .row-arrow": { transform: "translateX(2px)", opacity: 1 },
        },
        "&:focus-visible": {
          boxShadow: `0 0 0 3px ${alpha(tokens.color.primary, 0.25)}`,
        },
      }}
    >
      <Box sx={{ minWidth: 0 }}>
        <Stack direction="row" spacing={1.5} alignItems="center" mb={0.5}>
          <Box sx={{ color: "text.secondary", display: "flex" }}>
            <FileText size={16} />
          </Box>
          <Typography
            variant="subtitle1"
            fontWeight={600}
            sx={{ lineHeight: 1.2 }}
          >
            {template.friendlyName}
          </Typography>
        </Stack>
        <Typography
          variant="caption"
          color="text.secondary"
          sx={{
            fontFamily: "ui-monospace, SFMono-Regular, Menlo, monospace",
            display: "block",
            mb: 0.75,
            ml: 3.5,
          }}
        >
          {template.key}
        </Typography>
        <Typography
          variant="body2"
          color="text.secondary"
          sx={{
            ml: 3.5,
            overflow: "hidden",
            textOverflow: "ellipsis",
            display: "-webkit-box",
            WebkitLineClamp: 1,
            WebkitBoxOrient: "vertical",
          }}
        >
          {template.subject}
        </Typography>
      </Box>

      {isCustomised ? (
        <Chip
          label="Customised"
          size="small"
          sx={{
            bgcolor: tokens.badge.tag.bg,
            color: tokens.badge.tag.text,
            fontWeight: 600,
            letterSpacing: 0.2,
          }}
        />
      ) : (
        <Box sx={{ width: 0 }} />
      )}

      <Box
        className="row-arrow"
        sx={{
          color: "text.secondary",
          display: "flex",
          opacity: 0.6,
          transition: "transform 160ms ease, opacity 160ms ease",
        }}
      >
        <ChevronRight size={18} />
      </Box>
    </Box>
  );
}
