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
import { useState } from "react";
import { Box, Typography, Button, Tabs, Tab } from "@mui/material";
import { ArrowLeft } from "lucide-react";
import { SettingsSection } from "./namespace-detail/SettingsSection";
import { MembersSection } from "./namespace-detail/MembersSection";
import { ApiKeysSection } from "./namespace-detail/ApiKeysSection";

interface NamespaceDetailViewProps {
  slug: string;
  onBack: () => void;
}

const TAB_IDS = ["settings", "members", "api-keys"] as const;

export function NamespaceDetailView({
  slug,
  onBack,
}: NamespaceDetailViewProps) {
  const [tab, setTab] = useState(0);

  return (
    <Box sx={{ display: "flex", flexDirection: "column", gap: 3 }}>
      <Box>
        <Button
          size="small"
          startIcon={<ArrowLeft size={14} />}
          onClick={onBack}
          sx={{ mb: 1 }}
        >
          Back to Namespaces
        </Button>
        <Typography variant="h2" gutterBottom>
          {slug}
        </Typography>
      </Box>

      <Tabs
        value={tab}
        onChange={(_, v) => setTab(v)}
        aria-label="Namespace settings tabs"
        sx={{ borderBottom: 1, borderColor: "divider" }}
      >
        <Tab
          label="Settings"
          id="ns-tab-settings"
          aria-controls="ns-panel-settings"
        />
        <Tab
          label="Members"
          id="ns-tab-members"
          aria-controls="ns-panel-members"
        />
        <Tab
          label="API Keys"
          id="ns-tab-api-keys"
          aria-controls="ns-panel-api-keys"
        />
      </Tabs>

      {TAB_IDS.map((id, i) => (
        <Box
          key={id}
          role="tabpanel"
          id={`ns-panel-${id}`}
          aria-labelledby={`ns-tab-${id}`}
          hidden={tab !== i}
        >
          {tab === 0 && i === 0 && <SettingsSection slug={slug} />}
          {tab === 1 && i === 1 && <MembersSection slug={slug} />}
          {tab === 2 && i === 2 && <ApiKeysSection slug={slug} />}
        </Box>
      ))}
    </Box>
  );
}
