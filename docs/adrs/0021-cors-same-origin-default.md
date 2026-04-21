# ADR-0021: CORS same-origin default with opt-in allow-list

## Status

Accepted

## Context

The 1.0.0-beta.1 security audit (tracker [#255]) flagged that `SecurityConfiguration.securityFilterChain` did not call `.cors(...)` explicitly (finding SBS-002 / M-01 / [#263]). Spring Boot's implicit default — no CORS handling — happened to produce the right behaviour for Plugwerk today, but:

- The intent was undocumented in code. A reviewer verifying "is CORS safely off?" had to trace the full filter chain and every controller.
- A future `@CrossOrigin` annotation on a controller would silently open a per-endpoint origin with no centralised review point.
- A future frontend deployed on a separate origin (CDN, subdomain) would require a Spring config change rather than a runtime property.

### Why CORS is genuinely not required today

- **Frontend bundled in the server JAR.** The Vite build output is served from the same Spring Boot application that exposes the REST API. Browser same-origin policy already permits all calls because request and response share scheme + host + port.
- **PF4J client plugins are server-to-server.** They make HTTP calls from arbitrary JVM hosts using a `Namespace-Access-Key` or JWT Bearer. Browsers are not in the loop; CORS does not apply.
- **OIDC callback is a full-page browser navigation.** The provider redirects the browser back to Plugwerk's own origin. No cross-origin fetch.

The security gap was purely about making the same-origin intent explicit and providing a single review point for future changes.

## Decision

Introduce an explicit `CorsConfigurationSource` bean in `SecurityConfiguration` backed by `PlugwerkProperties.ServerProperties.CorsProperties`, and wire it into the filter chain with `.cors { it.configurationSource(...) }`.

### Default behaviour

```yaml
plugwerk:
  server:
    cors:
      allowed-origins: []   # empty => same-origin-only
      allowed-methods: [GET, POST, PUT, PATCH, DELETE, OPTIONS]
      allowed-headers: [Authorization, Content-Type, X-Api-Key]
      allow-credentials: true
      max-age: 3600
```

Empty `allowed-origins` preserves today's behaviour bit-for-bit: a browser request that carries a cross-origin `Origin` header receives no `Access-Control-Allow-Origin` response header, and the browser enforces same-origin policy on the client side.

### Opt-in cross-origin support

Operators who deploy the frontend on a separate origin add the exact origins via the `PLUGWERK_SERVER_CORS_ALLOWED_ORIGINS` env var (comma-separated). No code change is needed.

### Startup validators

- `@AssertTrue` on `CorsProperties.isWildcardCredentialsCombinationValid` — rejects the classic `allowed-origins: ["*"]` + `allow-credentials: true` anti-pattern at startup. Spring Security rejects this combination at runtime in any case; failing fast with a readable message at startup is a strictly better operator experience.
- `@Min(0) @Max(86400)` on `max-age` — bounds the preflight cache window at 24 hours (most browsers ignore longer values anyway).

### No `@CrossOrigin` on controllers

Controllers must not use `@CrossOrigin`. Cross-origin policy lives only in `SecurityConfiguration.corsConfigurationSource()` so there is exactly one review point. The filter-chain comment block in `SecurityConfiguration` and this ADR are the canonical references.

### Alternatives considered

- **Option A — explicit deny-all `CorsConfigurationSource` with no property hook.** Rejected: any future cross-origin deployment becomes a code change plus a new ADR. The audit finding is categorically about making the intent explicit, not about forbidding future flexibility.
- **`allowedOriginPatterns` with subdomain wildcards.** Out of scope for #263. Can be added later without breaking the contract — the current `allowedOrigins` list maps 1:1 to Spring's exact-match semantics, and `allowedOriginPatterns` would be a separate property.
- **Wildcard origins without credentials.** Technically safe per the spec but materially different from the current deployment model; operators who actually need `*` should write a conscious ADR supplementing or superseding this one.

## Consequences

### Positive

- The same-origin intent is self-documenting at code read time. A reviewer sees `.cors { it.configurationSource(corsConfigurationSource()) }` and the comment block referencing this ADR instead of inferring intent from the absence of a call.
- Future cross-origin deployments are a config change, reviewable as a runtime operator decision rather than a source patch.
- The wildcard-plus-credentials footgun fails fast at startup with a clear error naming the env var.
- `@CrossOrigin`-annotation drift is forbidden by policy; if someone adds one anyway, it combines with the central config predictably (Spring merges the two) — a future code review is the enforcement.

### Negative

- One extra indirection in `SecurityConfiguration` (the `@Bean` method plus the `.cors { ... }` call). Small readability cost vs. large signal value.
- Operators who set `allowed-origins: "*"` with `allow-credentials: true` now see a hard startup failure. This is by design — the previous Spring behaviour was also a hard runtime rejection on the first preflight, just later and less clearly attributed.

### Revisit conditions

Re-open the decision if:

- Plugwerk ships a deployment model where the frontend runs on a different origin than the backend by default (CDN-first, multi-tenant subdomains).
- A non-browser cross-origin client (an embeddable widget, iframe integration) needs CORS — current PF4J clients bypass CORS entirely and will not trigger this.
- Subdomain-wildcard support becomes a requirement. At that point migrate from `allowedOrigins` to `allowedOriginPatterns` and add explicit test coverage for the pattern semantics.
- A refresh-cookie-based auth flow is introduced via [#294] / H-08 (JWT-in-localStorage hardening). Cookie-based auth materially changes the CSRF surface (see ADR-0020) and the CORS `allow-credentials` interaction; re-audit both together.

[#255]: https://github.com/plugwerk/plugwerk/issues/255
[#263]: https://github.com/plugwerk/plugwerk/issues/263
[#294]: https://github.com/plugwerk/plugwerk/issues/294
