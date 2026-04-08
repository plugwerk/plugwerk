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
import { createTheme, type PaletteMode } from '@mui/material'
import { tokens } from './tokens'

export function buildTheme(mode: PaletteMode) {
  const isDark = mode === 'dark'

  return createTheme({
    palette: {
      mode,
      primary: {
        main: tokens.color.primary,
        dark: tokens.color.primaryDark,
        light: tokens.color.primaryLight,
        contrastText: tokens.color.white,
      },
      secondary: {
        main: tokens.color.secondary,
      },
      success:  { main: tokens.color.success },
      warning:  { main: tokens.color.warning },
      error:    { main: tokens.color.danger },
      background: {
        default: isDark ? '#161616' : tokens.color.gray10,
        paper:   isDark ? '#262626' : tokens.color.white,
      },
      text: {
        primary:   isDark ? '#F4F4F4' : tokens.color.gray100,
        secondary: isDark ? '#C6C6C6' : tokens.color.gray80,
        disabled:  isDark ? '#6F6F6F' : tokens.color.gray40,
      },
      divider: isDark ? '#393939' : tokens.color.gray20,
    },

    typography: {
      fontFamily: 'Inter, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif',
      h1: { fontWeight: 700, fontSize: '2.25rem', lineHeight: '2.75rem' },
      h2: { fontWeight: 600, fontSize: '1.75rem', lineHeight: '2.25rem' },
      h3: { fontWeight: 600, fontSize: '1.375rem', lineHeight: '1.75rem' },
      h4: { fontWeight: 600, fontSize: '1.0625rem', lineHeight: '1.5rem' },
      body1: { fontSize: '1rem', lineHeight: '1.5rem' },
      body2: { fontSize: '0.875rem', lineHeight: '1.25rem' },
      caption: { fontSize: '0.75rem', lineHeight: '1.125rem' },
    },

    shape: {
      borderRadius: 6,
    },

    components: {
      MuiCssBaseline: {
        styleOverrides: {
          '*, *::before, *::after': { boxSizing: 'border-box' },
          body: { minHeight: '100dvh' },
          a: { color: 'inherit', textDecoration: 'none' },
          'ul, ol': { listStyle: 'none', margin: 0, padding: 0 },
        },
      },
      MuiButton: {
        styleOverrides: {
          root: {
            textTransform: 'none',
            fontWeight: 500,
            borderRadius: tokens.radius.btn,
          },
        },
      },
      MuiChip: {
        styleOverrides: {
          root: { borderRadius: tokens.radius.btn, fontWeight: 500 },
        },
      },
      MuiCard: {
        styleOverrides: {
          root: {
            borderRadius: tokens.radius.card,
            border: `1px solid ${isDark ? '#393939' : tokens.color.gray20}`,
            boxShadow: tokens.shadow.card,
          },
        },
      },
      MuiDialog: {
        styleOverrides: {
          paper: {
            borderRadius: tokens.radius.dialog,
            boxShadow: isDark
              ? '0 8px 32px rgba(0,0,0,0.5)'
              : tokens.shadow.modal,
          },
        },
      },
      MuiMenu: {
        styleOverrides: {
          paper: {
            borderRadius: tokens.radius.card,
            border: `1px solid ${isDark ? '#393939' : tokens.color.gray20}`,
            boxShadow: isDark
              ? '0 4px 16px rgba(0,0,0,0.4)'
              : '0 4px 16px rgba(0,0,0,0.08)',
            marginTop: 4,
          },
          list: {
            padding: '4px',
          },
        },
      },
      MuiMenuItem: {
        styleOverrides: {
          root: {
            fontSize: '0.8125rem',
            borderRadius: '4px',
            padding: '6px 12px',
            minHeight: 'unset',
            transition: 'background 150ms ease',
            '&:hover': {
              backgroundColor: isDark ? 'rgba(255,255,255,0.06)' : tokens.color.gray10,
            },
            '&.Mui-selected': {
              backgroundColor: isDark ? 'rgba(15,98,254,0.15)' : `${tokens.color.primaryLight}`,
              '&:hover': {
                backgroundColor: isDark ? 'rgba(15,98,254,0.22)' : '#bdd4ff',
              },
            },
          },
        },
      },
      MuiInputBase: {
        styleOverrides: {
          root: { borderRadius: `${tokens.radius.input} !important` },
        },
      },
      MuiOutlinedInput: {
        styleOverrides: {
          root: { borderRadius: tokens.radius.input },
        },
      },
      MuiAppBar: {
        styleOverrides: {
          root: {
            boxShadow: 'none',
            borderBottom: `1px solid ${isDark ? '#393939' : tokens.color.gray20}`,
            backgroundColor: isDark ? '#262626' : tokens.color.white,
            color: isDark ? '#F4F4F4' : tokens.color.gray100,
          },
        },
      },
    },
  })
}
