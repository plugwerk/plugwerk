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
      //
      // changeOrigin MUST be false so the Host header reaches Spring as
      // `localhost:5173`. Spring uses that header to build the `redirect_uri`
      // it hands to the upstream provider — if Spring saw `localhost:8080`
      // it would tell Keycloak to redirect the browser there, the JSESSIONID
      // cookie that holds the OAuth2 state + PKCE verifier (set on the
      // :5173 origin) would not travel along, and the callback would land
      // on a stateless Spring with no idea what to do. Symptom: a Spring
      // Whitelabel error page on /login/oauth2/code/{id}.
      "/oauth2/": {
        target: "http://localhost:8080",
        changeOrigin: false,
      },
      "/login/oauth2/": {
        target: "http://localhost:8080",
        changeOrigin: false,
      },
    },
  },
});
