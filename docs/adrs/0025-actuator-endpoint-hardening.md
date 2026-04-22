# ADR-0025: Actuator endpoint hardening (`/info`, `/prometheus`)

## Status

Accepted

## Context

The 1.0.0-beta.1 security audit (tracker [#255]) flagged finding SBS-004 / [#292]: the Spring Security chain required only `authenticated()` for `/actuator/info` and `/actuator/prometheus`. Any authenticated principal — including namespace members with the minimum `READ_ONLY` role, and any caller holding a valid `X-Api-Key` — could read both endpoints.

These endpoints are not benign:

- `/actuator/info` exposes build version and git metadata, which accelerates vulnerability matching against the deployed artifact.
- `/actuator/prometheus` exposes JVM internals, Hibernate statement counters, HikariCP pool sizing, `http_server_requests_seconds_*` cardinality (which leaks endpoint inventory and rough traffic patterns), and counters that correlate with namespace membership and access-key usage over time.

`/actuator/health` is deliberately out of scope: it is used by the in-repo docker-compose health check, by external uptime probes, and by container orchestrators that cannot be expected to authenticate. Its existing `permitAll()` carve-out is preserved.

### Options considered

1. **Superadmin-only via SpEL** — gate `/info` and `/prometheus` behind `@namespaceAuthorizationService.isCurrentUserSuperadmin()`. Zero new properties, zero new beans, zero operator action on upgrade. Breaks unattended scraping: a Prometheus scraper would need a superadmin JWT, which has an 8-hour lifetime (`plugwerk.auth.token-validity-hours`). Operators without a way to refresh a service-account JWT would be pushed toward disabling the restriction entirely.

2. **Dedicated scrape account (HTTP Basic) + superadmin fallback, opt-in** — a separate `SecurityFilterChain` matches `/actuator/**`; when `plugwerk.auth.actuator.scrape-username` and `scrape-password` are set, HTTP Basic with an in-memory single-user store is enabled; the authorization rule admits either `hasAuthority('ACTUATOR_SCRAPE')` or `isCurrentUserSuperadmin()`. When the env vars are unset the basic-auth mechanism is not installed and superadmin JWT is the only access path. The contract is strictly stronger than option 1: operators who configure the scrape account get option 1's posture plus a service account; operators who don't get option 1 verbatim.

3. **Both access mechanisms mandatory and layered** — always enable both httpBasic and oauth2ResourceServer on the actuator chain with a combined expression. Strictly a superset of option 2; adds configuration surface (and corner cases in the Spring test harness) with no observable benefit over option 2.

## Decision

Adopt **option 2**. Remediation targets the root cause (authorization on the endpoints) and adds a deployment-ergonomic path for unattended scraping without forcing it on operators who do not need it.

### Security chain

Two `SecurityFilterChain` beans are registered:

- `actuatorSecurityFilterChain` — `@Order(1)`, `securityMatcher("/actuator/**")`.
  - `.requestMatchers("/actuator/health").permitAll()` — unchanged.
  - `.requestMatchers("/actuator/info", "/actuator/prometheus").access(...)` — admit either `ACTUATOR_SCRAPE` authority (granted only to the scrape account) or a superadmin authentication.
  - `.anyRequest().denyAll()` — closed by default. New `/actuator/*` endpoints introduced in future work cannot silently leak.
  - `oauth2ResourceServer` is installed so superadmin Bearer tokens are decoded here; `httpBasic` is only installed when the scrape account is configured.
  - Namespace-scoped filters (`NamespaceAccessKeyAuthFilter`, `PublicNamespaceFilter`, the rate-limit filters, `PasswordChangeRequiredFilter`) are intentionally **not** wired into this chain — actuator endpoints are host-level and do not carry namespace context.

- `securityFilterChain` — `@Order(2)`, the existing API chain, unchanged except that the `/actuator/health` matcher is removed (ownership moved to the actuator chain).

### Scrape account

The scrape account is opt-in. When `plugwerk.auth.actuator.scrape-username` **and** `plugwerk.auth.actuator.scrape-password` are both non-blank:

- An `InMemoryUserDetailsManager` is registered as `actuatorScrapeUserDetailsService` containing exactly one user carrying the `ACTUATOR_SCRAPE` authority.
- The plaintext password is BCrypt-encoded once at bean creation via the existing `PasswordEncoder` bean. The plaintext lives in the bound `ActuatorProperties` data class, whose `toString()` is overridden to redact.
- The scrape user is only referenced by the actuator chain. The API chain has no username/password login surface, so the scrape credentials cannot be used against any other endpoint.
- A cross-field `@AssertTrue` on `ActuatorProperties` rejects half-configured states at startup (username set without password, or vice versa), rather than failing silently at login time.

Password minimum length is 16 characters; 32+ is recommended. Rotation is "change the env var, restart the server" — nothing persistent.

### Separation from JWT secret

Unlike ADR-0024 (where the access-key HMAC deliberately shares the JWT secret because both grant the same capability), the scrape account uses an operator-controlled password independent of `PLUGWERK_AUTH_JWT_SECRET`. The scrape account cannot mint JWTs, cannot read namespaces, and cannot authenticate against the API chain — so there is no reason to couple its rotation cadence to JWT rotation.

## Consequences

### Good

- Audit finding SBS-004 closed: namespace members and access-key principals receive `403` on `/info` and `/prometheus`.
- `/actuator/health` is unaffected — existing healthchecks and uptime probes continue to work.
- Default posture after upgrade is "strictly locked down" — operators do not need to do anything for the fix to apply.
- Unattended Prometheus scraping has a first-class path (HTTP Basic with a dedicated service account), which is the idiomatic shape expected by `ServiceMonitor.spec.basicAuth` and Prometheus' `basic_auth` config block.
- `anyRequest().denyAll()` on the actuator chain means any future `/actuator/*` endpoint that gets exposed is locked by default; a developer who wants to expose a new actuator endpoint must write an explicit matcher, which is a reviewable event.

### Breaking (alpha window)

- Any deployment that was scraping `/actuator/prometheus` with a namespace-member JWT stops working on upgrade. The migration path is documented in `AGENTS.md` and `.env.example`: set `PLUGWERK_AUTH_ACTUATOR_SCRAPE_USERNAME` + `PLUGWERK_AUTH_ACTUATOR_SCRAPE_PASSWORD`, and update the scraper's `basic_auth` config. Acceptable in the 1.0.0-beta.1 window because no GA deployment exists yet.
- Access-key principals lose access to `/actuator/info` and `/actuator/prometheus`. `NamespaceAuthorizationService.isSuperadmin` already returns `false` for any principal whose `name` starts with `key:`, so the new SpEL rule rejects them even if they somehow reach the actuator chain. This is the intended behaviour — access keys are namespace-scoped service credentials and should not read host-level metrics.

### Watch

- If an operator configures a scrape account with a weak password (the `@Size(min = 16)` validator is the only gate), brute-force against HTTP Basic is cheaper than against BCrypt-verified login. Mitigated by the high entropy recommendation (`openssl rand -base64 32`) in the env-var documentation and by the fact that actuator endpoints are typically placed behind a reverse proxy on an internal network in production deployments. Revisit if the deployment model shifts to public internet exposure without a proxy — a follow-up could add an IP allowlist property.
- If a future PR introduces a new actuator endpoint and wires it into `management.endpoints.web.exposure.include`, the default-deny posture means it returns `403` until an explicit matcher is added. The failure mode is loud, not silent.

## References

- Issue [#292] — audit row SBS-004.
- Audit tracker [#255] and `docs/audits/1.0.0-beta.1-artifacts/triage-SBS.csv` row `SBS-004`.
- Precedent for split filter chains and paired ADR: ADR-0024 (access-key HMAC lookup) / [#291].
- Touched files:
  - `plugwerk-server/plugwerk-server-backend/src/main/kotlin/io/plugwerk/server/config/SecurityConfiguration.kt`
  - `plugwerk-server/plugwerk-server-backend/src/main/kotlin/io/plugwerk/server/PlugwerkProperties.kt`
  - `plugwerk-server/plugwerk-server-backend/src/main/resources/application.yml`
  - `plugwerk-server/plugwerk-server-backend/src/test/kotlin/io/plugwerk/server/config/ActuatorSecurityWithScrapeAccountIT.kt`
  - `plugwerk-server/plugwerk-server-backend/src/test/kotlin/io/plugwerk/server/config/ActuatorSecurityWithoutScrapeAccountIT.kt`
  - `.env.example`
  - `AGENTS.md`

[#255]: https://github.com/plugwerk/plugwerk/issues/255
[#291]: https://github.com/plugwerk/plugwerk/pull/311
[#292]: https://github.com/plugwerk/plugwerk/issues/292
