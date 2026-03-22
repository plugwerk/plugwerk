# ADR-0004: Frontend Conventions (React + TypeScript + MUI + Zustand)

## Status

Accepted

## Context

Milestone 6 introduced the Plugwerk web UI — a React single-page application embedded in the server JAR
and served by `SpaController`. During implementation we established conventions for the entire frontend
stack: technology choices, project structure, API client generation, state management, component design,
authentication, error handling, and testing.

## Decision

### Technology Stack

| Component | Technology | Version |
|-----------|-----------|---------|
| Language | TypeScript | ~5.9.x |
| Framework | React | 19.x |
| UI Library | Material UI (MUI) | 7.x |
| Icons | lucide-react | 0.x (no CDN, bundled) |
| State Management | Zustand | 5.x |
| HTTP Client | Axios | 1.x |
| Router | React Router | 7.x |
| File Upload | react-dropzone | 15.x |
| Markdown | react-markdown + rehype-sanitize | 10.x + 6.x |
| Build | Vite | 8.x |
| Test Runner | Vitest | 4.x |
| Test Utilities | @testing-library/react + @testing-library/user-event | 16.x + 14.x |
| API Codegen | openapi-generator-cli (typescript-axios) | 7.12.x |
| Fonts | @fontsource/inter + @fontsource/jetbrains-mono | bundled, no CDN |

**Constraint**: No external CDN resources. All dependencies (fonts, icons, libraries) must be bundled.

### Project Structure

```
plugwerk-server-frontend/
├── public/                    # Static assets copied verbatim to build output
│   ├── favicon.svg
│   ├── logo-dark.svg          # Theme-aware full logo (dark mode)
│   ├── logo-light.svg         # Theme-aware full logo (light mode)
│   └── logomark.svg           # Square logomark for login/compact contexts
├── src/
│   ├── api/
│   │   ├── config.ts          # Axios instance, interceptors, named API objects
│   │   └── generated/         # AUTO-GENERATED — do not edit manually (openapi-generator)
│   ├── components/
│   │   ├── admin/             # Admin-specific components
│   │   ├── auth/              # AuthCard, ProtectedRoute
│   │   ├── catalog/           # FilterBar, PluginCard, PluginListRow, PaginationBar, …
│   │   ├── common/            # Reusable primitives: Badge, CodeBlock, EmptyState, Toast, FilterSelect
│   │   ├── layout/            # TopBar, Footer, PageWrapper
│   │   ├── plugin-detail/     # PluginHeader, OverviewTab, VersionsTab, ChangelogTab, DependenciesTab, DetailSidebar
│   │   └── upload/            # UploadModal
│   ├── pages/                 # Route-level components (one file per route)
│   │   └── errors/            # Error pages (403, 404, 500, 503)
│   ├── router/
│   │   └── index.tsx          # createBrowserRouter, route definitions
│   ├── shims/
│   │   └── url.ts             # Node.js 'url' shim for openapi-generator output
│   ├── stores/                # Zustand stores (one file per domain)
│   ├── test/                  # Test utilities (renderWithTheme, setup.ts)
│   ├── theme/
│   │   ├── theme.ts           # MUI theme (light + dark palette)
│   │   └── tokens.ts          # Design tokens (colors, radii, spacing)
│   ├── App.tsx                # ThemeProvider, RouterProvider
│   ├── AppShell.tsx           # Layout shell (TopBar + Outlet + Footer)
│   └── main.tsx               # React entry point
```

**Rules:**
- Organize by **feature/domain**, not by type (no top-level `utils/` or `hooks/` dumping ground).
- Page components live in `pages/`, layout in `components/layout/`, domain in `components/<domain>/`.
- Files: 200–400 lines typical, 800 max. Split when a component grows beyond that.

### OpenAPI Code Generation

The API client is generated from `plugwerk-api/src/main/resources/openapi/plugwerk-api.yaml`.

```bash
# Run from plugwerk-server-frontend/:
npm run generate:api
# Equivalent:
openapi-generator-cli generate   # reads openapitools.json
```

`openapitools.json` configures:
- Generator: `typescript-axios`
- Input: `../../plugwerk-api/src/main/resources/openapi/plugwerk-api.yaml`
- Output: `src/api/generated/`
- Options: `withSeparateModelsAndApi=true`, `modelPackage=model`, `apiPackage=api`, `useSingleRequestParameter=false`

**Rules:**
- **Never edit `src/api/generated/` manually.** Re-generate after every YAML change.
- The `url` module shim in `vite.config.ts` resolves a Node.js import in the generated output — keep it.
- The generated code is excluded from Vitest coverage thresholds (`src/api/generated/**`).
- Re-generation sequence: change YAML → run `./gradlew :plugwerk-api:openApiGenerate` (backend) → run
  `npm run generate:api` (frontend).

