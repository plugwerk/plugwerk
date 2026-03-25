import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import path from 'path'

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      // The openapi-generator typescript-axios template imports { URL, URLSearchParams } from 'url'
      // (a Node.js built-in). Browsers already have these as globals, so we shim the module.
      url: path.resolve(__dirname, 'src/shims/url.ts'),
    },
  },
  build: {
    outDir: 'build/dist',
  },
  server: {
    proxy: {
      // Proxy all REST API calls and the OpenAPI YAML to the Spring Boot backend.
      // Use '/api/' (with trailing slash) to avoid matching '/api-docs' (the SPA page).
      '/api/': 'http://localhost:8080',
      '/api-docs/openapi.yaml': 'http://localhost:8080',
    },
  },
})
