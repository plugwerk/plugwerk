// SPDX-License-Identifier: AGPL-3.0
// Copyright (C) 2026 devtank42 GmbH

/**
 * Downloads a release artifact with the JWT auth token.
 * Uses fetch + blob URL to trigger a browser download with the correct filename.
 */
export async function downloadArtifact(url: string, filename: string): Promise<void> {
  const token = localStorage.getItem('pw-access-token')
  const response = await fetch(url, {
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  })
  if (!response.ok) {
    throw new Error(`Download failed: ${response.status}`)
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
