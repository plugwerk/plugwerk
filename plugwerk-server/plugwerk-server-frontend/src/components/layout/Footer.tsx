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
import { Box, Typography, Link } from "@mui/material";
import { Link as RouterLink } from "react-router-dom";
import { useConfigStore } from "../../stores/configStore";
import { tokens } from "../../theme/tokens";

export function Footer() {
  const version = useConfigStore((s) => s.version);
  const fetchConfig = useConfigStore((s) => s.fetchConfig);

  useEffect(() => {
    fetchConfig();
  }, [fetchConfig]);
  return (
    <Box
      component="footer"
      role="contentinfo"
      sx={{
        borderTop: `1px solid`,
        borderColor: "divider",
        mt: "auto",
        py: 1.5,
        px: { xs: 2, sm: 3 },
        bgcolor: "background.paper",
      }}
    >
      <Box
        sx={{
          display: "flex",
          alignItems: "center",
          gap: 3,
          flexWrap: "wrap",
        }}
      >
        <Box sx={{ display: "flex", alignItems: "center", gap: 1 }}>
          <Typography
            variant="body2"
            sx={{
              fontWeight: 700,
            }}
          >
            Plugwerk
          </Typography>
          <Typography
            variant="caption"
            sx={{
              color: "text.disabled",
            }}
          >
            v{version}
          </Typography>
        </Box>

        <Link
          component={RouterLink}
          to="/api-docs"
          sx={{
            fontSize: "0.8125rem",
            color: tokens.color.primary,
            "&:hover": { textDecoration: "underline" },
          }}
        >
          API Docs
        </Link>

        <Box sx={{ flex: 1 }} />
      </Box>
    </Box>
  );
}
