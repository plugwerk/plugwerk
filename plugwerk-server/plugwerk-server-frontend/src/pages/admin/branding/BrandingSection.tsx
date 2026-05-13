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
import { Box, Button, Chip, Typography } from "@mui/material";
import { useDropzone } from "react-dropzone";
import { ImagePlus, RotateCcw } from "lucide-react";
import { Section } from "../../../components/common/Section";
import { tokens } from "../../../theme/tokens";
import { adminBrandingApi } from "../../../api/config";
import { useUiStore } from "../../../stores/uiStore";

/**
 * Three-slot branding upload UI for the global-settings page (#254).
 * Operator drops a file → server validates → live preview swaps to the
 * new version. "Reset" goes back to the bundled default.
 *
 * Cropping is intentionally out of scope for this first iteration —
 * the server enforces aspect tolerance and the dropzone help text
 * tells the operator what shape to provide. A follow-up issue can
 * add an in-product cropper if support traffic justifies it.
 */
interface SlotDescriptor {
  readonly slot: "logo-light" | "logo-dark" | "logomark";
  readonly label: string;
  readonly help: string;
  readonly previewBg: string;
  readonly previewAspect: string;
  readonly previewSize: string;
  readonly bundledFallback: string;
}

const SLOTS: readonly SlotDescriptor[] = [
  {
    slot: "logo-light",
    label: "Light logo",
    help: "SVG / PNG / WebP, max 512 KB, ~4:1 aspect (e.g. 800×200). Rendered in the top bar (light theme), the login page, and email headers.",
    previewBg: tokens.color.white,
    previewAspect: "4 / 1",
    previewSize: "240px",
    bundledFallback: "/logo-light.svg",
  },
  {
    slot: "logo-dark",
    label: "Dark logo",
    help: "SVG / PNG / WebP, max 512 KB, ~4:1 aspect. Rendered in the top bar (dark theme) and dark-themed emails.",
    previewBg: tokens.color.gray100,
    previewAspect: "4 / 1",
    previewSize: "240px",
    bundledFallback: "/logo-dark.svg",
  },
  {
    slot: "logomark",
    label: "Logomark",
    help: "SVG / PNG / WebP, max 256 KB, square (1:1, e.g. 512×512). Used as favicon, OG image, and the mobile / collapsed top bar.",
    previewBg: tokens.color.gray10,
    previewAspect: "1 / 1",
    previewSize: "128px",
    bundledFallback: "/logomark.svg",
  },
];

export function BrandingSection() {
  return (
    <Section
      icon={<ImagePlus size={18} />}
      title="Branding"
      description="Replace the default Plugwerk logos with your own. Independent slots — missing slots fall back to the bundled defaults."
      contentGap={3}
    >
      <Box
        sx={{
          display: "grid",
          gridTemplateColumns: { xs: "1fr", md: "repeat(3, 1fr)" },
          gap: 3,
        }}
      >
        {SLOTS.map((s) => (
          <BrandingSlotCard key={s.slot} descriptor={s} />
        ))}
      </Box>
    </Section>
  );
}

