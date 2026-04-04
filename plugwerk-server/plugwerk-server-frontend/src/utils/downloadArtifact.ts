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

/**
 * Downloads a release artifact with the JWT auth token.
 * Uses fetch + blob URL to trigger a browser download with the correct filename.
 */
export async function downloadArtifact(url: string, filename: string): Promise<void> {
  const token = localStorage.getItem('pw-access-token')
  const headers: Record<string, string> = { Accept: 'application/octet-stream' }
  if (token) {
    headers['Authorization'] = `Bearer ${token}`
  }
  const response = await fetch(url, { headers })
  if (!response.ok) {
    let message = `Download failed (${response.status})`
    try {
      const body = await response.json()
      if (body.message) message = body.message
    } catch { /* response may not be JSON */ }
    throw new Error(message)
  }
  const blob = await response.blob()
  const blobUrl = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = blobUrl
  a.download = filename
  a.style.display = 'none'
  document.body.appendChild(a)
  a.click()
  // Delay cleanup so the browser has time to start the download
  setTimeout(() => {
    document.body.removeChild(a)
    URL.revokeObjectURL(blobUrl)
  }, 100)
}
