// SPDX-License-Identifier: AGPL-3.0
// Copyright (C) 2026 devtank42 GmbH
import { Box, Typography } from '@mui/material'
import ReactMarkdown from 'react-markdown'
import rehypeSanitize from 'rehype-sanitize'
import type { PluginReleaseDto } from '../../api/generated/model'
import { formatDateTime } from '../../utils/formatDateTime'

interface ChangelogTabProps {
  releases: PluginReleaseDto[]
}

export function ChangelogTab({ releases }: ChangelogTabProps) {
  const withChangelog = releases.filter((r) => r.changelog)

  if (withChangelog.length === 0) {
    return (
      <Typography variant="body2" color="text.secondary">
        No changelog available. Add release notes when publishing a new version.
      </Typography>
    )
  }

  return (
    <Box
      sx={{
        '& h2': { fontSize: '1.125rem', fontWeight: 600, mt: 3, mb: 1 },
        '& ul, & ol': { pl: 3, mb: 1.5 },
        '& li': { fontSize: '0.875rem', color: 'text.secondary', mb: 0.5, lineHeight: 1.7 },
        '& p':  { fontSize: '0.875rem', color: 'text.secondary', mb: 1 },
      }}
    >
      {withChangelog.map((rel) => (
        <Box key={rel.id}>
          <Typography variant="h3" sx={{ mt: 3, mb: 1 }}>
            v{rel.version}
            {rel.createdAt && (
              <Box component="span" sx={{ fontWeight: 400, fontSize: '0.875rem', color: 'text.disabled', ml: 1 }}>
                – {formatDateTime(rel.createdAt)}
              </Box>
            )}
          </Typography>
          <ReactMarkdown rehypePlugins={[rehypeSanitize]}>
            {rel.changelog!}
          </ReactMarkdown>
        </Box>
      ))}
    </Box>
  )
}
