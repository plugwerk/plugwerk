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
import { Box, Typography, Link } from "@mui/material";
import { ExternalLink } from "lucide-react";
import { Badge } from "../common/Badge";
import type { PluginDto, PluginReleaseDto } from "../../api/generated/model";
import { tokens } from "../../theme/tokens";
import { formatFileSize } from "../../utils/formatFileSize";

interface DetailSidebarProps {
  plugin: PluginDto;
  release: PluginReleaseDto | null;
}

function MetaItem({
  label,
  children,
}: {
  label: string;
  children: React.ReactNode;
}) {
  return (
    <Box sx={{ display: "flex", justifyContent: "space-between", gap: 1 }}>
      <Typography
        variant="caption"
        color="text.disabled"
        sx={{ flexShrink: 0 }}
      >
        {label}
      </Typography>
      <Box sx={{ textAlign: "right" }}>{children}</Box>
    </Box>
  );
}

function SidebarBox({
  title,
  children,
}: {
  title: string;
  children: React.ReactNode;
}) {
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
      <Typography
        variant="caption"
        fontWeight={600}
        color="text.secondary"
        sx={{ textTransform: "uppercase", letterSpacing: "0.06em" }}
      >
        {title}
      </Typography>
      {children}
    </Box>
  );
}

export function DetailSidebar({ plugin, release }: DetailSidebarProps) {
  const sha256 = release?.artifactSha256;

  return (
    <Box
      component="aside"
      aria-label="Plugin metadata"
      sx={{
        position: "sticky",
        top: 72,
        display: "flex",
        flexDirection: "column",
        gap: 2,
      }}
    >
      {/* Details */}
      <SidebarBox title="Details">
        {plugin.license && (
          <MetaItem label="License">
            <Typography variant="caption">{plugin.license}</Typography>
          </MetaItem>
        )}
        {plugin.repository?.startsWith("http") && (
          <MetaItem label="Repository">
            <Link
              href={plugin.repository}
              target="_blank"
              rel="noopener noreferrer"
              sx={{
                display: "flex",
                alignItems: "center",
                gap: 0.5,
                fontSize: "0.75rem",
                color: tokens.color.primary,
              }}
            >
              GitHub <ExternalLink size={11} />
            </Link>
          </MetaItem>
        )}
        {release?.requiresSystemVersion && (
          <MetaItem label="Requires">
            <Typography variant="caption">
              {release.requiresSystemVersion}
            </Typography>
          </MetaItem>
        )}
        {release?.artifactSize && (
          <MetaItem label="File Size">
            <Typography variant="caption">
              {formatFileSize(release.artifactSize)}
            </Typography>
          </MetaItem>
        )}
        {sha256 && (
          <MetaItem label="SHA-256">
            <Box sx={{ display: "flex", alignItems: "center", gap: 0.5 }}>
              <Typography
                variant="caption"
                sx={{
                  fontFamily: "monospace",
                  wordBreak: "break-all",
                  maxWidth: 120,
                }}
              >
                {sha256.slice(0, 16)}…
              </Typography>
            </Box>
          </MetaItem>
        )}
      </SidebarBox>

      {/* Tags */}
      {plugin.tags && plugin.tags.length > 0 && (
        <SidebarBox title="Tags">
          <Box sx={{ display: "flex", flexWrap: "wrap", gap: 0.5 }}>
            {plugin.tags.map((tag) => (
              <Badge key={tag} variant="tag">
                {tag}
              </Badge>
            ))}
          </Box>
        </SidebarBox>
      )}
    </Box>
  );
}
