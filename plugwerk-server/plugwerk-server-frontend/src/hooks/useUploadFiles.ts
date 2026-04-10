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
import { useCallback } from 'react'
import axios from 'axios'
import { axiosInstance } from '../api/config'
import { useUploadStore } from '../stores/uploadStore'
import { useUiStore } from '../stores/uiStore'
import { usePluginStore } from '../stores/pluginStore'
import { useConfigStore } from '../stores/configStore'

const VALID_EXTENSIONS = ['.jar', '.zip']

function isValidPluginFile(file: File): boolean {
  const name = file.name.toLowerCase()
  return VALID_EXTENSIONS.some((ext) => name.endsWith(ext))
}

/**
 * Hook for uploading plugin files in parallel.
 *
 * Validates files (extension + size), adds them to the upload store,
 * and fires parallel POST requests. Per-file progress is tracked via
 * the upload store. After all uploads settle, the plugin list is refreshed.
 */
export function useUploadFiles() {
  const uploadFiles = useCallback(async (rawFiles: readonly File[], namespace: string) => {
    const { addFiles } = useUploadStore.getState()
    const { addToast } = useUiStore.getState()

    // Partition into valid and invalid by extension
    const validFiles: File[] = []
    const invalidFiles: File[] = []
    for (const file of rawFiles) {
      if (isValidPluginFile(file)) {
        validFiles.push(file)
      } else {
        invalidFiles.push(file)
      }
    }

    if (invalidFiles.length > 0) {
      const names = invalidFiles.map((f) => f.name).join(', ')
      addToast({
        type: 'warning',
        title: 'Unsupported files skipped',
        message: `Only .jar and .zip files are accepted. Skipped: ${names}`,
      })
    }

    if (validFiles.length === 0) return

    // Ensure config is loaded (cached after first fetch)
    await useConfigStore.getState().fetchConfig()
    const currentMaxMb = useConfigStore.getState().maxFileSizeMb
    const maxBytes = currentMaxMb * 1024 * 1024

    // Add files to store (this filters by extension again, but that's fine)
    addFiles(validFiles)

    // Get the entries that were just added (they are at the end of the array)
    const allEntries = useUploadStore.getState().entries
    const newEntries = allEntries.slice(-validFiles.length)

    // Upload each file in parallel
    const promises = newEntries.map(async (entry) => {
      // File size validation
      if (entry.file.size > maxBytes) {
        useUploadStore.getState().updateEntry(entry.id, {
          status: 'failed',
          errorMessage: `File too large (${(entry.file.size / 1024 / 1024).toFixed(1)} MB). Maximum: ${currentMaxMb} MB.`,
        })
        return
      }

      useUploadStore.getState().updateEntry(entry.id, { status: 'uploading' })

      const formData = new FormData()
      formData.append('artifact', entry.file)

      try {
        await axiosInstance.post(`/namespaces/${namespace}/plugin-releases`, formData, {
          headers: { 'Content-Type': 'multipart/form-data' },
          onUploadProgress: (evt) => {
            if (evt.total) {
              const pct = Math.round((evt.loaded / evt.total) * 100)
              useUploadStore.getState().updateEntry(entry.id, { progress: pct })
            }
          },
        })
        useUploadStore.getState().updateEntry(entry.id, { status: 'success', progress: 100 })
      } catch (err: unknown) {
        const message = axios.isAxiosError(err)
          ? (err.response?.data?.message ?? err.message)
          : err instanceof Error ? err.message : 'Upload failed.'
        useUploadStore.getState().updateEntry(entry.id, { status: 'failed', errorMessage: message })
      }
    })

    await Promise.allSettled(promises)

    // Refresh plugin list once after all uploads complete
    usePluginStore.getState().fetchPlugins(namespace)

    // Summary toast
    const finalEntries = useUploadStore.getState().entries.slice(-validFiles.length)
    const successCount = finalEntries.filter((e) => e.status === 'success').length
    const failCount = finalEntries.filter((e) => e.status === 'failed').length

    if (successCount > 0 && failCount === 0) {
      addToast({
        type: 'success',
        title: 'Upload complete',
        message: successCount === 1
          ? 'Release uploaded successfully.'
          : `${successCount} releases uploaded successfully.`,
      })
    } else if (successCount > 0 && failCount > 0) {
      addToast({
        type: 'warning',
        title: 'Upload partially complete',
        message: `${successCount} succeeded, ${failCount} failed. Check the progress panel for details.`,
      })
    } else if (failCount > 0) {
      addToast({
        type: 'error',
        title: 'Upload failed',
        message: failCount === 1
          ? 'Release upload failed. Check the progress panel for details.'
          : `All ${failCount} uploads failed. Check the progress panel for details.`,
      })
    }
  }, [])

  return { uploadFiles }
}
