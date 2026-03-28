// SPDX-License-Identifier: AGPL-3.0
// Copyright (C) 2026 devtank42 GmbH
import { Box, Skeleton, Card } from '@mui/material'

export function PluginCardSkeleton() {
  return (
    <Card sx={{ p: 2.5, display: 'flex', flexDirection: 'column', gap: 1.5 }} aria-hidden="true">
      <Box sx={{ display: 'flex', gap: 1.5 }}>
        <Skeleton variant="rounded" width={48} height={48} />
        <Box sx={{ flex: 1 }}>
          <Skeleton variant="text" width="60%" height={20} />
          <Skeleton variant="text" width="40%" height={16} />
        </Box>
      </Box>
      <Skeleton variant="text" width="100%" height={16} />
      <Skeleton variant="text" width="80%" height={16} />
      <Box sx={{ display: 'flex', gap: 0.5 }}>
        <Skeleton variant="rounded" width={40} height={20} />
        <Skeleton variant="rounded" width={50} height={20} />
        <Skeleton variant="rounded" width={45} height={20} />
      </Box>
    </Card>
  )
}