### Axios Instance and Interceptors

A single shared `axiosInstance` in `src/api/config.ts` is the HTTP boundary:

```typescript
const axiosInstance = axios.create({ baseURL: '/api/v1' })

// Request interceptor — attaches Bearer token from localStorage
axiosInstance.interceptors.request.use((config) => {
  const token = localStorage.getItem('pw-access-token')
  if (token) config.headers['Authorization'] = `Bearer ${token}`
  return config
})

// Response interceptor — clears session on 401 and redirects to login
axiosInstance.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem('pw-access-token')
      localStorage.removeItem('pw-username')
      window.location.href = '/login'   // full reload intentional — clears all React state
    }
    return Promise.reject(error)
  },
)
```

Named API objects wrap the generated classes with the shared `axiosInstance`:
```typescript
export const catalogApi = new CatalogApi(apiConfig, BASE_PATH, axiosInstance)
export const managementApi = new ManagementApi(apiConfig, BASE_PATH, axiosInstance)
```

**Do not import** individual `*Api` classes directly from the generated output — always use the named
exports from `src/api/config.ts`.

### Zustand Store Conventions

One store file per domain. Store files live in `src/stores/` and follow this pattern:

```typescript
// src/stores/exampleStore.ts
import { create } from 'zustand'

interface ExampleState {
  // State fields
  value: string

  // Actions
  setValue: (v: string) => void
}

export const useExampleStore = create<ExampleState>((set, get) => ({
  value: '',
  setValue: (v) => set({ value: v }),
}))
```

**Rules:**
- State is **immutable** — always use `set({ ... })`, never mutate the state object directly.
- UI state (modal open/close, toast queue, theme, search query) → `uiStore`.
- Auth state (access token, username, namespace, login/logout) → `authStore`.
- Domain state (plugins list, filters, pagination) → `pluginStore`.
- Namespace list → `namespaceStore`.
- `localStorage` persistence: only `authStore` and `uiStore` (theme preference).

### Authentication (Phase 1)

The frontend uses JWT Bearer tokens issued by `POST /api/auth/login`.

| Item | Detail |
|------|--------|
| Token storage | `localStorage` (`pw-access-token`) |
| Username storage | `localStorage` (`pw-username`) |
| Namespace storage | `localStorage` (`pw-namespace`) |
| Token attachment | Axios request interceptor (see above) |
| Session expiry | 401 response → clear localStorage + redirect to `/login` |
| Route protection | `<ProtectedRoute>` wrapper — redirects to `/login?from=<current-path>` |
| Login redirect | After login, `navigate(from, { replace: true })` |

**Phase 2**: Replace `localStorage` token storage with httpOnly cookies and `useAuthStore` with OIDC.

### Component Conventions

- **Named exports only** — no default exports from component files.
- **License header** on every `.tsx`/`.ts` file:
  ```typescript
  // SPDX-License-Identifier: AGPL-3.0
  // Copyright (C) 2026 devtank42 GmbH
  ```
- **MUI `sx` prop** for styling — no CSS files, no inline `style={{}}` for complex rules.
- **Design tokens** from `src/theme/tokens.ts` for colors, radii, and spacing constants.
  Do not hardcode hex values or pixel values that exist in `tokens`.
- **lucide-react** for all icons. Icon size `15` in buttons, `16–18` in toolbars, `20–36` in content areas.
- **Accessibility**: every interactive element needs an `aria-label`. Use semantic roles
  (`role="list"`, `role="search"`, `role="banner"`, `role="alert"`). Provide skip links on layout shells.

### Error Handling in Components

```typescript
// Backend errors from Axios
catch (err: unknown) {
  const message = axios.isAxiosError(err)
    ? (err.response?.data?.message ?? err.message)
    : err instanceof Error ? err.message : 'Unknown error'
  setError(message)
}
```

**Rule**: Always use `axios.isAxiosError()` to extract backend `message` from the error response body.
Never show raw HTTP status codes to the user.

### Markdown Rendering

Use `react-markdown` with `rehype-sanitize` — never `dangerouslySetInnerHTML`:

```tsx
import ReactMarkdown from 'react-markdown'
import rehypeSanitize from 'rehype-sanitize'

<ReactMarkdown rehypePlugins={[rehypeSanitize]}>{content}</ReactMarkdown>
```

### SPA Routing (Backend Side)

`SpaController` (`src/main/kotlin/.../controller/SpaController.kt`) forwards all client-side routes
to `index.html`. When adding a new top-level route in React Router, add the corresponding path to
`SpaController`'s `@GetMapping` value list so that deep links and browser refreshes work correctly.

