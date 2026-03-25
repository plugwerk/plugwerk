// SPDX-License-Identifier: AGPL-3.0
// Copyright (C) 2026 devtank42 GmbH
import { Box, Typography, Link } from '@mui/material'
import { Link as RouterLink } from 'react-router-dom'
import { tokens } from '../../theme/tokens'

export function Footer() {
  return (
    <Box
      component="footer"
      role="contentinfo"
      sx={{
        borderTop: `1px solid`,
        borderColor: 'divider',
        mt: 'auto',
        py: 1.5,
        px: { xs: 2, sm: 3 },
        bgcolor: 'background.paper',
      }}
    >
      <Box
        sx={{
          display: 'flex',
          alignItems: 'center',
          gap: 3,
          flexWrap: 'wrap',
        }}
      >
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
          <Typography variant="body2" fontWeight={700}>Plugwerk</Typography>
          <Typography variant="caption" color="text.disabled">v1.0.0</Typography>
        </Box>

        <Link
          component={RouterLink}
          to="/api-docs"
          sx={{
            fontSize: '0.8125rem',
            color: tokens.color.primary,
            '&:hover': { textDecoration: 'underline' },
          }}
        >
          API Docs
        </Link>

        <Box sx={{ flex: 1 }} />
      </Box>
    </Box>
  )
}
