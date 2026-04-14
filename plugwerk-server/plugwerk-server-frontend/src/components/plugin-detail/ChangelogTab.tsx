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
import type { PluginReleaseDto } from "../../api/generated/model";
import { formatDateTime } from "../../utils/formatDateTime";

interface ChangelogTabProps {
  releases: PluginReleaseDto[];
}

export function ChangelogTab({ releases }: ChangelogTabProps) {
  const withChangelog = releases.filter((r) => r.changelog);

  if (withChangelog.length === 0) {
    return (
      <Typography variant="body2" color="text.secondary">
        No changelog available. Add release notes when publishing a new version.
      </Typography>
    );
  }

  return (
    <Box
      sx={{
        "& h2": { fontSize: "1.125rem", fontWeight: 600, mt: 3, mb: 1 },
        "& ul, & ol": { pl: 3, mb: 1.5 },
        "& li": {
          fontSize: "0.875rem",
          color: "text.secondary",
          mb: 0.5,
          lineHeight: 1.7,
        },
        "& p": { fontSize: "0.875rem", color: "text.secondary", mb: 1 },
      }}
    >
      {withChangelog.map((rel) => (
        <Box key={rel.id}>
          <Typography variant="h3" sx={{ mt: 3, mb: 1 }}>
            v{rel.version}
            {rel.createdAt && (
              <Box
                component="span"
                sx={{
                  fontWeight: 400,
                  fontSize: "0.875rem",
                  color: "text.disabled",
                  ml: 1,
                }}
              >
                – {formatDateTime(rel.createdAt)}
              </Box>
            )}
          </Typography>
          <ReactMarkdown rehypePlugins={[rehypeSanitize]}>
            {rel.changelog!}
          </ReactMarkdown>
        </Box>
      ))}
    </Box>
  );
}
