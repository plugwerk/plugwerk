# ADR-0038: Opt-out telemetry beacon

## Status

Accepted

(Security/privacy review — [DEV-25](/DEV/issues/DEV-25) — passed with no CRITICAL/HIGH
findings: zero-PII payload allowlist, random-UUID install id, complete opt-out, HTTPS-only
configurable endpoint, and fail-open all verified. Flipped Proposed → Accepted on sign-off.)

## Context

Plugwerk is a self-hosted, open-source plugin marketplace. To size the activation
funnel and prioritise work, the team needs a basic, privacy-respecting signal of
how many installations exist, what versions are deployed, and how they are run
(Docker Compose / JAR / Kubernetes). There is currently no signal at all once a
build leaves our hands.

The Growth spec ([DEV-15](/DEV/issues/DEV-15)) and the GTM-readiness note
([DEV-12](/DEV/issues/DEV-12) §G) mandate **opt-out** (not opt-in) telemetry,
because opt-in collection on self-hosted infrastructure yields a participation
rate too low to be useful. Opt-out shifts the burden onto us to make the
collection unimpeachable: it must be impossible for a beacon to leak anything
identifying, trivial to turn off, well-documented, and incapable of affecting the
running server.

This is the P0 child ([DEV-23](/DEV/issues/DEV-23)) of the telemetry epic
([DEV-22](/DEV/issues/DEV-22)).

## Decision

Ship an **opt-out** telemetry beacon in the server with four guardrails that make
opt-out defensible:

1. **Strict zero-PII payload (allowlist by construction).** The beacon sends
   exactly four fields and there is nowhere in the code to attach a fifth:

   ```json
   { "installId": "<uuid-v4>", "version": "1.1.0-SNAPSHOT", "installType": "docker-compose", "event": "server_start" }
   ```

   - `installId` — a random UUID v4 generated once per installation and persisted
     locally via the existing DB-backed `ApplicationSettingsService`
     (`telemetry.install_id`). It is synthetic and unlinkable to any real-world
     identity.
   - `version` — the running Plugwerk version (from build info / `VERSION`).
   - `installType` — `docker-compose` | `jar` | `k8s` | `unknown`. Tells us *how*
     the software is deployed, never *where*.
   - `event` — `server_start` | `heartbeat`.

   No hostnames, IPs, namespaces, usernames, plugin names, or request data are
   collected. The payload is a Kotlin `data class` with exactly these fields, and
   a unit test asserts the serialized JSON has exactly these keys, so a future
   change cannot silently widen it.

2. **One clean opt-out.** `PLUGWERK_TELEMETRY=false` (relaxed-bound to
   `plugwerk.telemetry.enabled`) fully disables the feature: no install UUID is
   generated, no heartbeat is scheduled (the scheduler bean is
   `@ConditionalOnProperty`-gated), and no HTTP call is ever made. Default is
   enabled.

3. **Loud documentation.** The root `README.md` carries a "Telemetry & Privacy"
   section stating precisely what is sent, why, and how to disable it.

4. **Fail-open, silent.** Telemetry never runs on the request path; the first-start
   beacon is dispatched off the startup thread. Every send is wrapped so any
   exception (timeout, DNS, 5xx, serialization) is swallowed and logged at debug,
   with short (2s) connect/read timeouts. Telemetry can never affect startup,
   health, or readiness.

**Endpoint ownership.** The beacon POSTs to a single, configurable,
**Plugwerk-owned, HTTPS-only** endpoint (`PLUGWERK_TELEMETRY_ENDPOINT`). Until
Growth provisions the live URL (sibling endpoint-coordination child), the
endpoint defaults to empty; a blank or non-HTTPS endpoint means the payload is
built but never sent — a harmless no-op under the fail-open design. HTTPS-only is
enforced at **send time** (skip + debug-log), deliberately **not** as startup
bean validation, so a misconfigured endpoint can never crash the server.

**Build vs. reuse.** No new persistence or scheduling primitives were introduced:
- install UUID persistence reuses `ApplicationSettingsService` /
  `ApplicationSettingKey` (ADR-0016);
- the daily heartbeat reuses the `@Scheduled` + ShedLock + scheduler-control-plane
  pattern (ADR-0036), so operators can pause it from the admin dashboard and only
  one instance emits per tick in a cluster;
- the HTTP call reuses Spring's `RestClient` already on the classpath.

## Consequences

- **Easier:** the team gets a minimal, honest install/version/deployment signal
  without compromising the project's privacy posture; operators who object turn it
  off with a single environment variable and can verify the payload from the
  README.
- **Easier:** because nothing new was built for persistence or scheduling, the
  surface area to review is small and the failure modes are already understood.
- **Harder / accepted trade-offs:**
  - Opt-out means some operators will send a beacon before reading the docs. The
    zero-PII guarantee is what makes this acceptable; the payload is reviewed by
    Security ([DEV-25](/DEV/issues/DEV-25)) before this ADR is Accepted.
  - The `telemetry.install_id` setting is surfaced in the Admin → Settings list
    (a side effect of reusing `ApplicationSettingsService`). Changing it only
    resets the anonymous install identity; it carries no PII and no security
    impact.
  - A brand-new multi-instance cluster could briefly generate two install IDs on
    first start (a benign race); analytics reconciles to one quickly and the
    fail-open caller never surfaces the race.
- **Follow-ups:** the live endpoint URL is provisioned by Growth out of band; P1
  activation events (namespace-created, first-plugin-publish —
  [DEV-24](/DEV/issues/DEV-24)) build on this beacon's transport and payload
  conventions. QA ([DEV-26](/DEV/issues/DEV-26)) verifies opt-out + fail-open.
