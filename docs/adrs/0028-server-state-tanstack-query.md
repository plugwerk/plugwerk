# ADR-0028: Server state moves to TanStack Query; Zustand stays for UI state

## Status

Accepted.

## Context

The 1.0.0-beta.1 security audit flagged three related frontend findings
(triage rows TS-007 / TS-008 / TS-009 in
`docs/audits/1.0.0-beta.1-artifacts/triage-TS.csv`) that all trace back to
the same architectural antipattern: **server-authoritative data cached in
Zustand stores with hand-rolled, uncoordinated invalidation**.

- **TS-007** — `pluginStore` holds `plugins`, `totalElements`, `totalPages`,
  `pendingReviewPluginCount`, and `availableTags` (all server-derived) with
  no TTL, no stale-while-revalidate, and no per-query cancellation. Background
  navigation leaves previous-namespace data on screen.
- **TS-008** — `namespaceStore` fetches `/namespaces` independently of
  `authStore` which tracks the active slug; `NamespacesSection` has to
  manually poke both stores on mutation, and the TopBar dropdown silently
  desyncs from the admin list after creates/deletes.
- **TS-009** — `NamespaceDetailView` calls `namespacesApi.listNamespaces()`
  just to find one entry by slug, fetching the full list on every mount of
  the detail page.

Zustand is the right tool for **client-only UI state** (theme, toasts,
search-query, drag-over flags) but forcing it to double as a server cache
means every consumer reimplements cache-invalidation-by-convention. For
multi-namespace deployments the desync windows are user-visible.

The audit recommendation (both rows) is explicit: *"use a server-state
library (TanStack Query or SWR)"*. TanStack Query is the de-facto standard
for React apps with axios-based APIs, and it dovetails with the
OpenAPI-generated axios clients we already consume.

## Decision

Adopt a two-layer state architecture:

| Concern | Tool |
|---|---|
| **Server state** (anything the backend owns: plugin lists, namespaces, releases, reviews, users, tags, pending counts, …) | **TanStack Query v5** — shared `QueryClient`, per-feature hooks under `src/api/hooks/`, query-key roots defined alongside each hook. |
| **UI state** (theme, toasts, search query, drag-over flag, filter form state, view toggles) | **Zustand** — retained for `uiStore`, `authStore` (credentials + CSRF state only, NOT namespace list), and any future pure-UI stores. |

Concretely:

