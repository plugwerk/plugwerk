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
import { Box, Tab, Tabs, Typography } from "@mui/material";
import { Outlet, useLocation, useNavigate } from "react-router-dom";

/**
 * Shell for the `/admin/email/*` admin area (#253, #438).
 *
 * Two sub-pages: `Server` (SMTP transport configuration) and `Templates`
 * (per-template subject + body editor backed by `MailTemplateService`).
 */
const EMAIL_TABS = [
  { path: "server", label: "Server" },
  { path: "templates", label: "Templates" },
];

export function EmailLayout() {
  const location = useLocation();
  const navigate = useNavigate();
  const activePath = location.pathname.startsWith("/admin/email/")
    ? location.pathname.replace("/admin/email/", "")
    : EMAIL_TABS[0].path;
  const activeTab =
    EMAIL_TABS.find((t) => activePath.startsWith(t.path))?.path ??
    EMAIL_TABS[0].path;

  return (
    <Box sx={{ display: "flex", flexDirection: "column", gap: 3 }}>
      <Box>
        <Typography variant="h2" gutterBottom>
          Email
        </Typography>
        <Typography variant="body2" sx={{
          color: "text.secondary"
        }}>
          SMTP server and outgoing email configuration. Changes take effect
          immediately — no server restart required.
        </Typography>
      </Box>
      <Tabs
        value={activeTab}
        onChange={(_, value: string) => navigate(`/admin/email/${value}`)}
        aria-label="Email admin sub-pages"
        sx={{ borderBottom: 1, borderColor: "divider" }}
      >
        {EMAIL_TABS.map((tab) => (
          <Tab key={tab.path} label={tab.label} value={tab.path} />
        ))}
      </Tabs>
      <Outlet />
    </Box>
  );
}
