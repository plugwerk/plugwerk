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
import { Box, Table, TableBody, TableCell, TableHead, TableRow, Typography, Link } from '@mui/material'
import { Badge } from '../common/Badge'
import type { PluginReleaseDto } from '../../api/generated/model'

interface DependenciesTabProps {
  release: PluginReleaseDto | null
  namespace: string
}

export function DependenciesTab({ release, namespace }: DependenciesTabProps) {
  const deps = release?.pluginDependencies ?? []

  if (deps.length === 0) {
    return (
      <Typography variant="body2" color="text.secondary">This plugin has no dependencies.</Typography>
    )
  }

  return (
    <Box>
      <Typography variant="body2" color="text.disabled" sx={{ mb: 2 }}>
        Required plugins that must be installed alongside this plugin.
      </Typography>
      <Box sx={{ overflowX: 'auto' }}>
        <Table aria-label="Plugin dependencies" size="small">
          <TableHead>
            <TableRow>
              <TableCell>Plugin ID</TableCell>
              <TableCell>Required Version</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {deps.map((dep) => (
              <TableRow key={dep.id}>
                <TableCell>
                  <Link
                    href={`/${namespace}/plugins/${dep.id}`}
                    sx={{ fontFamily: 'monospace', fontSize: '0.8125rem', color: 'primary.main' }}
                  >
                    {dep.id}
                  </Link>
                </TableCell>
                <TableCell>
                  <Badge variant="version">{dep.version}</Badge>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </Box>
    </Box>
  )
}
