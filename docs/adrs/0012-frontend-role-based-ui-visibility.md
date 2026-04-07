# ADR-0012: Frontend Role-Based UI Visibility

## Status

Accepted

## Context

The Plugwerk frontend previously showed all UI elements (navigation items, action buttons, admin pages) to every authenticated user, regardless of their role or permissions. The backend enforced access control via API responses (401/403), but the frontend provided no visual feedback — users could navigate to pages they weren't authorized to see, and action buttons were visible even when the underlying API would reject the request.

This created a confusing user experience and a potential security concern (information leakage through UI structure). Issue #176 (catalog improvements) and follow-up work required a consistent approach to role-based UI visibility across the entire frontend.

## Decision

### Authentication State

The `LoginResponse` from the backend now includes an `isSuperadmin` boolean field. The frontend `authStore` (Zustand) persists this alongside the existing state:

| State Field | Source | Persistence |
|---|---|---|
| `isAuthenticated` | Login success | `localStorage` |
| `username` | Login response | `localStorage` |
| `isSuperadmin` | Login response (`isSuperadmin`) | `localStorage` |
| `namespaceRole` | `GET /namespaces/{ns}/members/me` | In-memory (refetched on namespace switch) |
| `namespace` | User selection / `initNamespace()` | `localStorage` |

### Role Hierarchy

The system distinguishes five actor types with decreasing privileges:

| Actor | `isSuperadmin` | `namespaceRole` | Description |
|---|---|---|---|
| System Admin | `true` | — | Full access to everything |
| Namespace Admin | `false` | `ADMIN` | Full access within their namespace(s) |
| Namespace Member | `false` | `MEMBER` | Read-write within namespace, no admin actions |
| Read-Only User | `false` | `READ_ONLY` | Browse and download only |
| Unauthenticated | — | — | Public catalog only |

### UI Visibility Rules

#### Navigation Items (TopBar)

| Element | Visible When |
|---|---|
| Catalog | Always (when namespace selected) |
| Upload | `isSuperadmin \|\| namespaceRole === 'ADMIN' \|\| namespaceRole === 'MEMBER'` |
| Admin | `isSuperadmin \|\| namespaceRole === 'ADMIN'` |

#### Route Guards

| Route Pattern | Guard | Unauthorized Behavior |
|---|---|---|
| `/admin/*` | `AdminRoute` (wraps `ProtectedRoute`) | Redirect to `/403` |
| All protected routes | `ProtectedRoute` | Redirect to `/login` |
| Public routes | None | Accessible to all |

`AdminRoute` is a new guard component (`src/components/auth/AdminRoute.tsx`) that checks `isSuperadmin \|\| namespaceRole === 'ADMIN'` and redirects to the 403 Forbidden error page if the condition is not met.

#### Catalog Visibility (Backend-Enforced)

Plugin visibility in the catalog depends on `CatalogVisibility`, determined by the caller's role:

| Visibility Level | Actor | Visible Plugins | Visible Releases |
|---|---|---|---|
| `PUBLIC` | Unauthenticated, API Key | Only `ACTIVE` with published releases | `published`, `deprecated` |
| `AUTHENTICATED` | Member, Read-Only | All except `SUSPENDED`, incl. draft-only | All |
| `ADMIN` | Namespace Admin, System Admin | All statuses | All |

The `CatalogController.resolveVisibility()` method determines the level from `SecurityContextHolder`:
1. No authentication / API key (`key:` prefix) -> `PUBLIC`
2. Superadmin -> `ADMIN`
3. Namespace Admin -> `ADMIN`
4. Namespace Member / Read-Only -> `AUTHENTICATED`
5. Fallback -> `PUBLIC`

This visibility is applied consistently in both `listPlugins` and `listTags` endpoints.

#### Action Buttons in Plugin Details

| Action | Visible When |
|---|---|
| Download | Always (when a release exists) |
| Change Release Status | `namespaceRole === 'ADMIN'` (via `canApprove` prop) |
| Delete Release | `namespaceRole === 'ADMIN'` (via `canApprove` prop) |
| Approve Draft | `namespaceRole === 'ADMIN'` (via `canApprove` prop) |
| Change Plugin Status | `isAdmin` prop passed from parent |
| Delete Plugin | `isAdmin` prop passed from parent |

### Implementation Pattern

The frontend uses a **check-at-render** pattern rather than a centralized permission service:

```tsx
// Navigation: destructure from store, conditionally render
const { isSuperadmin, namespaceRole } = useAuthStore()
{(isSuperadmin || namespaceRole === 'ADMIN') && <AdminButton />}

// Route guard: dedicated wrapper component
<ProtectedRoute>
  <AdminRoute>
    <AdminSettingsPage />
  </AdminRoute>
</ProtectedRoute>
```

This approach was chosen because:
- The permission model is simple (two dimensions: `isSuperadmin` + `namespaceRole`)
- MUI components render conditionally with standard JSX (`{condition && <Component />}`)
- A centralized permission service would add indirection without reducing complexity at this scale

### Important: Defense in Depth

Frontend visibility is a UX convenience, not a security boundary. The backend **always** enforces authorization independently:
- API endpoints validate roles via `NamespaceAuthorizationService.requireRole()`
- The `PublicNamespaceFilter` controls anonymous access
- The `NamespaceAccessKeyAuthFilter` limits API key scope
- Unauthorized API calls return 401/403 regardless of frontend state

## Consequences

### Positive

- Users only see UI elements they can actually use, reducing confusion and support burden
- Direct URL access to `/admin/*` by unauthorized users shows a proper 403 page instead of broken API error states
- Catalog content matches the user's actual access level (no phantom plugins or missing tags)
- `isSuperadmin` in `LoginResponse` avoids an extra API call after login
- Pattern is simple and consistent across the codebase

### Negative

- `isSuperadmin` is cached in `localStorage` and could become stale if admin status is revoked mid-session (mitigated by token expiry and backend enforcement)
- `namespaceRole` is refetched on namespace switch but could lag behind server-side changes within a session
- Adding new permission dimensions (e.g., per-plugin roles) would require extending the store and all visibility checks
- Frontend and backend visibility rules must be kept in sync manually — no shared permission definition
