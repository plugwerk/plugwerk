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
import { useState } from 'react'
import { Box, Tooltip, IconButton, Typography } from '@mui/material'
import { Copy, Check } from 'lucide-react'

interface CopyablePluginIdProps {
  pluginId: string
}

export function CopyablePluginId({ pluginId }: CopyablePluginIdProps) {
  const [copied, setCopied] = useState(false)

  async function handleCopy(e: React.MouseEvent) {
    e.preventDefault()
    e.stopPropagation()
    try {
      await navigator.clipboard.writeText(pluginId)
      setCopied(true)
      setTimeout(() => setCopied(false), 2000)
    } catch {
      // clipboard not available
    }
  }

  return (
    <Box
      sx={{
        display: 'inline-flex',
        alignItems: 'center',
        gap: 0.25,
        maxWidth: '100%',
        minWidth: 0,
      }}
    >
      <Typography
        variant="caption"
        sx={{
          fontFamily: 'monospace',
          fontSize: '0.7rem',
          color: 'text.disabled',
          overflow: 'hidden',
          textOverflow: 'ellipsis',
          whiteSpace: 'nowrap',
        }}
      >
        {pluginId}
      </Typography>
      <Tooltip title={copied ? 'Copied!' : 'Copy plugin ID'} arrow>
        <IconButton
          size="small"
          onClick={handleCopy}
          aria-label="Copy plugin ID"
          sx={{ p: 0.25, color: 'text.disabled' }}
        >
          {copied ? <Check size={11} /> : <Copy size={11} />}
        </IconButton>
      </Tooltip>
    </Box>
  )
}