### Design Tokens

Design tokens in `src/theme/tokens.ts` mirror `docs/design/html-templates/tokens.css`:

| Token | Value | Usage |
|-------|-------|-------|
| `tokens.color.primary` | `#0F62FE` | Brand color, active states, focus rings |
| `tokens.color.primaryDark` | `#0043CE` | Hover states on primary elements |
| `tokens.color.primaryLight` | `#D0E2FF` | Subtle highlights, drag-over backgrounds |
| `tokens.color.gray10` | `#F4F4F4` | Input backgrounds (light mode) |
| `tokens.color.gray20` | `#E0E0E0` | Borders (light mode) |
| `tokens.color.gray40` | `#A8A8A8` | Disabled / placeholder icons |
| `tokens.radius.btn` | `4px` | Buttons, inputs |
| `tokens.radius.card` | `8px` | Cards, dialogs, containers |

### Frontend Development Scripts

Run all commands from `plugwerk-server/plugwerk-server-frontend/`:

| Command | Description |
|---------|-------------|
| `npm run dev` | Vite dev server with HMR at `http://localhost:5173`, proxies `/api` → `http://localhost:8080` |
| `npm run build` | TypeScript check + Vite production build → `build/dist/` (picked up by Gradle) |
| `npm run lint` | ESLint with TypeScript + React Hooks rules |
| `npm run preview` | Serve the production build locally |
| `npm run generate:api` | Regenerate TypeScript API client from OpenAPI spec |
| `npm test` | Vitest in watch mode |
| `npm run test:run` | Vitest single run (CI) |
| `npm run test:coverage` | Vitest with V8 coverage report |

### Testing Conventions

- **Framework**: Vitest 4.x + @testing-library/react + jsdom
- **Test files**: colocated with the source file (`*.test.tsx` / `*.test.ts`)
- **Coverage threshold**: 80% lines / functions / branches (enforced by `vitest.config.ts`)
- **Excluded from coverage**: `src/api/generated/**`, `src/test/**`
- **Test helper**: `renderWithRouter()` from `src/test/renderWithTheme.tsx` — wraps the component
  in `ThemeProvider + MemoryRouter + RouterContext`. Use this for all component tests.
- **Store setup**: `useXxxStore.setState({ ... })` in `beforeEach` — never import stores and call
  actions directly in tests; always set state explicitly.
- **Mocking axios**: `vi.mock('../../api/config', () => ({ axiosInstance: { post: vi.fn(), get: vi.fn() } }))`.
  Use `vi.mocked(apiConfig.axiosInstance.post)` to access the mock in test bodies.
  Do **not** reference variables declared before `vi.mock` inside the mock factory (hoisting trap).
- **Disabled buttons**: MUI disabled buttons have `pointer-events: none` — test with
  `expect(button).toBeDisabled()` instead of clicking them.
- **Async assertions**: use `waitFor(() => ..., { timeout: 15000 })` with a 20 000 ms test timeout
  for upload/fetch interactions that run through multiple React state cycles.

### Build Integration into Server JAR

The frontend is built by Gradle and embedded in the Spring Boot JAR:

```
plugwerk-server-frontend/build.gradle.kts:
  - executes `npm run build` as part of Gradle's processResources
  - copies build/dist/ → backend's src/main/resources/static/
```

The backend's `SpaController` forwards all SPA routes to `index.html`. Static assets under `/assets/**`,
`/*.svg`, and `/*.ico` are served directly by Spring's static resource handler.

## Consequences

- **Easier**: TypeScript types are always in sync with the backend API — the generator catches contract
  mismatches at build time.
- **Easier**: Zustand's flat store model and direct `setState` in tests eliminate boilerplate compared
  to Redux.
- **Easier**: MUI's `sx` prop and design tokens keep all styling co-located with the component — no
  separate CSS files to maintain.
- **Easier**: `react-markdown + rehype-sanitize` provides safe Markdown rendering without XSS risk.
- **Harder**: The generated `src/api/generated/` directory must be re-generated after every OpenAPI
  YAML change — forgetting this causes TypeScript errors.
- **Harder**: `vi.mock` factory hoisting is a non-obvious Vitest behavior — variables declared before
  the factory are `undefined` inside it.
- **Harder**: `localStorage`-based token storage is vulnerable to XSS. Phase 2 must migrate to
  httpOnly cookies before exposing the UI to untrusted content.
- **Harder**: MUI disabled buttons set `pointer-events: none`, which silently swallows `userEvent.click`
  in tests — assertions on `toBeDisabled()` are required instead.