function BrandingSlotCard({
  descriptor,
}: {
  readonly descriptor: SlotDescriptor;
}) {
  const [version, setVersion] = useState(0);
  const [hasCustom, setHasCustom] = useState<boolean | null>(null);
  const [busy, setBusy] = useState(false);
  const addToast = useUiStore((s) => s.addToast);

  // Probe the public endpoint on mount to learn whether this slot
  // currently carries a custom asset. 404 = default; 200 = custom.
  useEffect(() => {
    let cancelled = false;
    fetch(`/api/v1/branding/${descriptor.slot}`, { method: "HEAD" })
      .then((res) => {
        if (cancelled) return;
        setHasCustom(res.ok);
      })
      .catch(() => {
        if (!cancelled) setHasCustom(false);
      });
    return () => {
      cancelled = true;
    };
  }, [descriptor.slot, version]);

  const upload = useCallback(
    async (file: File) => {
      setBusy(true);
      try {
        await adminBrandingApi.uploadBrandingAsset({
          slot: descriptor.slot,
          file,
        });
        setVersion((v) => v + 1);
        addToast({ message: `${descriptor.label} updated.`, type: "success" });
      } catch (err: unknown) {
        const message =
          (err as { response?: { data?: { message?: string } } }).response?.data
            ?.message ?? "Upload failed.";
        addToast({ message, type: "error" });
      } finally {
        setBusy(false);
      }
    },
    [addToast, descriptor.label, descriptor.slot],
  );

  const reset = useCallback(async () => {
    setBusy(true);
    try {
      await adminBrandingApi.resetBrandingAsset({ slot: descriptor.slot });
      setVersion((v) => v + 1);
      addToast({
        message: `${descriptor.label} reset to default.`,
        type: "success",
      });
    } catch {
      addToast({
        message: `Failed to reset ${descriptor.label}.`,
        type: "error",
      });
    } finally {
      setBusy(false);
    }
  }, [addToast, descriptor.label, descriptor.slot]);

  const { getRootProps, getInputProps, isDragActive } = useDropzone({
    onDrop: (accepted) => {
      const file = accepted[0];
      if (file) upload(file);
    },
    accept: {
      "image/svg+xml": [".svg"],
      "image/png": [".png"],
      "image/webp": [".webp"],
    },
    multiple: false,
    disabled: busy,
  });

  const previewUrl = useMemo(() => {
    if (hasCustom) {
      return `/api/v1/branding/${descriptor.slot}?v=${version}`;
    }
    return descriptor.bundledFallback;
  }, [hasCustom, descriptor.bundledFallback, descriptor.slot, version]);

  return (
    <Box
      sx={{
        border: "1px solid",
        borderColor: "divider",
        borderRadius: tokens.radius.card,
        p: 2,
        display: "flex",
        flexDirection: "column",
        gap: 1.5,
      }}
    >
      <Box
        sx={{
          display: "flex",
          alignItems: "center",
          justifyContent: "space-between",
        }}
      >
        <Typography variant="subtitle2" sx={{ fontWeight: 600 }}>
          {descriptor.label}
        </Typography>
        <Chip
          size="small"
          label={hasCustom === null ? "…" : hasCustom ? "Custom" : "Default"}
          sx={{
            height: 20,
            fontSize: "0.7rem",
            bgcolor: hasCustom ? tokens.badge.tag.bg : tokens.badge.version.bg,
            color: hasCustom
              ? tokens.badge.tag.text
              : tokens.badge.version.text,
          }}
        />
      </Box>

      <Box
        sx={{
          aspectRatio: descriptor.previewAspect,
          width: descriptor.previewSize,
          maxWidth: "100%",
          bgcolor: descriptor.previewBg,
          border: "1px solid",
          borderColor: "divider",
          borderRadius: tokens.radius.input,
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
          overflow: "hidden",
          alignSelf: "center",
        }}
      >
        <img
          src={previewUrl}
          alt={`${descriptor.label} preview`}
          style={{ maxWidth: "85%", maxHeight: "85%", objectFit: "contain" }}
        />
      </Box>

      <Box
        {...getRootProps()}
        sx={{
          border: "1px dashed",
          borderColor: isDragActive ? tokens.color.primary : "divider",
          borderRadius: tokens.radius.input,
          p: 1.5,
          textAlign: "center",
          cursor: busy ? "not-allowed" : "pointer",
          bgcolor: isDragActive ? tokens.color.primaryLight : "transparent",
          color: "text.secondary",
          fontSize: "0.78rem",
          transition: "background 0.15s, border-color 0.15s",
        }}
      >
        <input {...getInputProps()} />
        {isDragActive ? "Drop to upload…" : "Drop file or click to upload"}
      </Box>

      <Typography
        variant="caption"
        sx={{ color: "text.disabled", fontSize: "0.7rem", lineHeight: 1.4 }}
      >
        {descriptor.help}
      </Typography>

      {hasCustom && (
        <Button
          size="small"
          variant="outlined"
          startIcon={<RotateCcw size={12} />}
          onClick={reset}
          disabled={busy}
        >
          Reset to default
        </Button>
      )}
    </Box>
  );
}
