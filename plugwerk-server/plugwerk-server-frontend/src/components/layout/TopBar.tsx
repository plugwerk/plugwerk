// SPDX-License-Identifier: AGPL-3.0
// Copyright (C) 2026 devtank42 GmbH
import {
  AppBar,
  Toolbar,
  Box,
  IconButton,
  Button,
  InputBase,
  useTheme,
  alpha,
} from '@mui/material'
import { Search, Sun, Moon, Menu, User, LogOut } from 'lucide-react'
import { Link, useNavigate } from 'react-router-dom'
import { useUiStore } from '../../stores/uiStore'
import { useAuthStore } from '../../stores/authStore'
import { tokens } from '../../theme/tokens'

interface TopBarProps {
  showSearch?: boolean
}

export function TopBar({ showSearch = true }: TopBarProps) {
  const theme = useTheme()
  const isDark = theme.palette.mode === 'dark'
  const { toggleTheme, setSearchQuery } = useUiStore()
  const { isAuthenticated, logout } = useAuthStore()
  const navigate = useNavigate()

  function handleLogout() {
    logout()
    navigate('/login', { replace: true })
  }

  function handleSearchChange(e: React.ChangeEvent<HTMLInputElement>) {
    setSearchQuery(e.target.value)
  }

  function handleSearchKeyDown(e: React.KeyboardEvent<HTMLInputElement>) {
    if (e.key === 'Enter') {
      navigate('/')
    }
  }

  return (
    <AppBar position="sticky" role="banner">
      {/* Skip link */}
      <a
        href="#main-content"
        style={{
          position: 'absolute',
          left: '-9999px',
          top: 0,
          zIndex: 9999,
          padding: '4px 8px',
          background: tokens.color.primary,
          color: tokens.color.white,
        }}
        onFocus={(e) => { e.currentTarget.style.left = '0' }}
        onBlur={(e) => { e.currentTarget.style.left = '-9999px' }}
      >
        Skip to main content
      </a>

      <Toolbar sx={{ gap: 2, minHeight: { xs: 56 } }}>
        {/* Logo */}
        <Box
          component={Link}
          to="/"
          aria-label="Plugwerk – back to catalog"
          sx={{
            display: 'flex',
            alignItems: 'center',
            gap: 1,
            textDecoration: 'none',
            color: 'inherit',
            flexShrink: 0,
          }}
        >
          <Box
            component="img"
            src={isDark ? '/logo-dark.svg' : '/logo-light.svg'}
            alt="Plugwerk"
            sx={{ height: 32, width: 'auto', maxWidth: { xs: 100, sm: 140 }, display: 'block', flexShrink: 0 }}
          />
        </Box>

        {/* Search */}
        {showSearch && (
          <Box
            role="search"
            sx={{
              flex: 1,
              maxWidth: 480,
              position: 'relative',
            }}
          >
            <Box
              sx={{
                position: 'absolute',
                left: 10,
                top: '50%',
                transform: 'translateY(-50%)',
                color: 'text.disabled',
                display: 'flex',
              }}
              aria-hidden="true"
            >
              <Search size={16} />
            </Box>
            <InputBase
              placeholder='Search plugins… (press / to focus)'
              aria-label="Search plugins"
              onChange={handleSearchChange}
              onKeyDown={handleSearchKeyDown}
              sx={{
                width: '100%',
                pl: '34px',
                pr: 1.5,
                py: 0.75,
                borderRadius: tokens.radius.btn,
                border: `1px solid ${isDark ? '#393939' : tokens.color.gray20}`,
                background: isDark ? '#1c1c1c' : tokens.color.gray10,
                fontSize: '0.875rem',
                '&:focus-within': {
                  borderColor: tokens.color.primary,
                  background: isDark ? '#262626' : tokens.color.white,
                  outline: `2px solid ${alpha(tokens.color.primary, 0.25)}`,
                  outlineOffset: 0,
                },
              }}
            />
          </Box>
        )}

        {/* Spacer */}
        <Box sx={{ flex: 1 }} />

        {/* Nav links — only for authenticated users */}
        {isAuthenticated && (
          <Box
            component="nav"
            aria-label="Main navigation"
            sx={{ display: { xs: 'none', md: 'flex' }, gap: 0.5 }}
          >
            <Button
              component={Link}
              to="/"
              sx={{ color: 'text.primary', fontWeight: 500, fontSize: '0.875rem' }}
            >
              Catalog
            </Button>
            <Button
              component={Link}
              to="/admin"
              sx={{ color: 'text.primary', fontWeight: 500, fontSize: '0.875rem' }}
            >
              Admin
            </Button>
          </Box>
        )}

        {/* Actions */}
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
          <IconButton
            onClick={toggleTheme}
            aria-label="Toggle dark mode"
            size="small"
            sx={{ color: 'text.secondary' }}
          >
            {isDark ? <Sun size={18} /> : <Moon size={18} />}
          </IconButton>

          {isAuthenticated ? (
            <>
              <IconButton
                component={Link}
                to="/profile"
                aria-label="Profile settings"
                size="small"
                sx={{ color: 'text.secondary', display: { xs: 'none', sm: 'inline-flex' } }}
              >
                <User size={18} />
              </IconButton>
              <IconButton
                onClick={handleLogout}
                aria-label="Log out"
                size="small"
                sx={{ color: 'text.secondary', display: { xs: 'none', sm: 'inline-flex' } }}
              >
                <LogOut size={18} />
              </IconButton>
            </>
          ) : (
            <Button
              component={Link}
              to="/login"
              variant="outlined"
              size="small"
              sx={{ display: { xs: 'none', sm: 'inline-flex' } }}
            >
              Log In
            </Button>
          )}

          <IconButton
            aria-label="Open menu"
            size="small"
            sx={{ display: { xs: 'flex', md: 'none' }, color: 'text.secondary' }}
          >
            <Menu size={20} />
          </IconButton>
        </Box>
      </Toolbar>
    </AppBar>
  )
}
