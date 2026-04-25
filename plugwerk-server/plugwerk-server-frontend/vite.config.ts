import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import path from "path";

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      // The openapi-generator typescript-axios template imports { URL, URLSearchParams } from 'url'
      // (a Node.js built-in). Browsers already have these as globals, so we shim the module.
      url: path.resolve(__dirname, "src/shims/url.ts"),
    },
  },
  build: {
    outDir: "build/dist",
  },
  server: {
    proxy: {
      // Proxy all REST API calls and the OpenAPI YAML to the Spring Boot backend.
      // Use '/api/' (with trailing slash) to avoid matching '/api-docs' (the SPA page).
      "/api/": "http://localhost:8080",
      "/api-docs/openapi.yaml": "http://localhost:8080",
      // OAuth2 browser-flow endpoints managed by Spring Security itself (issue #79).
      // /oauth2/authorization/{id} starts the flow; /login/oauth2/code/{id} is the
      // upstream-provider callback. Both must reach Spring rather than be served by
      // Vite's SPA fallback (which would return index.html and the React router
      // would render a 404). In production the frontend is bundled into the backend
      // JAR and Spring serves both the SPA and these routes from the same origin,
      // so this proxy entry is dev-only.
      "/oauth2/": "http://localhost:8080",
      "/login/oauth2/": "http://localhost:8080",
    },
  },
});
