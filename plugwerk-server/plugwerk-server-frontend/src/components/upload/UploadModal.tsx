// SPDX-License-Identifier: AGPL-3.0
// Copyright (C) 2026 devtank42 GmbH
import { useState, useCallback, useEffect } from 'react'
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  Box,
  Typography,
  Alert,
  LinearProgress,
  IconButton,
} from '@mui/material'
import { X, UploadCloud, FileBox } from 'lucide-react'
import { useDropzone } from 'react-dropzone'
import axios from 'axios'
import { axiosInstance } from '../../api/config'
import { useUiStore } from '../../stores/uiStore'
import { useAuthStore } from '../../stores/authStore'
import { usePluginStore } from '../../stores/pluginStore'
import { tokens } from '../../theme/tokens'

const DEFAULT_MAX_FILE_SIZE_MB = 100

export function UploadModal() {
  const { uploadModalOpen, closeUploadModal, addToast } = useUiStore()
  const { namespace } = useAuthStore()

  const [file, setFile] = useState<File | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [progress, setProgress] = useState<number | null>(null)
  const [maxFileSizeMb, setMaxFileSizeMb] = useState(DEFAULT_MAX_FILE_SIZE_MB)

  useEffect(() => {
    if (!uploadModalOpen) return
    axiosInstance.get('/config')
      .then((res) => {
        const limit = res.data?.upload?.maxFileSizeMb
        if (typeof limit === 'number' && limit > 0) setMaxFileSizeMb(limit)
      })
      .catch(() => { /* use default */ })
  }, [uploadModalOpen])

  const maxFileSizeBytes = maxFileSizeMb * 1024 * 1024

  const onDrop = useCallback((accepted: File[]) => {
    if (accepted[0]) {
      if (accepted[0].size > maxFileSizeBytes) {
        setError(`File is too large (${(accepted[0].size / 1024 / 1024).toFixed(1)} MB). Maximum allowed size is ${maxFileSizeMb} MB.`)
        setFile(null)
        return
      }
      setFile(accepted[0])
      setError(null)
    }
  }, [maxFileSizeBytes, maxFileSizeMb])

  const { getRootProps, getInputProps, isDragActive } = useDropzone({
    onDrop,
    accept: { 'application/java-archive': ['.jar'], 'application/zip': ['.jar'] },
    multiple: false,
  })

  function handleClose() {
    if (progress !== null) return
    setFile(null)
    setError(null)
    setProgress(null)
    closeUploadModal()
  }

  async function handleUpload() {
    if (!file) {
      setError('Please select a .jar file.')
      return
    }
    setError(null)
    setProgress(0)

    const formData = new FormData()
    formData.append('artifact', file)

    try {
      await axiosInstance.post(`/namespaces/${namespace}/plugin-releases`, formData, {
        headers: { 'Content-Type': 'multipart/form-data' },
        onUploadProgress: (evt) => {
          if (evt.total) setProgress(Math.round((evt.loaded / evt.total) * 100))
        },
      })
      addToast({ type: 'success', title: 'Release uploaded', message: 'Your release is pending review.' })
      usePluginStore.getState().fetchPlugins(namespace)
      handleClose()
    } catch (err: unknown) {
      const message = axios.isAxiosError(err)
        ? (err.response?.data?.message ?? err.message)
        : err instanceof Error ? err.message : 'Upload failed.'
      setError(message)
      setProgress(null)
    }
  }

  return (
    <Dialog open={uploadModalOpen} onClose={handleClose} maxWidth="sm" fullWidth>
      <DialogTitle sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', pr: 1 }}>
        Upload Plugin Release
        <IconButton onClick={handleClose} size="small" aria-label="Close upload dialog" disabled={progress !== null}>
          <X size={18} />
        </IconButton>
      </DialogTitle>

      <DialogContent sx={{ display: 'flex', flexDirection: 'column', gap: 2, pt: 1 }}>
        <Typography variant="body2" color="text.secondary">
          Drop a plugin <strong>.jar</strong> or <strong>.zip</strong> file. All metadata (plugin ID, version,
          dependencies) is read from the descriptor inside the archive.
        </Typography>

        {error && (
          <Alert severity="error" onClose={() => setError(null)}>
            {error}
          </Alert>
        )}

        <Box
          {...getRootProps()}
          sx={{
            border: `2px dashed ${isDragActive ? tokens.color.primary : tokens.color.gray20}`,
            borderRadius: tokens.radius.card,
            p: 4,
            textAlign: 'center',
            cursor: progress !== null ? 'not-allowed' : 'pointer',
            background: isDragActive ? tokens.color.primaryLight + '22' : 'background.default',
            transition: 'border-color 0.15s, background 0.15s',
            '&:hover': { borderColor: progress === null ? tokens.color.primary : undefined },
          }}
        >
          <input {...getInputProps()} aria-label="Select plugin JAR or ZIP file" disabled={progress !== null} />
          <Box sx={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 1.5 }}>
            {file ? (
              <>
                <FileBox size={36} color={tokens.color.primary} />
                <Typography variant="body2" fontWeight={600}>{file.name}</Typography>
                <Typography variant="caption" color="text.disabled">
                  {(file.size / 1024 / 1024).toFixed(2)} MB · Click to replace
                </Typography>
              </>
            ) : (
              <>
                <UploadCloud size={36} color={tokens.color.gray40} />
                <Typography variant="body2" fontWeight={600}>
                  {isDragActive ? 'Drop the file here…' : 'Drag & drop a .jar or .zip file here'}
                </Typography>
                <Typography variant="caption" color="text.disabled">
                  or click to browse · Max. {maxFileSizeMb} MB
                </Typography>
              </>
            )}
          </Box>
        </Box>

        {progress !== null && (
          <Box>
            <Typography variant="caption" color="text.secondary">Uploading… {progress}%</Typography>
            <LinearProgress variant="determinate" value={progress} sx={{ mt: 0.5 }} />
          </Box>
        )}
      </DialogContent>

      <DialogActions sx={{ px: 3, pb: 2 }}>
        <Button onClick={handleClose} disabled={progress !== null}>Cancel</Button>
        <Button
          variant="contained"
          onClick={handleUpload}
          disabled={!file || progress !== null}
        >
          {progress !== null ? 'Uploading…' : 'Upload Release'}
        </Button>
      </DialogActions>
    </Dialog>
  )
}
