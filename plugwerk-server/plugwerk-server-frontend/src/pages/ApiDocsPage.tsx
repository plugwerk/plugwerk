// SPDX-License-Identifier: AGPL-3.0
// Copyright (C) 2026 devtank42 GmbH
import { ApiReferenceReact } from '@scalar/api-reference-react'
import '@scalar/api-reference-react/style.css'
import { useEffect, useMemo } from 'react'
import { useUiStore } from '../stores/uiStore'

export function ApiDocsPage() {
  const themeMode = useUiStore((s) => s.themeMode)
  const isDark = themeMode === 'dark'

  // Scalar uses a module-level colorMode singleton and reads localStorage
  // on init, so the darkMode prop alone is not enough to control the mode
  // after the first render. The actual styling is driven by 'dark-mode' /
  // 'light-mode' CSS classes on document.body — we sync them here.
  useEffect(() => {
    localStorage.setItem('colorMode', isDark ? 'dark' : 'light')
    document.body.classList.toggle('dark-mode', isDark)
    document.body.classList.toggle('light-mode', !isDark)
    return () => {
      document.body.classList.remove('dark-mode', 'light-mode')
    }
  }, [isDark])

  const configuration = useMemo(
    () => ({
      url: '/api-docs/openapi.yaml',
      darkMode: isDark,
      hideDownloadButton: false,
      layout: 'modern' as const,
    }),
    [isDark],
  )

  return (
    <ApiReferenceReact
      key={themeMode}
      configuration={configuration}
    />
  )
}
