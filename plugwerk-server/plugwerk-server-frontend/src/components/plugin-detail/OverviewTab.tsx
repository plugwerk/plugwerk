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
import { Box, Typography } from "@mui/material";
import ReactMarkdown from "react-markdown";
import rehypeSanitize from "rehype-sanitize";
import type { PluginDto } from "../../api/generated/model";

interface OverviewTabProps {
  plugin: PluginDto;
}

export function OverviewTab({ plugin }: OverviewTabProps) {
  const content = plugin.description ?? "*No description available.*";

  return (
    <Box
      sx={{
        "& h2": { fontSize: "1.25rem", fontWeight: 600, mt: 3, mb: 1.5 },
        "& h3": { fontSize: "1.0625rem", fontWeight: 600, mt: 2, mb: 1 },
        "& p": {
          fontSize: "0.875rem",
          color: "text.secondary",
          mb: 1.5,
          lineHeight: 1.7,
        },
        "& ul, & ol": { pl: 3, mb: 1.5 },
        "& li": {
          fontSize: "0.875rem",
          color: "text.secondary",
          mb: 0.5,
          lineHeight: 1.7,
        },
        "& pre": { my: 2 },
        "& code": { fontFamily: "monospace", fontSize: "0.8125rem" },
      }}
    >
      <Typography variant="h2" sx={{ mb: 2 }}>
        About this plugin
      </Typography>
      <ReactMarkdown rehypePlugins={[rehypeSanitize]}>{content}</ReactMarkdown>
    </Box>
  );
}
