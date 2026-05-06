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
import { Box, Fade, Typography } from "@mui/material";
import { UploadCloud } from "lucide-react";
import { tokens } from "../../theme/tokens";

interface CatalogDropOverlayProps {
  visible: boolean;
}

export function CatalogDropOverlay({ visible }: CatalogDropOverlayProps) {
  return (
    <Fade in={visible} timeout={150}>
      <Box
        aria-hidden={!visible}
        sx={{
          position: "fixed",
          inset: 0,
          zIndex: 1200,
          bgcolor: `${tokens.color.primary}0D`,
          backdropFilter: "blur(2px)",
          pointerEvents: "none",
          visibility: visible ? "visible" : "hidden",
        }}
      >
        {/* Drop zone filling the area below the AppBar */}
        <Box
          sx={{
            position: "absolute",
            top: 56,
            left: 0,
            right: 0,
            bottom: 0,
            display: "flex",
            flexDirection: "column",
            alignItems: "center",
            justifyContent: "center",
            gap: 2,
            borderTop: `1px dashed ${tokens.color.primary}`,
            background: (theme) =>
              theme.palette.mode === "dark"
                ? `${tokens.color.primary}0F`
                : `${tokens.color.primaryLight}4D`,
          }}
        >
          <UploadCloud
            size={64}
            color={tokens.color.primary}
            strokeWidth={1.25}
          />
          <Typography
            variant="h5"
            sx={{
              fontWeight: 600,
              color: "text.primary"
            }}>
            Drop .jar or .zip files to upload
          </Typography>
          <Typography variant="body2" sx={{
            color: "text.secondary"
          }}>
            Files will be uploaded immediately
          </Typography>
        </Box>
      </Box>
    </Fade>
  );
}
