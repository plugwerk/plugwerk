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
      '/api': 'http://localhost:8080',
    },
  },
})
