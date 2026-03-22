// SPDX-License-Identifier: AGPL-3.0
// Copyright (C) 2026 devtank42 GmbH
import { Box, Paper, Typography } from '@mui/material'
import type { ReactNode } from 'react'

interface AuthCardProps {
  title: string
  subtitle?: string
  children: ReactNode
}

export function AuthCard({ title, subtitle, children }: AuthCardProps) {
  return (
    <Box
      component="main"
      id="main-content"
      sx={{
        flex: 1,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        py: 8,
        px: 2,
      }}
    >
      <Paper
        sx={{
          width: '100%',
          maxWidth: 400,
          p: 4,
          display: 'flex',
          flexDirection: 'column',
          gap: 3,
          border: '1px solid',
          borderColor: 'divider',
        }}
        elevation={0}
      >
        {/* Logo */}
        <Box sx={{ display: 'flex', justifyContent: 'center' }}>
          <Box
            component="img"
            src="/logomark.svg"
            alt="Plugwerk"
            sx={{ height: 56, width: 'auto' }}
          />
        </Box>

        {/* Title */}
        <Box sx={{ textAlign: 'center' }}>
          <Typography variant="h3">{title}</Typography>
          {subtitle && (
            <Typography variant="body2" color="text.disabled" sx={{ mt: 0.5 }}>
              {subtitle}
            </Typography>
          )}
        </Box>

        {children}
      </Paper>
    </Box>
  )
}
