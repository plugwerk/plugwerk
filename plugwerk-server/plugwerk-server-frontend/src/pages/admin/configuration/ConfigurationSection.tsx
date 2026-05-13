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
import { useEffect, useMemo, useState } from "react";
import {
  Alert,
  Box,
  CircularProgress,
  InputAdornment,
  TextField,
  Typography,
} from "@mui/material";
import { AlertTriangle, FileText, Info, Search } from "lucide-react";
import { Section } from "../../../components/common/Section";
import { adminConfigurationApi } from "../../../api/config";
import { ConfigurationTree } from "./ConfigurationTree";

type ConfigValue =
  | string
  | number
  | boolean
  | null
  | ConfigTree
  | readonly ConfigValue[];

export interface ConfigTree {
  readonly [key: string]: ConfigValue;
}

/**
 * Read-only dashboard for the effective `plugwerk.*` configuration tree
 * (#522). The page is informational only — values are set in
 * `application.yml` and the `PLUGWERK_*` env-vars; this view exists so
 * an operator can see what the running server actually has without
 * shelling into the container.
 */
export function ConfigurationSection() {
  const [tree, setTree] = useState<ConfigTree | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [filter, setFilter] = useState("");

  useEffect(() => {
    let cancelled = false;
    async function load() {
      setLoading(true);
      try {
        const res = await adminConfigurationApi.getEffectiveConfiguration();
        if (cancelled) return;
        setTree((res.data as unknown as ConfigTree) ?? null);
        setError(null);
      } catch {
        if (cancelled) return;
        setError("Failed to load configuration. See server logs.");
      } finally {
        if (!cancelled) setLoading(false);
      }
    }
    load();
    return () => {
      cancelled = true;
    };
  }, []);

  const topLevelGroups = useMemo(() => {
    if (!tree) return [];
    return Object.entries(tree).map(([key, value]) => ({
      key,
      value,
    }));
  }, [tree]);

  return (
    <Box sx={{ display: "flex", flexDirection: "column", gap: 3 }}>
      <Box>
        <Typography variant="h6" sx={{ fontWeight: 600 }}>
          Configuration
        </Typography>
        <Typography variant="body2" sx={{ color: "text.secondary" }}>
          Effective <code>plugwerk.*</code> properties bound from{" "}
          <code>application.yml</code> and the <code>PLUGWERK_*</code>{" "}
          environment variables. Read-only.
        </Typography>
      </Box>

      <Alert severity="info" icon={<Info size={18} />}>
        These values come from <code>application.yml</code> and the
        <code> PLUGWERK_*</code> environment variables. They cannot be edited
        here — change the deployment configuration and restart the server.
        Secrets are shown as a <em>configured / not configured</em> indicator
        only and never travel over the wire.
      </Alert>

      {error && (
        <Alert severity="warning" icon={<AlertTriangle size={18} />}>
          {error}
        </Alert>
      )}

      {loading ? (
        <Box sx={{ display: "flex", justifyContent: "center", py: 6 }}>
          <CircularProgress size={28} />
        </Box>
      ) : tree ? (
        <>
          <TextField
            value={filter}
            onChange={(e) => setFilter(e.target.value)}
            placeholder="Filter by property path…"
            size="small"
            sx={{ maxWidth: 480 }}
            slotProps={{
              input: {
                startAdornment: (
                  <InputAdornment position="start">
                    <Search size={14} />
                  </InputAdornment>
                ),
              },
            }}
          />

          {topLevelGroups.map((group) => (
            <Section
              key={group.key}
              icon={<FileText size={18} />}
              title={`plugwerk.${group.key}`}
              description={`Configuration under the plugwerk.${group.key} hierarchy.`}
            >
              <ConfigurationTree
                pathPrefix={`plugwerk.${group.key}`}
                value={group.value}
                filter={filter.trim().toLowerCase()}
              />
            </Section>
          ))}
        </>
      ) : null}
    </Box>
  );
}
