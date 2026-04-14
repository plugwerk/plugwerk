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
import { Box, Skeleton, Card } from "@mui/material";

export function PluginCardSkeleton() {
  return (
    <Card
      sx={{ p: 2.5, display: "flex", flexDirection: "column", gap: 1.5 }}
      aria-hidden="true"
    >
      <Box sx={{ display: "flex", gap: 1.5 }}>
        <Skeleton variant="rounded" width={48} height={48} />
        <Box sx={{ flex: 1 }}>
          <Skeleton variant="text" width="60%" height={20} />
          <Skeleton variant="text" width="40%" height={16} />
        </Box>
      </Box>
      <Skeleton variant="text" width="100%" height={16} />
      <Skeleton variant="text" width="80%" height={16} />
      <Box sx={{ display: "flex", gap: 0.5 }}>
        <Skeleton variant="rounded" width={40} height={20} />
        <Skeleton variant="rounded" width={50} height={20} />
        <Skeleton variant="rounded" width={45} height={20} />
      </Box>
    </Card>
  );
}
