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
import { CircularProgress, IconButton, Tooltip } from "@mui/material";
import type { LucideIcon } from "lucide-react";

interface ActionIconButtonProps {
  icon: LucideIcon;
  tooltip: string;
  onClick: (e: React.MouseEvent) => void;
  color?:
    | "default"
    | "primary"
    | "secondary"
    | "error"
    | "info"
    | "success"
    | "warning";
  loading?: boolean;
  disabled?: boolean;
}

/**
 * Design guideline: All table actions are icon-only buttons with a tooltip.
 * Use this component for every action in DataTable columns.
 */
export function ActionIconButton({
  icon: Icon,
  tooltip,
  onClick,
  color,
  loading,
  disabled,
}: ActionIconButtonProps) {
  return (
    <Tooltip title={tooltip}>
      <span>
        <IconButton
          size="small"
          color={color}
          onClick={onClick}
          disabled={disabled || loading}
          aria-label={tooltip}
        >
          {loading ? <CircularProgress size={14} /> : <Icon size={14} />}
        </IconButton>
      </span>
    </Tooltip>
  );
}