1. **Infrastructure (this ADR / #276):**
   - Add `@tanstack/react-query` ^5 to `package.json`.
   - Introduce a single `src/api/queryClient.ts` with internal-tool-friendly
     defaults (`staleTime = 60s`, `gcTime = 5min`,
     `refetchOnWindowFocus = false`, retry only on 5xx with cap 2).
   - Wrap the app root in `<QueryClientProvider>` in `src/main.tsx`.
   - Provide a `createQueryWrapper()` test helper and teach the existing
     `renderWithTheme` / `renderWithRouter` / `renderWithRouterAt` helpers
     to inject a fresh per-test `QueryClient` transparently, so existing
     tests pick up the provider without opt-in.

2. **Migration pattern (established by `namespaceStore` in #276):**
   - Delete the Zustand store that holds server data.
   - Add a typed hook under `src/api/hooks/<resource>.ts` that exports:
     - a `queryKeys` root (e.g. `namespacesKeys.list()`), and
     - one or more `useXxx()` hooks returning `useQuery` results.
   - Consumers call the hook directly — no more
     `useXxxStore().fetch() + useEffect` plumbing.
   - Mutation handlers invalidate the shared cache via
     `useQueryClient().invalidateQueries({ queryKey: xxxKeys.list() })`
     instead of manually refreshing sibling components.
   - For resources where the backend has no single-item GET endpoint
     (e.g. `GET /namespaces/{slug}`), a secondary hook (`useNamespace(slug)`)
     filters from the shared list cache — still satisfies the "don't refetch
     a full list per detail view" requirement because all consumers dedupe
     on the same `staleTime` window.

3. **Phased rollout (explicit scope boundary):**
   - **#276 ships `namespaceStore` → `useNamespaces` / `useNamespace`** only,
     closing TS-008 and TS-009.
   - **TS-007 (`pluginStore`)** is larger (list + filters + pagination +
     tags + pending counts) and deferred to a follow-up issue to keep this
     PR reviewable.
   - **`authStore` server-parts** (`namespaceRole`, `fetchNamespaceRole`)
     overlap with the #294 / #315 auth work and are deferred to a separate
     follow-up issue so the TanStack migration does not conflict with the
     in-flight refresh-cookie and OIDC work.

## Architecture

### Query-key convention

Each hooks module owns a `<resource>Keys` root and derives specific keys
from it. Invalidation walks from the root, so mutation callers never
hand-assemble query keys:

```ts
export const namespacesKeys = {
  all: ["namespaces"] as const,
  list: () => [...namespacesKeys.all, "list"] as const,
} as const;
```

Future hooks (`pluginsKeys`, `reviewsKeys`, …) follow the same shape. This
is the standard TanStack convention; no bespoke key factory.

### QueryClient defaults

```ts
export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 60_000,          // background refetch no sooner than 60s
      gcTime: 5 * 60_000,         // retain cache 5 min after last observer
      refetchOnWindowFocus: false, // internal tool — no tab-switch churn
      retry: (failureCount, error) => {
        const status = (error as { response?: { status?: number } })
          ?.response?.status;
        if (status && status >= 400 && status < 500) return false;
        return failureCount < 2;
      },
    },
  },
});
```

- `refetchOnWindowFocus: false` matches the UX of an internal admin tool —
  switching tabs should not trigger a wall of toasts.
- 4xx errors are not retried (authorization / validation failures are
  deterministic). 5xx errors retry at most twice.
- `staleTime` of 60s is conservative; individual hooks can override per
  resource.

### Test wrapper

`createQueryWrapper()` in `src/test/queryWrapper.tsx` produces a
*fresh-per-test* `QueryClient` with `retry: false`, `gcTime: 0`,
`staleTime: 0` — deterministic, no cross-test bleed, no retry waits. Used
transparently by `renderWithTheme` / `renderWithRouter` so existing tests
do not need to opt in.

### Shared-cache invalidation (the TS-008 fix)

Old pattern:

```ts
// NamespacesSection.tsx — before
function handleCreated() {
  useNamespaceStore.getState().fetchNamespaces();
  // TopBar dropdown only refreshes because it happens to subscribe
  // to the same Zustand slice. Order-dependent, fragile.
}
```

New pattern:

```ts
// NamespacesSection.tsx — after
const queryClient = useQueryClient();

function handleCreated() {
  queryClient.invalidateQueries({ queryKey: namespacesKeys.list() });
  // Every consumer (TopBar, ProfileSettings, NamespaceDetailView, …)
  // refetches automatically on next render.
}
```

### Detail-by-filter fallback (the TS-009 fix)

Since `NamespacesApi` has no `getNamespace(slug)` endpoint in the current
OpenAPI, `useNamespace(slug)` filters from the shared list cache:

```ts
export function useNamespace(slug: string | null | undefined) {
  const query = useNamespaces();
  const found = slug ? query.data?.find((ns) => ns.slug === slug) : undefined;
  return { namespace: found, isLoading: query.isLoading, error: query.error };
}
```

When any other consumer has already fetched the list within `staleTime`,
this resolves synchronously — no extra network round-trip. The audit
recommendation's primary concern was "don't fetch the whole list per
detail mount"; the shared cache satisfies that without a backend change.
If the namespace list grows large enough that server-side pagination
becomes necessary, the fallback here breaks at the same point the audit
finding originally anticipated, and a dedicated `GET /namespaces/{slug}`
can be added then.

## Consequences

### Good

- **Single source of truth for each server resource.** Mutation
  invalidates one query-key; every consumer updates in the next render.
- **No more `fetchXxx + useEffect([])` boilerplate** at component level.
  Hooks are declarative; loading / error / data are first-class.
- **Automatic deduplication.** `useNamespaces()` called in three sibling
  components fires one HTTP request, not three.
- **Stale-while-revalidate out of the box.** Users see cached data
  instantly on route mount; background refetch refreshes it.
- **Structured cache invalidation for future features** (optimistic updates,
  per-namespace cache scoping, offline-friendly behaviour) — none required
  now, all cheap to add later.

### Neutral

- **Bundle size:** `@tanstack/react-query` adds ~13 KB gzipped. Acceptable
  for an admin SPA; offset by deleted Zustand boilerplate in later phases.
- **Two state layers require discipline.** A reviewer needs to ask, for
  each new piece of state: "is the server authoritative here?" — if yes,
  it belongs in a hook; if no, it belongs in a Zustand store or local
  `useState`. This ADR is the written rule; review checklist entry to follow.

### Follow-up work (shipped after this ADR)

- **`pluginStore` → TanStack Query** (TS-007 scope) — shipped in #328
  (`src/api/hooks/usePlugins.ts`). Filter form state remains in Zustand
  as the UI-only layer; server state (plugins, tags, pending-review
  counts) is now fully in TanStack Query.
- **`authStore` server-parts → TanStack Query** — shipped in #329
  (`src/api/hooks/useNamespaceRole.ts`). `namespaceRole` and
  `fetchNamespaceRole` removed from `authStore`. `authStore` is now
  purely credential + UI-preference state (access token, username,
  `isSuperadmin`, `passwordChangeRequired`, `isAuthenticated`,
  `isHydrating`, active `namespace`). The
  `AuthHydrationBoundary` drops the entire TanStack cache on
  authenticated→unauthenticated transitions so previous-user role
  caches do not bleed into the next login.

## References

- Issue [#276]; audit rows TS-007 (deferred), TS-008 (fixed), TS-009
  (fixed) in `docs/audits/1.0.0-beta.1-artifacts/triage-TS.csv`.
- Established precedent for shared-cache invalidation via query-key roots —
  [TanStack Query v5 docs](https://tanstack.com/query/v5/docs/framework/react/guides/query-invalidation).
- Touched files (this ADR):
  - `plugwerk-server/plugwerk-server-frontend/package.json`
  - `plugwerk-server/plugwerk-server-frontend/src/api/queryClient.ts`
  - `plugwerk-server/plugwerk-server-frontend/src/api/hooks/useNamespaces.ts`
  - `plugwerk-server/plugwerk-server-frontend/src/main.tsx`
  - `plugwerk-server/plugwerk-server-frontend/src/test/queryWrapper.tsx`
  - `plugwerk-server/plugwerk-server-frontend/src/test/renderWithTheme.tsx`
  - `plugwerk-server/plugwerk-server-frontend/src/components/layout/TopBar.tsx`
  - `plugwerk-server/plugwerk-server-frontend/src/components/admin/NamespacesSection.tsx`
  - `plugwerk-server/plugwerk-server-frontend/src/components/admin/NamespaceDetailView.tsx`
  - `plugwerk-server/plugwerk-server-frontend/src/pages/ProfileSettingsPage.tsx`
  - `plugwerk-server/plugwerk-server-frontend/src/pages/CatalogPage.tsx`
  - (removed) `plugwerk-server/plugwerk-server-frontend/src/stores/namespaceStore.ts`

[#276]: https://github.com/plugwerk/plugwerk/issues/276
