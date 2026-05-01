# ADR-0029: OIDC web-UI login via Spring Security `oauth2Login` (token-exchange pattern)

## Status

Accepted — closes [#315](https://github.com/plugwerk/plugwerk/issues/315). Documents retrospectively the design choices made across PRs #350 (#79 Phase 1), #354 (#352), and #355 (#351).

## Context

[ADR-0027](0027-refresh-cookie-and-csrf-reenabled.md) moved the self-issued JWT off `localStorage` into a memory-only access token plus an httpOnly refresh cookie. **OIDC web-UI login was explicitly out of scope** because Plugwerk only ran resource-server-mode OIDC at the time (validating Bearer tokens issued by external IdPs; no code-exchange in the SPA).

ADR-0027 pinned the future direction:

> When a web-UI OIDC login lands in Phase 2 (tracking #315), the **token-exchange** shape is already decided: the backend will perform the OIDC code exchange, map to a local `UserEntity`, and issue a Plugwerk-native access + refresh pair — identical to the flow below from step 2 onwards.

[#315](https://github.com/plugwerk/plugwerk/issues/315) tracked the implementation. Three parallel concerns surfaced during the work:

1. **The PKCE `code_verifier` and OAuth2 `state` cannot live in `localStorage`** (same XSS argument as ADR-0027) — need a server-side stash.
2. **Identity model.** The first-cut implementation in PR #350 wrote the synthetic string `"<provider-uuid>:<sub>"` into `plugwerk_user.username` to avoid building the proper FK model up-front. That hack leaked into UI labels and audit logs and was clearly tech debt — tracked as [#351](https://github.com/plugwerk/plugwerk/issues/351) and reworked in PR #355.
3. **Logout.** The Plugwerk-side logout left the IdP session active, so the next "Login with Keycloak" silently re-authenticated the same user — bad UX. Tracked as [#352](https://github.com/plugwerk/plugwerk/issues/352) and addressed in PR #354.

This ADR captures the joint resolution across all three PRs.

## Decisions

### 1. Token exchange via Spring Security `oauth2Login`

**Use Spring Security's built-in `oauth2Login` configurer** rather than self-implementing the `/api/v1/auth/oidc/{provider}/{authorize|callback}` endpoints originally sketched in #315.

- Spring exposes the equivalent of those endpoints out-of-the-box at the canonical Spring-Security paths:
  - **Authorization start**: `GET /oauth2/authorization/{registrationId}` — generates PKCE `code_verifier` + `state`, stashes them in the HttpSession, redirects the browser to the IdP's authorize endpoint.
  - **Callback**: `GET /login/oauth2/code/{registrationId}` — receives `code` + `state`, validates `state`, exchanges the code for tokens, validates the ID-token (signature, issuer, audience, exp, nonce), populates an `OAuth2AuthenticationToken` with an `OidcUser` principal.
- Spring's implementation gets PKCE-S256, state validation, code exchange, ID-token JWT validation, JWKS rotation, and replay protection for free. Re-implementing those in `/api/v1/auth/oidc/...` would duplicate ~500 lines of high-criticality crypto/auth code with zero functional gain.
- The Plugwerk-side glue lives in two narrow components after Spring hands us the authenticated principal:
  - **`OidcLoginSuccessHandler`** (`AuthenticationSuccessHandler`) — bridges the Spring `OAuth2AuthenticationToken` into Plugwerk's session (mints JWT, sets refresh cookie, redirects to `/`). Identical exit point to local login from step 2 onwards, exactly as ADR-0027 prescribed.
  - **`OidcIdentityService.upsertOnLogin(provider, sub, claims)`** — resolves or creates the Plugwerk `UserEntity` for the OIDC subject (see decision 3).
- The `ClientRegistrationRepository` is **DB-backed** (`DbClientRegistrationRepository`) — registrations are derived from rows in `oidc_provider` (admin-managed via the existing OIDC-provider admin UI) instead of static `application.yml` configuration. The `registrationId` matches `oidc_provider.id` (UUID) via `OidcRegistrationIds.of(...)`, so the same identifier flows from the admin row to the URL paths to the DB FK.

### 2. PKCE `code_verifier` + `state` storage: HttpSession

**`HttpSessionOAuth2AuthorizationRequestRepository`** (Spring default). The full `OAuth2AuthorizationRequest` — including `code_verifier`, `state`, `nonce`, and `redirect_uri` — sits in the Tomcat-managed HTTP session keyed by `state`.

- The session cookie is httpOnly + SameSite + (in prod) Secure — same protection profile the refresh cookie gets in ADR-0027.
- **`SessionCreationPolicy.IF_REQUIRED`** is non-negotiable for OAuth2: stateless mode would lose the `code_verifier` between the authorize-start request and the callback request, and the IdP would happily redirect with a `code` we cannot exchange. (Symptom pre-fix in PR #350: a Spring whitelabel error page on `/login/oauth2/code/{id}`.)
- The session is consumed and discarded on the callback — there is no long-lived OAuth2 server-side state to manage.
- HMAC-signed cookies were considered as an alternative but rejected: same security properties, more code, and Spring's Default works.

### 3. Identity model: `oidc_identity` as 1:1 mapping, no linking

**Schema** (migration 0017, PR #355):

```
plugwerk_user                  -- canonical Plugwerk principal (the "Hub")
  id, display_name, email, source ENUM('LOCAL','OIDC'), username, password_hash, …

oidc_identity                  -- one row per (provider, sub), strictly 1:1 to user
  id, oidc_provider_id, subject, user_id, created_at, last_login_at
  UNIQUE (oidc_provider_id, subject)
  UNIQUE (user_id)             -- enforces the no-linking policy at schema level
```

- **Each OIDC `(provider, sub)` pair maps to exactly one `plugwerk_user`**. Same human across two providers ⇒ two unrelated `plugwerk_user` rows. `UNIQUE(user_id)` is the schema-level guarantee. If the policy ever flips (admin-merge UI, etc.), dropping that constraint is a one-line migration.
- Authorization is **PK-based**: `namespace_member.user_id`, `refresh_token.user_id`, JWT `sub` all reference `plugwerk_user.id` — never the IdP's `sub` claim. `CurrentUserResolver` is the single source of truth that parses JWT-`sub` into a `plugwerk_user.id` UUID.
- The synthetic `"<provider-uuid>:<sub>"` string in `plugwerk_user.username` from PR #350 is gone (PR #355). For OIDC users `username = NULL` and `password_hash = NULL`, enforced by `chk_plugwerk_user_credentials`.

### 4. Auto-provisioning on first OIDC contact

`OidcIdentityService.upsertOnLogin` materialises a Plugwerk identity on the first successful callback for an unknown `(provider, sub)`. No admin pre-registration is required — the original #315 sketch listed pre-registration as the "safer Phase-2 starting point" but the no-linking-by-policy decision makes auto-provisioning the natural default.

- `display_name` resolution precedence: `name` claim → `preferred_username` claim → `subject` (last-resort visible identifier).
- `email` is **mandatory** — see decision 5.
- The new `plugwerk_user` row defaults to `enabled=true`, `is_superadmin=false`, no namespace memberships. The user lands on the welcome page with a hint to ask an administrator for namespace access. (Welcome page conditionally hides the "Create Namespace" button for non-superadmins.)

### 5. `email` claim is mandatory; missing email → 400

`plugwerk_user.email` is `NOT NULL` after migration 0017. An OIDC callback whose ID token carries no `email` claim is rejected with HTTP 400 and an operator-actionable message: *"OIDC provider '<name>' returned no `email` claim — configure the IdP to include `email` in the requested scope (default scope is 'openid email profile')."*

- `OidcIdentityService.createNewIdentityAndUser` raises `OidcEmailMissingException` which `OidcLoginSuccessHandler` translates to `response.sendError(400, …)`.
- This is a setup-time misconfiguration, not a runtime user-fixable condition. The operator-facing browser whitelabel is acceptable.
- **Email uniqueness** is partial: the unique index `uq_plugwerk_user_email_internal` only constrains `WHERE source = 'INTERNAL'`. EXTERNAL accounts may share emails — both with each other and with INTERNAL accounts — because the no-linking policy makes a duplicate-email check a foot-gun (would block legitimate logins).

#### 5a. Email-policy for OAuth2-only providers (#357)

After #357 added GitHub, Google, Facebook and a generic OAuth2 type, the email-resolution path is no longer "read `email` claim from the ID token" for all providers. The three OAuth2-shaped surfaces resolve email differently:

| Provider | Source of `email` | Failure mode |
|---|---|---|
| Google | OIDC discovery + ID token (same as Keycloak/Authentik) | Operator forgot the `email` scope |
| GitHub | `/user` user-info, **fallback** to `/user/emails` for private primaries | User has neither a public email nor granted the `user:email` scope |
| Facebook | `/me?fields=email` | Facebook App Review has not approved the `email` permission for the operator's app |
| Generic OAuth2 | operator-configured user-info attribute name (default `email`) | The configured attribute is absent from the user-info response |

The product question Plugwerk answered (the issue's α/β/γ choice):

- **Decision**: **Option α** — *reject the login with a provider-aware, actionable message.*
- **Implemented in**: PR #406 (#357 phase 2). `OidcEmailMissingException.buildMessage` switches on `provider.providerType` and produces a hint that points at the specific failure mode for that provider — so a GitHub user sees *"Set a public primary email in your GitHub account settings (Settings → Emails → uncheck 'Keep my email addresses private') or sign in with a different provider"* rather than the generic OIDC message.

Why option α and not β (onboarding page) or γ (synthetic placeholder):

- **β (`/onboarding/email` page)** is the right long-term UX but requires email-verification infrastructure (verification token table, mailer, click-to-verify endpoint) we do not have yet. Tracked in the original #357 scope as *"would be a good place to land it"* but kept deferred — the cost was disproportionate for an edge case that mostly resolves itself once the user adds a public email upstream.
- **γ (synthetic `<id>@github.no-email.local`)** silently violates the `email` semantics (downstream code assumes the column is a real address — notifications, audit, etc.). It would have unblocked the flow at the cost of poisoning the data; rejected because it spreads the workaround across the entire system instead of localising it at the failure boundary.

The provider-aware message keeps the failure mode honest: the operator (or in GitHub's case, the user) sees exactly what to fix, and `plugwerk_user.email` retains its real-address invariant.

### 6. JWT `sub` = `plugwerk_user.id` (UUID), never the upstream subject

The Plugwerk-issued access token's `sub` claim is the `plugwerk_user.id` UUID-string for both LOCAL and OIDC users. Authorization, audit logs, refresh-token rows, namespace_member FKs all key off this single canonical identifier.

- Pre-#351 the `sub` was the `plugwerk_user.username` (or, for OIDC, the synthetic `"<provider-uuid>:<sub>"`). That format leaked the upstream subject into every authorization decision and made cross-provider identity policy impossible.
- Migration 0017 force-revokes all active refresh tokens (`revocation_reason = 'SCHEMA_MIGRATION_0017'`) so users re-authenticate against the new `sub` format. Pre-1.0.0-beta.1 → no production deployments → acceptable.

### 7. RP-Initiated Logout

A Plugwerk logout on an OIDC-sourced session terminates the upstream IdP session via [OpenID Connect RP-Initiated Logout 1.0](https://openid.net/specs/openid-connect-rpinitiated-1_0.html). Tracked in #352, implemented in PR #354.

- Backend stores the upstream `id_token` on `refresh_token.upstream_id_token` at OIDC-login time (migration 0016) — gives `/api/v1/auth/logout` an `id_token_hint` to send to the IdP.
- `POST /api/v1/auth/logout` returns `200 + LogoutResponse{endSessionUrl}` for OIDC sessions and `204 No Content` for local sessions.
- Frontend (`useAuthStore.logout`) navigates `window.location.assign(endSessionUrl)` for the OIDC case, falls through to local routing for the 204 case.
- `post_logout_redirect_uri` is rewritten frontend-side to `${window.location.origin}/login` so that the bounce always lands on the SPA the user actually uses (handles dev `localhost:5173` vs. backend `:8080` cleanly, plus prod vanity domains).
- Providers without an `end_session_endpoint` (vanilla OAuth2 surfaces) get a graceful fallback: backend returns 204, no IdP-side cleanup possible, document the limitation.

### 8. Provider-Delete-Politik C

Deleting an `oidc_provider` row cascades through `oidc_identity` (FK `ON DELETE CASCADE`) but **the orphaned `plugwerk_user` rows survive with `enabled=false`** — set by application code in `OidcProviderService.delete` *before* the SQL cascade fires.

- The audit trail (namespace memberships, refresh-token history, download events) survives the provider deletion.
- Disabled users cannot log in; an admin can review them and explicitly hard-delete via the user-admin UI later.
- Alternative considered: cascade up to `plugwerk_user` (rejected — destroys audit history); refuse delete while users exist (rejected — operationally annoying for legitimate provider rotations).

## Endpoint surface

| URL | Purpose | Owner |
|---|---|---|
| `GET /oauth2/authorization/{registrationId}` | Start OAuth2 / OIDC code flow | Spring Security `OAuth2AuthorizationRequestRedirectFilter` |
| `GET /login/oauth2/code/{registrationId}` | Code-exchange callback | Spring Security `OAuth2LoginAuthenticationFilter` |
| `POST /api/v1/auth/refresh` | Exchange refresh cookie for new access token (LOCAL + OIDC after first login) | `AuthController.refresh` |
| `POST /api/v1/auth/logout` | Revoke session; for OIDC returns `endSessionUrl` for RP-Initiated Logout | `AuthController.logout` |
| `GET /api/v1/config` (already public) | Returns `auth.oidcProviders[]` so the SPA can render `Login with <Provider>` buttons | `ConfigController` |

The `registrationId` is the OIDC-provider UUID (`oidc_provider.id.toString()`) — same identifier in the URL path, the DB FK, the `ClientRegistration` registry, and the `Set-Cookie` issuance side.

## Consequences

### Positive

- Smallest possible Plugwerk surface area on the OAuth2 critical path. ~50 lines of glue (`OidcLoginSuccessHandler`) plus ~80 lines of `DbClientRegistrationRepository` build the entire integration on top of Spring Security's mature implementation.
- `OidcIdentityService` is a clean, testable boundary — first-login, returning-login, and missing-email paths are all unit-testable without mocking Spring Security internals.
- Identity model (`oidc_identity` + UNIQUE(user_id)) cleanly forbids the linking policy at the schema level — silent regressions impossible.
- After the OIDC callback the session is byte-for-byte identical to a local-login session: same JWT, same refresh cookie, same downstream code paths.

### Negative

- **URL convention mismatch with the rest of `/api/v1/...`**: the OAuth2 endpoints live under `/oauth2/...` and `/login/oauth2/...` (Spring's choice), not `/api/v1/auth/oidc/...` as the original #315 sketch proposed. Operators reading the audit log will see two URL families. Documented in this ADR; not fixable without re-implementing what Spring already provides.
- **HttpSession state** is now unavoidable on OIDC-login paths. ADR-0027's stateless principle stays true for everything *after* the OIDC callback (the access token + refresh cookie flow is stateless), but the OAuth2 dance itself requires a session to hold the PKCE verifier. Acceptable trade-off — the session is short-lived, owns no business state, and exists only between the authorize-start and callback requests.
- **OIDC users cannot share identities across providers** by design. A user who registers via Keycloak and later via GitHub gets two unrelated Plugwerk accounts. Documented as policy in this ADR and in PR #355's body.

### Follow-up issues kept open

- **#353** — full provider edit UI/REST (admin can change `name`, `clientId`, `clientSecret`, `issuerUri`, `scope` on existing providers).
- Account-linking flows (admin-merge UI for two `plugwerk_user` rows that turn out to be the same human) — no issue yet; create on demand.
- SAML support — separate feature track.
- Backchannel/Frontchannel logout — separate issue when the multi-RP deployment story matures.

## Alternatives considered

### A. Self-implemented `/api/v1/auth/oidc/{providerId}/{authorize,callback}` (the #315 original sketch)

Rejected because:
- Duplicates ~500 lines of crypto/auth code from Spring Security with no functional benefit.
- Every PKCE/state/JWS-validation defect Spring will fix in their next release would be ours to track separately.
- Plugwerk gains nothing operational (admins do not interact with these URLs directly; the SPA's "Login with <Provider>" button is a `<a href>` regardless of the URL shape).

### B. PKCE `code_verifier` in HMAC-signed cookie

Workable, eliminates the HttpSession requirement. Rejected because:
- More code (cookie serialization, HMAC roundtrip, replay-window management).
- Same security properties as HttpSession given that the session cookie is also httpOnly + SameSite + (in prod) Secure.
- Cookie size bloat (the verifier is 43+ bytes encoded; bigger after HMAC + base64) on every authorize-start request.

### C. OIDC ID token as the Plugwerk session token (no token exchange)

Considered for completeness; rejected immediately. Would force every Plugwerk service-side check to re-validate JWS against the IdP's JWKS, would not give Plugwerk control over session lifetime, would not allow refresh-cookie rotation, would not allow logout-side revocation. ADR-0027's whole architecture would break.

### D. Pre-registration of OIDC subjects (admin must create the `plugwerk_user` row first)

Considered as the "safer Phase-2 starting point" in the #315 sketch. Rejected because:
- Operationally hostile for environments where the IdP is the source of truth (typical Keycloak/Auth0 deployment). Every new hire would need a manual click-through.
- The no-linking-by-policy decision (#3 above) makes auto-provisioning safe — there is no "merge with existing user" risk to defend against.
- Disabled-by-default flag on the user row is sufficient defence-in-depth if an operator wants gated onboarding (set in `OidcIdentityService.createNewIdentityAndUser`, not yet wired to a property).

## References

- ADR-0022 — encryption key for `oidc_provider.client_secret_encrypted`
- ADR-0024 — HMAC-keyed access keys (sibling pattern for refresh tokens)
- ADR-0027 — refresh cookie + memory-only access token (this ADR's parent)
- Issues: #79 (Phase 1 OIDC), #294 (refresh cookie), #315 (this work), #351 (identity-hub split), #352 (RP-initiated logout)
- PRs: #349 (local Keycloak dev setup), #350 (Phase 1 OAuth2 browser flow), #354 (RP-initiated logout), #355 (identity-hub refactor)
- Specs: [OpenID Connect Core 1.0](https://openid.net/specs/openid-connect-core-1_0.html), [RFC 7636 PKCE](https://datatracker.ietf.org/doc/html/rfc7636), [OIDC RP-Initiated Logout 1.0](https://openid.net/specs/openid-connect-rpinitiated-1_0.html)
