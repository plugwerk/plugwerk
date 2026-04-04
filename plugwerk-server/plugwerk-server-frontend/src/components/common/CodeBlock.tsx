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
import { Box, IconButton, Tooltip } from '@mui/material'
import { Clipboard, Check } from 'lucide-react'
import { useState } from 'react'
import { tokens } from '../../theme/tokens'

interface CodeBlockProps {
  code: string
  lang?: string
}

export function CodeBlock({ code }: CodeBlockProps) {
  const [copied, setCopied] = useState(false)

  async function handleCopy() {
    await navigator.clipboard.writeText(code)
    setCopied(true)
    setTimeout(() => setCopied(false), 1500)
  }

  return (
    <Box
      sx={{
        position: 'relative',
        background: 'background.default',
        border: '1px solid',
        borderColor: 'divider',
        borderRadius: tokens.radius.card,
        p: 2,
        pr: 5,
        overflow: 'auto',
      }}
    >
      <Box
        component="pre"
        sx={{
          m: 0,
          fontFamily: '"JetBrains Mono", "Fira Code", monospace',
          fontSize: '0.8125rem',
          lineHeight: 1.6,
          color: 'text.primary',
          whiteSpace: 'pre-wrap',
          wordBreak: 'break-all',
        }}
      >
        <code>{code}</code>
      </Box>

      <Tooltip title={copied ? 'Copied!' : 'Copy'}>
        <IconButton
          size="small"
          onClick={handleCopy}
          aria-label="Copy code"
          sx={{
            position: 'absolute',
            top: 6,
            right: 6,
            color: 'text.disabled',
            '&:hover': { color: 'text.secondary' },
          }}
        >
          {copied ? <Check size={14} /> : <Clipboard size={14} />}
        </IconButton>
      </Tooltip>
    </Box>
  )
}
