# ADR-0020: CSRF protection is disabled — stateless JWT + no cookie auth

## Status

Accepted

## Context

The 1.0.0-beta.1 security audit (tracker [#255]) flagged `SecurityConfiguration.securityFilterChain` calling `.csrf { it.disable() }` without an inline comment explaining the rationale (finding H-11 / SBS-001). The disable call is correct for the current architecture, but a future reviewer or a future refactor cannot verify "is CSRF safely off?" by reading a single line — they have to trace the entire filter chain and all authentication surfaces to confirm no endpoint relies on ambient cookies.

This ADR records why CSRF is safe to disable today and the conditions under which that decision must be revisited.

## Decision

Disable CSRF protection (`http.csrf { it.disable() }`) for the REST API filter chain.

### Why this is safe today

1. **Stateless session policy.** `SessionCreationPolicy.STATELESS` is enforced by the same filter chain. Spring Security does not create or consult an `HttpSession` for any request. There is no server-side session state for a CSRF token to protect.

2. **Bearer credentials only.** The two authentication mechanisms in use both require the attacker to *attach* the credential to the request deliberately:
   - Local/OIDC JWT: sent as `Authorization: Bearer <token>`. A cross-origin `<img>`, `<form>`, or `fetch` cannot set an arbitrary header on a cross-origin request without a preflight — the browser same-origin policy is the guardrail, and our CORS config (locked-down; see [#263] / M-01) does not allow arbitrary origins.
   - Namespace access keys: sent as `X-Api-Key`. Same browser-header restriction applies; access keys are machine-to-machine credentials that are not persisted in cookies.

3. **No cookie-based authentication.** No request in the codebase reads an ambient cookie to establish identity. The frontend stores the access token in `localStorage` today (tracked as a separate concern in [#294] / H-08) — not in a cookie. The server never issues or accepts an auth cookie.

4. **OIDC callback is CSRF-resistant.** The OIDC state machine (`OidcProviderRegistry` + callback handler) uses the standard `state` and `nonce` parameters for cross-site request forgery protection at the OAuth layer. Neither parameter is stored in an HTTP session or cookie; both are bound to the in-flight authorization request.

### Under what conditions must this be revisited

This decision is conditional. It must be re-opened if **any** of the following becomes true:

- **An authentication path moves to a cookie.** The most realistic trigger is [#294] / H-08 (JWT-in-localStorage hardening), which proposes moving the access token into a `httpOnly` / `Secure` / `SameSite=Strict` cookie for XSS-exfiltration resistance. `SameSite=Strict` mitigates most CSRF on modern browsers — but not all (older browsers, timing-based downgrade attacks, subdomain attacks). If that PR lands, CSRF must be re-enabled for all state-changing endpoints.
- **Any server-side session is introduced.** Switching `SessionCreationPolicy` away from `STATELESS`, introducing a session-backed feature (e.g. server-rendered login with `Remember-Me`), or adding any `HttpSession` use requires re-enabling CSRF.
- **An endpoint reads an auth-relevant cookie.** Any `@CookieValue` or `request.cookies` access that influences authorization is a CSRF surface.
- **A non-Bearer auth mechanism is added** that the browser attaches automatically (e.g. HTTP Basic auth persisted in a browser credential store).

## Consequences

### Positive

- The inline comment in `SecurityConfiguration` (added together with this ADR under [#261]) makes the current CSRF contract self-documenting: a reviewer can confirm the decision without tracing the filter chain.
- The revisit conditions above make the trigger explicit — future changes that cross one of those lines know they must re-enable CSRF.
- Disabling CSRF removes one source of spurious 403 responses during stateless API testing and avoids the need for frontend code to request and echo CSRF tokens.

### Negative

- The decision is load-bearing on the "no cookie auth" invariant. If that invariant is violated silently (e.g. a third-party Spring component introducing a cookie we did not anticipate), CSRF would be effectively off for an unintended surface. Mitigation: the inline comment names the specific conditions so any PR that breaks them is visible in review; ADR-0019's `pull_request_target` reviewer discipline applies here too.
- `SameSite=Strict` cookies are *not* a full replacement for CSRF tokens. If [#294] lands and moves the token into a cookie, we do not rely on `SameSite` alone — we re-enable CSRF with Spring Security's built-in token repository and update this ADR.

[#255]: https://github.com/plugwerk/plugwerk/issues/255
[#261]: https://github.com/plugwerk/plugwerk/issues/261
[#263]: https://github.com/plugwerk/plugwerk/issues/263
[#294]: https://github.com/plugwerk/plugwerk/issues/294
