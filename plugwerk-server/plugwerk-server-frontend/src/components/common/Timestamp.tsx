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
import { Box, Tooltip } from "@mui/material";
import { useEffectiveTimezone } from "../../hooks/useEffectiveTimezone";
import { formatDateTime, formatRelativeTime } from "../../utils/formatDateTime";
import { timezoneAbbreviation } from "../../utils/timezones";

type TimestampVariant = "full" | "relative";

interface TimestampProps {
  /** ISO-8601 timestamp (OffsetDateTime from the backend). */
  readonly date: string | undefined;
  /**
   * Rendering variant:
   * - `full` (default): `dd.MM.yyyy HH:mm:ss`
   * - `relative`: "5m ago", "3d ago"
   */
  readonly variant?: TimestampVariant;
  /**
   * Whether to show a tooltip with the full timestamp and timezone
   * abbreviation on hover.
   *
   * Default:
   * - `relative` → `true` (primary label lacks the absolute date; the
   *   tooltip adds the anchor)
   * - `full` → `false` (primary label already shows the full date;
   *   extra tooltip chrome would be noise)
   *
   * Pass `withTooltip={true}` explicitly when the timezone abbreviation
   * is especially useful for the reader.
   */
  readonly withTooltip?: boolean;
  /** Optional CSS class for the rendered `<span>`. */
  readonly className?: string;
}

/**
 * Renders a backend timestamp (UTC `OffsetDateTime`) in the effective user
 * timezone. For `relative` variants the tooltip (enabled by default) shows
 * the full timestamp plus the timezone abbreviation (e.g. "CET", "PST",
 * "UTC") so readers can anchor the value to a zone.
 */
export function Timestamp({
  date,
  variant = "full",
  withTooltip,
  className,
}: TimestampProps) {
  const timezone = useEffectiveTimezone();
  const tooltipEnabled = withTooltip ?? variant === "relative";

  if (!date) {
    return (
      <Box component="span" className={className}>
        —
      </Box>
    );
  }

  const primary =
    variant === "relative"
      ? formatRelativeTime(date)
      : formatDateTime(date, { timezone });

  if (!tooltipEnabled) {
    return (
      <Box component="span" className={className}>
        {primary}
      </Box>
    );
  }

  const fullWithTz = `${formatDateTime(date, { timezone })} ${timezoneAbbreviation(timezone)}`;

  return (
    <Tooltip title={fullWithTz} arrow>
      <Box component="span" className={className} sx={{ cursor: "help" }}>
        {primary}
      </Box>
    </Tooltip>
  );
}
