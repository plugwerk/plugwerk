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
import { Box, Typography, alpha, useTheme } from "@mui/material";
import { tokens } from "../../theme/tokens";

interface SectionProps {
  icon: React.ReactNode;
  title: string;
  description?: string;
  contentGap?: number;
  children: React.ReactNode;
}

export function Section({
  icon,
  title,
  description,
  contentGap,
  children,
}: SectionProps) {
  const theme = useTheme();
  const isDark = theme.palette.mode === "dark";

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
        <Box sx={{ color: "text.secondary", display: "flex" }}>{icon}</Box>
        <Box>
          <Typography
            variant="subtitle1"
            sx={{
              fontWeight: 600,
            }}
          >
            {title}
          </Typography>
          {description && (
            <Typography
              variant="caption"
              sx={{
                color: "text.secondary",
              }}
            >
              {description}
            </Typography>
          )}
        </Box>
      </Box>
      <Box
        sx={{
          px: 3,
          py: 2.5,
          ...(contentGap != null && {
            display: "flex",
            flexDirection: "column",
            gap: contentGap,
          }),
        }}
      >
        {children}
      </Box>
    </Box>
  );
}
