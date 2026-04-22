# ADR-0027: Refresh cookie, in-memory access token, scoped CSRF re-enablement

## Status

Accepted — supersedes [ADR-0020](0020-csrf-stateless-decision.md).

## Context

The 1.0.0-beta.1 security audit (tracker [#255]) flagged three related findings around the web-UI auth credential:

- **TS-001** — the JWT access token was persisted in `localStorage`. Any XSS path — including in a third-party asset — could read and exfiltrate it in one line (`localStorage.getItem('pw-access-token')`).
- **TS-002** — the axios interceptor read the token from `localStorage` on every request, making it trivial to hook or wrap and leak all in-flight bearers.
- **TS-003** — `downloadArtifact(url, filename)` attached the bearer to any caller-supplied URL with no origin validation. A crafted `url` parameter could exfiltrate the bearer to a third-party host via the browser's `fetch`.

ADR-0020 had explicitly documented that the existing architecture ("stateless JWT in localStorage, no ambient cookies") kept CSRF off as a sound decision, **with the revisit condition that any move to a cookie-based auth credential must re-enable CSRF**. This ADR records the architectural turn described by that revisit clause.

### Scope note — OIDC

Plugwerk currently accepts external OIDC JWTs in **resource-server mode** (the backend validates Bearer tokens issued by registered providers; the web UI does not run an OIDC code-exchange today). This ADR's refresh-cookie flow is strictly for self-issued tokens (`iss == plugwerk.server.base-url`). When a web-UI OIDC login lands in Phase 2 (tracking [#315]), the **token-exchange** shape is already decided: the backend will perform the OIDC code exchange, map to a local `UserEntity`, and issue a Plugwerk-native access + refresh pair — identical to the flow below from step 2 onwards. External Bearer-only clients remain entirely outside this ADR's scope.

## Decisions

### 1. Refresh-token model

**Opaque, DB-backed, rotating refresh tokens** (option a+c from the planning discussion — rejected both stateless JWT refresh tokens and OIDC-provider refresh passthrough).

- Server mints a 32-byte base64url plaintext; stores only `HMAC-SHA256(jwtSecret, plaintext)` in `refresh_token.token_lookup_hash` (unique, indexed). This mirrors the ADR-0024 access-key HMAC pattern — one constant-time equality probe per refresh, no timing side channel.
- Every `/api/v1/auth/refresh` call revokes the presented row (`revoked_at = now()`, `revocation_reason = 'ROTATED'`) and issues a successor in the same `family_id`. Reuse of a revoked row triggers `revokeFamily(reason = 'REUSE_DETECTED')` — the canonical refresh-token-reuse detection.
- Expiry: default 7 days (`plugwerk.auth.refresh-token-validity-hours = 168`). Idle sessions expire; active sessions are effectively infinite because each refresh rolls the expiry forward.
- Reuses the existing `AccessKeyHmac` helper — HMAC key is `plugwerk.auth.jwt-secret`. Rotating the JWT secret invalidates every JWT, every access key (ADR-0024), and now every refresh token in one "auth reset" operation.

### 2. CSRF scope

**Re-enable Spring Security CSRF** using `CookieCsrfTokenRepository.withHttpOnlyFalse()` (double-submit), **scoped exclusively to `POST /api/v1/auth/refresh`**. Implementation:

```kotlin
http.csrf { csrf ->
    csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
    csrf.requireCsrfProtectionMatcher { request ->
        request.method == HttpMethod.POST.name() &&
            request.requestURI == "/api/v1/auth/refresh"
    }
}
```

- `/api/v1/auth/refresh` is the only endpoint reading an ambient credential (the httpOnly refresh cookie). Every other mutation authenticates via non-ambient `Authorization: Bearer …` or `X-Api-Key`, so CSRF on those endpoints adds overhead without a threat model.
- `SameSite=Strict` on the refresh cookie is defence in depth — double-submit holds even on Safari ITP fallbacks or pre-2019 clients that silently downgrade `SameSite`.
- Pinned by `CsrfScopeIT` which asserts both directions: refresh POST without `X-XSRF-TOKEN` returns 403; every other Bearer-authenticated POST is unaffected.

### 3. Access-token lifetime

**15 minutes** (`plugwerk.auth.access-token-validity-minutes = 15`). Dropped from 8 hours because the token now lives in React memory only — session continuity comes from the refresh cookie, so aggressive expiry caps blast radius if a token ever leaks (e.g. log capture, DevTools screenshare). 15 min matches Auth0 / Okta / AWS Cognito defaults.

Deprecation bridge for `plugwerk.auth.token-validity-hours`: the legacy property is retained as a no-op `@Deprecated` field for one release so existing deployments do not fail on startup. It is ignored at runtime. Remove in the next minor bump.

### 4. `downloadArtifact` same-origin + allow-list

Same-origin attaches the bearer; everything else requires an explicit entry in `plugwerk.auth.download-allowed-hosts` (exposed to the frontend via `GET /api/v1/config`). Default empty list = strict same-origin — correct for the dominant bundled-JAR deployment.

```text
decideDownload(url) →
  resolvedUrl = new URL(url, window.location.origin)
  attachBearer = resolvedUrl.origin === window.location.origin
              OR resolvedUrl.hostname ∈ allowedHosts (case-insensitive)
```

If `attachBearer === false` the fetch proceeds unauthenticated; `downloadArtifact` does **not** throw in this case because the target may legitimately be a public CDN. Pinned by `decideDownload` unit tests.

### 5. Regression guard

Vitest `authStore.noLocalStorage.test.ts` spies `Storage.prototype.setItem` and fails immediately if any call matches `/token|auth|jwt|bearer|credential|refresh|access/i` against its key. The only exempt keys are `pw-namespace` (UI preference) and `pw-migrated-v294` (sessionStorage migration flag). Stack trace points at the offending line.

## Architecture

### Backend

- New table `refresh_token(id, family_id, user_id, token_lookup_hash UNIQUE, issued_at, expires_at, revoked_at, revocation_reason, rotated_to_id)` — migration `0010_refresh_token.yaml`.
- New `RefreshTokenEntity` + `RefreshTokenRepository` + `RefreshTokenService` (issue / rotate / revokeFamily / revokeAllForUser / `@Scheduled` hourly cleanup).
- New `RefreshTokenCookieFactory` — builds httpOnly/Secure/SameSite=Strict/`Path=/api/v1/auth` cookies.
- New `RefreshRateLimitFilter` — 30 req / 60 s per IP.
- New `POST /api/v1/auth/refresh` on `AuthController` (not modelled in OpenAPI — follow-up). Login sets the cookie; refresh rotates it; logout clears it and force-revokes the family.
- `TokenRevocationService` / `JwtTokenService` now read `accessTokenValidityMinutes`.
- `SecurityConfiguration` re-wires CSRF as described above.

### Frontend

- `useAuthStore`: `accessToken`, `username`, `isSuperadmin`, `passwordChangeRequired` all memory-only. `isHydrating` flag blocks initial render until `/auth/refresh` resolves on mount. One-time `removeItem` migration of legacy `pw-access-token` / `pw-username` / `pw-password-change-required` / `pw-is-superadmin` keys under a sessionStorage guard.
- New `api/refresh.ts` — single-flight refresh promise, reads `XSRF-TOKEN` cookie into `X-XSRF-TOKEN` header, `credentials: "include"`.
- `api/config.ts` axios interceptor: request interceptor reads in-memory token; response interceptor on 401 invokes `refreshAccessToken()` once per request (guarded by `_retryAttempted` flag), retries on success, force-logs-out on failure.
- `components/auth/AuthHydrationBoundary.tsx` — renders `<CircularProgress>` until hydrate settles; mounted at app root (`App.tsx`).
- `utils/downloadArtifact.ts` — `decideDownload` gates bearer attachment.

## Consequences

### Good

- XSS-exfil of the access token no longer possible: nothing in `localStorage` to steal.
- Even if an attacker injects code that reads the in-memory token, the 15-minute ceiling limits the damage window — refresh uses the httpOnly cookie which JS cannot reach.
- `downloadArtifact` can no longer be weaponised to leak the bearer to a third-party host.
- Reuse-detection turns stolen refresh cookies into a detectable event (family revocation) rather than an undetected session takeover.
- CSRF re-enablement is auditable: one SpEL matcher, one filter chain, one `CsrfScopeIT` asserting the scope in both directions.

### Breaking (alpha window)

- **All existing logged-in users are logged out on upgrade.** Their stored `pw-access-token` is no longer read; no refresh cookie exists yet. First interaction redirects to `/login`. Mirrors #291 / #292 precedent; acceptable in 1.0.0-beta.1.
- `plugwerk.auth.token-validity-hours` becomes a no-op. Operators who tuned it need to set `plugwerk.auth.access-token-validity-minutes` (or rely on the 15-minute default).
- Deployments behind non-HTTPS reverse proxies with `cookieSecure = true` (default) will see the browser drop the cookie. Override to `cookieSecure = false` only for plain-HTTP local dev.

### Watch

- Reverse proxies that strip cookies on `Path=/api/v1/auth` need an allow-list. Not observed in-tree (bundled deployment has no proxy by default) but document in AGENTS.md if a user reports it.
- Browsers that ignore `SameSite=Strict` on the cookie fall back to double-submit CSRF — acceptable, monitor audit telemetry.
- Frontend bundle size: `refresh.ts` + `AuthHydrationBoundary.tsx` add ~2 KB gzipped. Negligible.
- If a future change introduces a second cookie-authenticated endpoint, expand the CSRF `requireCsrfProtectionMatcher` rather than opening CSRF globally.

## References

- Readable guide with sequence diagrams: [docs/concepts/authentication.md](../concepts/authentication.md).
- Issue [#294]; audit rows TS-001, TS-002, TS-003 in `docs/audits/1.0.0-beta.1-artifacts/triage-TS.csv`.
- Supersedes [ADR-0020](0020-csrf-stateless-decision.md).
- HMAC-lookup precedent: [ADR-0024](0024-access-key-hmac-lookup.md).
- CSP directive review: `connect-src 'self'` remains correct — refresh endpoint is same-origin. No CSP change shipped; new `downloadAllowedHosts` entries may require matching `connect-src` additions, documented per deployment.
- Touched files:
  - `plugwerk-server/plugwerk-server-backend/src/main/resources/db/changelog/migrations/0010_refresh_token.yaml`
  - `plugwerk-server/plugwerk-server-backend/src/main/kotlin/io/plugwerk/server/domain/RefreshTokenEntity.kt`
  - `plugwerk-server/plugwerk-server-backend/src/main/kotlin/io/plugwerk/server/repository/RefreshTokenRepository.kt`
  - `plugwerk-server/plugwerk-server-backend/src/main/kotlin/io/plugwerk/server/service/RefreshTokenService.kt`
  - `plugwerk-server/plugwerk-server-backend/src/main/kotlin/io/plugwerk/server/security/RefreshTokenCookieFactory.kt`
  - `plugwerk-server/plugwerk-server-backend/src/main/kotlin/io/plugwerk/server/security/RefreshRateLimitFilter.kt`
  - `plugwerk-server/plugwerk-server-backend/src/main/kotlin/io/plugwerk/server/controller/AuthController.kt`
  - `plugwerk-server/plugwerk-server-backend/src/main/kotlin/io/plugwerk/server/config/SecurityConfiguration.kt`
  - `plugwerk-server/plugwerk-server-backend/src/main/kotlin/io/plugwerk/server/PlugwerkProperties.kt`
  - `plugwerk-server/plugwerk-server-frontend/src/stores/authStore.ts`
  - `plugwerk-server/plugwerk-server-frontend/src/api/{config,refresh}.ts`
  - `plugwerk-server/plugwerk-server-frontend/src/utils/downloadArtifact.ts`
  - `plugwerk-server/plugwerk-server-frontend/src/components/auth/AuthHydrationBoundary.tsx`

[#255]: https://github.com/plugwerk/plugwerk/issues/255
[#291]: https://github.com/plugwerk/plugwerk/pull/311
[#292]: https://github.com/plugwerk/plugwerk/issues/292
[#294]: https://github.com/plugwerk/plugwerk/issues/294
[#315]: https://github.com/plugwerk/plugwerk/issues/315
