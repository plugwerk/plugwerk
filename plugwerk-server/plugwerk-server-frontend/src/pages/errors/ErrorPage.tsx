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
import { Box, Container, Typography, Button } from "@mui/material";
import { Link } from "react-router-dom";
import type { ReactNode } from "react";

interface ErrorPageProps {
  code: number;
  title: string;
  message: string;
  illustration: ReactNode;
}

export function ErrorPage({
  code,
  title,
  message,
  illustration,
}: ErrorPageProps) {
  return (
    <Box
      component="main"
      id="main-content"
      sx={{
        flex: 1,
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        py: 8,
      }}
    >
      <Container maxWidth="sm">
        <Box
          sx={{
            display: "flex",
            flexDirection: "column",
            alignItems: "center",
            gap: 3,
            textAlign: "center",
          }}
        >
          <Box sx={{ color: "text.disabled", width: 160, height: 160 }}>
            {illustration}
          </Box>
          <Typography
            variant="h1"
            sx={{ fontSize: "4rem", fontWeight: 700, color: "text.disabled" }}
          >
            {code}
          </Typography>
          <Typography variant="h2">{title}</Typography>
          <Typography variant="body1" sx={{
            color: "text.secondary"
          }}>
            {message}
          </Typography>
          <Button component={Link} to="/" variant="contained">
            Back to Catalog
          </Button>
        </Box>
      </Container>
    </Box>
  );
}
