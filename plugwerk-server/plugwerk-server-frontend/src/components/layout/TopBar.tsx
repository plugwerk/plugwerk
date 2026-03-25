// SPDX-License-Identifier: AGPL-3.0
// Copyright (C) 2026 devtank42 GmbH
import {
  AppBar,
  Toolbar,
  Box,
  IconButton,
  Button,
  MenuItem,
  Typography,
  useTheme,
} from '@mui/material'
import { Sun, Moon, Menu, User, LogOut, Upload, LayoutGrid, Settings } from 'lucide-react'
import { Link, useNavigate, useLocation } from 'react-router-dom'
import { useUiStore } from '../../stores/uiStore'
import { UploadModal } from '../upload/UploadModal'
import { useAuthStore } from '../../stores/authStore'
import { useNamespaceStore } from '../../stores/namespaceStore'
import { FilterSelect } from '../common/FilterSelect'
import { tokens } from '../../theme/tokens'

export function TopBar() {
  const theme = useTheme()
  const isDark = theme.palette.mode === 'dark'
  const { toggleTheme, openUploadModal } = useUiStore()
  const { isAuthenticated, logout, namespace, setNamespace } = useAuthStore()
  const { namespaces } = useNamespaceStore()
  const navigate = useNavigate()
  const { pathname } = useLocation()

  function isActive(path: string) {
    return path === '/' ? pathname === '/' : pathname.startsWith(path)
  }

  function handleNamespaceChange(ns: string) {
    setNamespace(ns)
    navigate(`/namespaces/${ns}/plugins`)
  }

  function handleLogout() {
    logout()
    navigate('/login', { replace: true })
  }

  return (
    <>
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

        {/* Spacer */}
        <Box sx={{ flex: 1 }} />

        {/* Nav links — only for authenticated users */}
        {isAuthenticated && (
          <Box
            component="nav"
            aria-label="Main navigation"
            sx={{ display: { xs: 'none', md: 'flex' }, alignItems: 'center', gap: 0.5 }}
          >
            {/* Namespace dropdown — left of Catalog */}
            <Typography sx={{ color: 'text.primary', fontWeight: 500, fontSize: '0.875rem', whiteSpace: 'nowrap' }}>
              Namespace:
            </Typography>
            <FilterSelect
              value={namespace}
              onChange={handleNamespaceChange}
              aria-label="Select namespace"
              minWidth={130}
            >
              {namespaces.length > 0
                ? namespaces.map((ns) => (
                    <MenuItem key={ns.slug} value={ns.slug}>{ns.slug}</MenuItem>
                  ))
                : <MenuItem value={namespace}>{namespace}</MenuItem>
              }
            </FilterSelect>

            <Button
              component={Link}
              to="/"
              startIcon={<LayoutGrid size={15} color={isActive('/') ? tokens.color.primary : undefined} />}
              sx={{ color: 'text.primary', fontWeight: 500, fontSize: '0.875rem' }}
            >
              Catalog
            </Button>
            <Button
              onClick={openUploadModal}
              startIcon={<Upload size={15} />}
              sx={{ color: 'text.primary', fontWeight: 500, fontSize: '0.875rem' }}
            >
              Upload
            </Button>
            <Button
              component={Link}
              to="/admin"
              startIcon={<Settings size={15} color={isActive('/admin') ? tokens.color.primary : undefined} />}
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
    <UploadModal />
  </>
  )
}
