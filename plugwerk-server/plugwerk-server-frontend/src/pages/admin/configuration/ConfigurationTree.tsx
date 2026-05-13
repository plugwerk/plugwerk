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
import { Box, Chip, Typography } from "@mui/material";
import { CircleCheckBig, MinusCircle } from "lucide-react";
import { tokens } from "../../../theme/tokens";

interface ConfigurationTreeProps {
  readonly pathPrefix: string;
  readonly value: unknown;
  readonly filter: string;
}

interface FlatLeaf {
  readonly path: string;
  readonly value: unknown;
}

/**
 * Flattens the recursive config tree into a path-keyed list of leaves
 * so the renderer can apply the substring filter and table-style layout
 * uniformly. Recursive structures stop at leaf values, at the redacted-
 * secret marker, and at arrays (we render arrays as JSON-stringified
 * scalars for simplicity — arrays inside `plugwerk.*` are rare and
 * always short).
 */
function flatten(prefix: string, value: unknown): FlatLeaf[] {
  if (value === null || typeof value !== "object" || Array.isArray(value)) {
    return [{ path: prefix, value }];
  }
  // Redacted-secret marker emitted by ConfigurationTreeBuilder.
  if (
    typeof value === "object" &&
    "_secret" in value &&
    (value as { _secret?: unknown })._secret === true
  ) {
    return [{ path: prefix, value }];
  }
  const result: FlatLeaf[] = [];
  for (const [key, child] of Object.entries(value)) {
    result.push(...flatten(`${prefix}.${key}`, child));
  }
  return result;
}

/**
 * Converts a dotted property path into the conventional `PLUGWERK_*`
 * env-var name so the dashboard can show the operator exactly what to
 * flip in the deployment without grepping the yaml. Convention:
 *   - dots and dashes become underscores
 *   - camelCase splits become underscore boundaries (defensive — the
 *     backend already emits kebab-case, but a hand-edited path stays
 *     well-behaved)
 *   - the whole thing is uppercased
 *
 * Best effort — the yaml occasionally maps a property to a custom env
 * name via `${OVERRIDE:default}`; the operator still has the yaml as
 * the authoritative source in those edge cases.
 */
function toEnvVar(path: string): string {
  return path
    .replace(/([a-z0-9])([A-Z])/g, "$1_$2")
    .replace(/[.-]/g, "_")
    .toUpperCase();
}

function renderValue(value: unknown): React.ReactNode {
  if (value === null || value === undefined) {
    return (
      <Typography
        variant="caption"
        sx={{ color: "text.disabled", fontFamily: "monospace" }}
      >
        not set
      </Typography>
    );
  }
  if (
    typeof value === "object" &&
    "_secret" in (value as object) &&
    (value as { _secret?: unknown })._secret === true
  ) {
    const configured = Boolean((value as { configured?: boolean }).configured);
    return (
      <Chip
        size="small"
        icon={
          configured ? <CircleCheckBig size={12} /> : <MinusCircle size={12} />
        }
        label={configured ? "configured" : "not configured"}
        sx={{
          height: 22,
          fontSize: "0.7rem",
          fontWeight: 600,
          bgcolor: configured
            ? tokens.badge.published.bg
            : tokens.badge.version.bg,
          color: configured
            ? tokens.badge.published.text
            : tokens.badge.version.text,
        }}
      />
    );
  }
  if (Array.isArray(value)) {
    return (
      <Typography
        variant="body2"
        sx={{
          fontFamily: "monospace",
          fontSize: "0.78rem",
          fontFeatureSettings: '"tnum"',
        }}
      >
        [{value.map((v) => JSON.stringify(v)).join(", ")}]
      </Typography>
    );
  }
  if (typeof value === "boolean") {
    return (
      <Chip
        size="small"
        label={String(value)}
        sx={{
          height: 20,
          fontSize: "0.7rem",
          fontFamily: "monospace",
          bgcolor: value ? tokens.badge.tag.bg : tokens.badge.version.bg,
          color: value ? tokens.badge.tag.text : tokens.badge.version.text,
        }}
      />
    );
  }
  return (
    <Typography
      variant="body2"
      sx={{
        fontFamily: "monospace",
        fontSize: "0.78rem",
        fontFeatureSettings: '"tnum"',
        wordBreak: "break-all",
      }}
    >
      {String(value)}
    </Typography>
  );
}

export function ConfigurationTree({
  pathPrefix,
  value,
  filter,
}: ConfigurationTreeProps) {
  const all = flatten(pathPrefix, value);
  const visible = filter
    ? all.filter((leaf) => leaf.path.toLowerCase().includes(filter))
    : all;

  if (visible.length === 0) {
    return (
      <Typography
        variant="caption"
        sx={{ color: "text.disabled", display: "block", py: 1 }}
      >
        {filter ? "No properties match the filter." : "No properties."}
      </Typography>
    );
  }

  return (
    <Box
      component="table"
      role="table"
      aria-label={`Configuration: ${pathPrefix}`}
      sx={{ width: "100%", borderCollapse: "collapse" }}
    >
      <Box component="thead">
        <Box component="tr">
          <HeaderCell>Path</HeaderCell>
          <HeaderCell width={220}>Env var</HeaderCell>
          <HeaderCell width={260}>Value</HeaderCell>
        </Box>
      </Box>
      <Box component="tbody">
        {visible.map((leaf) => (
          <Box
            component="tr"
            key={leaf.path}
            sx={{
              "&:hover": { bgcolor: "background.default" },
            }}
          >
            <Cell>
              <Typography
                variant="body2"
                sx={{
                  fontFamily: "monospace",
                  fontSize: "0.78rem",
                  wordBreak: "break-all",
                }}
              >
                {leaf.path}
              </Typography>
            </Cell>
            <Cell>
              <Typography
                variant="caption"
                sx={{
                  fontFamily: "monospace",
                  color: "text.disabled",
                  fontSize: "0.7rem",
                }}
              >
                {toEnvVar(leaf.path)}
              </Typography>
            </Cell>
            <Cell>{renderValue(leaf.value)}</Cell>
          </Box>
        ))}
      </Box>
    </Box>
  );
}

function HeaderCell({
  children,
  width,
}: {
  readonly children?: React.ReactNode;
  readonly width?: number;
}) {
  return (
    <Box
      component="th"
      sx={{
        textAlign: "left",
        width,
        px: 2,
        py: 1,
        borderBottom: "1px solid",
        borderColor: "divider",
        bgcolor: "background.default",
        fontSize: "0.7rem",
        fontWeight: 600,
        color: "text.secondary",
        textTransform: "uppercase",
        letterSpacing: "0.06em",
      }}
    >
      {children}
    </Box>
  );
}

function Cell({ children }: { readonly children: React.ReactNode }) {
  return (
    <Box
      component="td"
      sx={{
        px: 2,
        py: 1.25,
        borderBottom: "1px solid",
        borderColor: "divider",
        verticalAlign: "middle",
      }}
    >
      {children}
    </Box>
  );
}
