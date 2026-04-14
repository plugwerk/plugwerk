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
import { Autocomplete, CircularProgress, TextField } from "@mui/material";

interface FilterAutocompleteProps {
  options: string[];
  value: string;
  onChange: (value: string) => void;
  placeholder?: string;
  "aria-label"?: string;
  minWidth?: number;
  loading?: boolean;
}

export function FilterAutocomplete({
  options,
  value,
  onChange,
  placeholder = "All",
  "aria-label": ariaLabel,
  minWidth = 180,
  loading = false,
}: FilterAutocompleteProps) {
  return (
    <Autocomplete
      size="small"
      options={options}
      value={value || null}
      onChange={(_, newValue) => onChange(newValue ?? "")}
      loading={loading}
      openOnFocus
      clearOnEscape
      sx={{ minWidth }}
      renderInput={(params) => (
        <TextField
          {...params}
          placeholder={placeholder}
          aria-label={ariaLabel}
          slotProps={{
            input: {
              ...params.InputProps,
              endAdornment: (
                <>
                  {loading && <CircularProgress color="inherit" size={16} />}
                  {params.InputProps.endAdornment}
                </>
              ),
              sx: {
                height: 36,
                fontSize: "0.875rem",
                color: "text.secondary",
                "& .MuiOutlinedInput-notchedOutline": {
                  borderColor: "divider",
                },
                "&:hover:not(.Mui-focused) .MuiOutlinedInput-notchedOutline": {
                  borderColor: "text.disabled",
                },
                "&.Mui-focused .MuiOutlinedInput-notchedOutline": {
                  borderColor: "primary.main",
                  borderWidth: "1px",
                },
              },
            },
          }}
        />
      )}
    />
  );
}
