// SPDX-License-Identifier: AGPL-3.0
// Copyright (C) 2026 devtank42 GmbH
import { Autocomplete, CircularProgress, TextField } from '@mui/material'

interface FilterAutocompleteProps {
  options: string[]
  value: string
  onChange: (value: string) => void
  placeholder?: string
  'aria-label'?: string
  minWidth?: number
  loading?: boolean
}

export function FilterAutocomplete({
  options,
  value,
  onChange,
  placeholder = 'All',
  'aria-label': ariaLabel,
  minWidth = 180,
  loading = false,
}: FilterAutocompleteProps) {
  return (
    <Autocomplete
      size="small"
      options={options}
      value={value || null}
      onChange={(_, newValue) => onChange(newValue ?? '')}
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
                fontSize: '0.875rem',
                color: 'text.secondary',
                '& .MuiOutlinedInput-notchedOutline': {
                  borderColor: 'divider',
                },
                '&:hover:not(.Mui-focused) .MuiOutlinedInput-notchedOutline': {
                  borderColor: 'text.disabled',
                },
                '&.Mui-focused .MuiOutlinedInput-notchedOutline': {
                  borderColor: 'primary.main',
                  borderWidth: '1px',
                },
              },
            },
          }}
        />
      )}
    />
  )
}
