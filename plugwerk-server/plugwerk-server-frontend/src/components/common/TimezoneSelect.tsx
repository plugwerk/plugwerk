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
import { useMemo } from "react";
import { Autocomplete, TextField } from "@mui/material";
import { getTimezoneOptions, type TimezoneOption } from "../../utils/timezones";

interface TimezoneSelectProps {
  readonly value: string;
  readonly onChange: (value: string) => void;
  readonly label?: string;
  readonly helperText?: string;
  readonly error?: boolean;
  readonly disabled?: boolean;
  readonly allowEmpty?: boolean;
  readonly emptyLabel?: string;
  readonly size?: "small" | "medium";
  readonly sx?: React.ComponentProps<typeof Autocomplete>["sx"];
}

const EMPTY_OPTION: TimezoneOption = {
  id: "",
  region: "",
  city: "",
  offset: "",
  label: "",
};

export function TimezoneSelect({
  value,
  onChange,
  label = "Timezone",
  helperText,
  error,
  disabled,
  allowEmpty = false,
  emptyLabel = "Use system default",
  size = "small",
  sx,
}: TimezoneSelectProps) {
  const options = useMemo<TimezoneOption[]>(() => {
    const list = getTimezoneOptions();
    return allowEmpty
      ? [{ ...EMPTY_OPTION, label: emptyLabel, region: "—" }, ...list]
      : list;
  }, [allowEmpty, emptyLabel]);

  const selected = options.find((o) => o.id === value) ?? null;

  return (
    <Autocomplete<TimezoneOption, false, false, false>
      value={selected}
      onChange={(_, option) => onChange(option?.id ?? "")}
      options={options}
      groupBy={(option) => option.region}
      getOptionLabel={(option) => option.label}
      isOptionEqualToValue={(opt, val) => opt.id === val.id}
      disabled={disabled}
      size={size}
      sx={sx}
      renderInput={(params) => (
        <TextField
          {...params}
          label={label}
          helperText={helperText}
          error={error}
        />
      )}
    />
  );
}
