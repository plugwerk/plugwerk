# Plugwerk Telemetry Reverse Proxy

A ~40-line Cloudflare Worker that backs `POST https://telemetry.plugwerk.io/v1/events`.
It validates an opt-out telemetry beacon against a **strict zero-PII allowlist**, then
forwards accepted events to PostHog EU Cloud. It is the receiving half of the beacon
shipped in DEV-23; the architecture is recorded in DEV-27 / DEV-28.

> **Scope:** this directory is **build only**. Live deploy, DNS, and the production
> smoke test are handled by the deploy sibling (DEV-34) and depend on infra
> provisioning (DEV-31). A security review (DEV-33) gates go-live.

## Contract

`POST /v1/events`, `Content-Type: application/json`, body ≤ 2 KB.

Accepted fields — **exactly these four, nothing else**:

| field         | rule                                                                |
| ------------- | ------------------------------------------------------------------- |
| `installId`   | required, string, UUID v4                                           |
| `version`     | required, string, semver-ish (e.g. `1.1.0-SNAPSHOT`), ≤ 32 chars    |
| `installType` | required, enum: `docker-compose` \| `jar` \| `k8s` \| `unknown`     |
| `event`       | enum: `server_start` \| `heartbeat` \| `namespace_created` \| `plugin_published` |

### Status codes

| Status | When                                                                        |
| ------ | --------------------------------------------------------------------------- |
| `204`  | Valid payload forwarded to PostHog (2xx upstream)                           |
| `400`  | Missing/invalid field, wrong enum, malformed UUID, **unknown field**, or body > 2 KB |
| `405`  | Non-POST method (includes `Allow: POST`)                                    |
| `415`  | Wrong `Content-Type`                                                        |
| `429`  | Per-IP rate limit exceeded (includes `Retry-After`); no PostHog forward     |
| `502`  | PostHog forwarding failed (non-2xx, network error, timeout, **or a misconfigured non-`phc_` key**) |
| `503`  | Kill switch active (`PROXY_DISABLED = "true"`); nothing metered, read, or forwarded |

Unknown fields are **rejected, not silently stripped**. Request bodies and field
values are **never logged** (defense-in-depth, even though the payload is PII-free).

### Rate limiting (two-layer defense)

The endpoint is public and unauthenticated, so it must not become an amplifier into
per-event-billed PostHog (OWASP API4:2023, DEV-33 HIGH gate). Two layers throttle it,
both keyed by client IP at **60 requests / 60 s**:

1. **In-code Worker binding** (`wrangler.toml` → `[[ratelimits]]` `RATE_LIMITER`):
   `src/index.ts` meters every real ingestion attempt _after_ the path/method/
   content-type guards and returns `429` before reading the body or forwarding.
   **Caveat:** the Workers rate-limit binding counts **per Cloudflare colo**, not
   globally — it is best-effort, not an authoritative global cap.
2. **Cloudflare zone rate-limiting rule** (authoritative, global; provisioned in the
   DEV-34 deploy runbook below). It blocks at the edge before the Worker spins up.

Both are required: layer 1 travels with the code, layer 2 is the global enforcement.

The `502`/fail-open split is deliberate: the client beacon fails open on its side
(DEV-23), so a `5xx` here never affects the Plugwerk server — it only makes our own
delivery failures observable.

### Forwarded shape (to `https://eu.i.posthog.com/capture/`)

```json
{
  "api_key": "<POSTHOG_PROJECT_KEY>",
  "event": "<event>",
  "distinct_id": "<installId>",
  "properties": {
    "version": "<version>",
    "installType": "<installType>",
    "$process_person_profile": false
  }
}
```

`distinct_id = installId` groups Growth funnels by install; `$process_person_profile: false`
keeps events person-profile-free.

## Local development

```bash
npm install
npm run typecheck        # tsc --noEmit
npm test                 # vitest (validator + handler unit tests)

cp .dev.vars.example .dev.vars   # add a throwaway PostHog key (gitignored)
npm run dev              # wrangler dev on http://127.0.0.1:8787
./smoke-test.sh          # 204 for a valid sample, 400 for an extra-field sample
```

`.dev.vars` holds local secrets and is gitignored. For a fully offline check, point
`POSTHOG_CAPTURE_URL` (in `.dev.vars`) at a local mock so nothing is forwarded; the
`400` extra-field path needs no upstream at all.

## Deploy runbook (deploy sibling — DEV-34)

Prerequisite: the `plugwerk.io` zone is on Cloudflare and `telemetry.plugwerk.io`
resolves (see **DNS** below). PostHog project + key provisioned in DEV-31.

### DPA acceptance (do this first)

GDPR requires a signed Data Processing Agreement with PostHog **before** any
production telemetry is ingested. PostHog offers self-serve DPA acceptance — no
legal back-and-forth — so do this the moment the PostHog account exists, ahead of
secret setup:

1. Sign in to **PostHog EU Cloud** (`https://eu.posthog.com`) with the project owner
   account.
2. Open **Organization settings → Legal & compliance** (Settings → _Legal &
   compliance_; on some plans it appears under _Billing → Legal documents_).
3. Under **Data Processing Agreement**, fill in the company legal entity
   (**devtank42 GmbH**) and **Accept / Sign** the self-serve DPA.
4. **Download the countersigned PDF** and file it with the company's legal records
   (Paperclip document / drive). Note the acceptance date in the DEV-34 deploy issue
   so go-live has an auditable record.
5. Confirm the project's **data region is EU** (Organization settings → the region
   badge reads _EU_, host `eu.i.posthog.com`). If a US project was created by
   mistake, recreate it in the EU region — region cannot be migrated in place.

> **Production residency:** the capture host **must** remain an EU PostHog host
> (`eu.i.posthog.com`). The `POSTHOG_CAPTURE_URL` binding (`src/posthog.ts`) is a
> non-secret override intended for **local/mock use only** — never point it at a
> non-EU host in production, or telemetry would leave the EU and break the DPA
> residency guarantee above.

Only after the DPA is accepted and the EU region is confirmed should the project API
key be captured and injected as a secret (below). The key is captured from
**Project settings → Project API key** (`phc_…`) and is **read once into the secret
prompt — never pasted into a file, commit, chat, or ticket**.

### Secret setup

```bash
# One-time per environment. Value is encrypted at rest; never committed or echoed.
wrangler secret put POSTHOG_PROJECT_KEY
# paste the PostHog project API key when prompted
```

The key **must** be the write-only **`phc_…` project (ingestion) key** — never a
personal (`phx_…`) or admin key — so a leak's blast radius stays capture-only
(DEV-54, condition 2). This is **enforced in code** (`src/posthog.ts`): the Worker
fails closed (`502`, no forward) on any key without the `phc_` prefix, so a
misconfigured secret is caught at smoke-test time, not in production. The key value
is still never logged — only the fact that the prefix check failed.

> The same rule applies to a Paperclip-managed secret: store the key only in the
> encrypted secret store, reference it by name, and never echo its value into a
> comment, log, or document.

### Deploy

```bash
npm run deploy           # wrangler deploy
```

`wrangler.toml` binds the route `telemetry.plugwerk.io/v1/*` on `zone_name = "plugwerk.io"`.

### DNS pointing

Add a proxied DNS record for `telemetry` on the `plugwerk.io` zone (orange-cloud
ON so the Worker route intercepts):

- `telemetry` → `AAAA 100::` (or any placeholder) **proxied**, **or** a `CNAME`
  to a proxied hostname. The record only needs to exist and be proxied; the Worker
  route handles `/v1/*`. TLS is automatic via Cloudflare's edge cert for the zone.

Verify after deploy:

```bash
./smoke-test.sh https://telemetry.plugwerk.io   # expect 204 then 400
```

### Zone rate-limiting rule (REQUIRED — authoritative edge control)

The in-code Worker binding (`[[ratelimits]]` in `wrangler.toml`) only throttles
**per Cloudflare colo**, not globally. Go-live therefore also requires an
authoritative **zone rate-limiting rule** that blocks at the edge before the Worker
runs. This is the DEV-33 HIGH gate (DEV-47); it must exist before opening the route
to production traffic.

Configure on the `plugwerk.io` zone (Security → WAF → Rate limiting rules), or via
API on the `http_ratelimit` phase ruleset:

- **Match expression:**
  `http.request.method eq "POST" and http.host eq "telemetry.plugwerk.io" and starts_with(http.request.uri.path, "/v1/")`
- **Counting characteristic:** client IP (`ip.src`).
- **Threshold:** **60 requests / 60 s** per IP.
- **Action:** **block** for 60 s. **Do NOT use a managed challenge** — the client is
  a JVM beacon (DEV-23), not a browser, and cannot solve challenges.

Rationale for the number: a single install emits only a handful of events/hour
(DEV-23 cadence), so 60/min/IP is very generous for legit traffic (including NAT'd
corporate egress) while clamping floods hard. Tighten later if observability shows
headroom.

Equivalent API call (Rulesets engine, `http_ratelimit` phase entrypoint) — for a
reproducible, auditable apply instead of hand-clicking the dashboard:

```bash
curl -X PUT \
  "https://api.cloudflare.com/client/v4/zones/${CF_ZONE_ID}/rulesets/phases/http_ratelimit/entrypoint" \
  -H "Authorization: Bearer ${CF_API_TOKEN}" -H "Content-Type: application/json" \
  --data '{
    "rules": [{
      "description": "Plugwerk telemetry per-IP rate limit (DEV-47/DEV-54)",
      "expression": "http.request.method eq \"POST\" and http.host eq \"telemetry.plugwerk.io\" and starts_with(http.request.uri.path, \"/v1/\")",
      "action": "block",
      "ratelimit": {
        "characteristics": ["ip.src", "cf.colo.id"],
        "period": 60,
        "requests_per_period": 60,
        "mitigation_timeout": 60
      }
    }]
  }'
```

> `cf.colo.id` is required in `characteristics` on non-Enterprise plans (counting is
> per data center); Enterprise Advanced Rate Limiting can drop it for true global
> counting. Either way this zone rule blocks at the edge before the Worker runs.

> The matching threshold/period is mirrored in the in-code binding so both layers
> agree. If you change one, change the other (`simple` in `wrangler.toml` and
> `RATE_LIMIT_PERIOD_SECONDS` in `src/constants.ts`).

### Go-live security verification (DEV-54)

Before opening the route to production traffic, run the read-only verifier to
confirm all three go-live security conditions against the **live** config and
capture the output as the DEV-54 sign-off evidence:

```bash
CF_API_TOKEN=<zone-scoped token> CF_ZONE_ID=<plugwerk.io zone id> \
  ./go-live-check.sh https://telemetry.plugwerk.io
```

It asserts: (1) the zone rate-limit rule above is present (block, `ip.src`,
60/60s, host + `/v1/`); (2) the `POSTHOG_PROJECT_KEY` secret is set (its `phc_`
prefix is enforced in code, proven by the smoke `204`); (3) no Logpush job on the
zone captures request bodies or headers. It then runs the live smoke test
(`204`/`400`). It makes no changes.

### Emergency stop (kill switch)

Fastest way to stop ingestion without touching code or routes: set the
`PROXY_DISABLED` variable to `"true"` — either in the Cloudflare dashboard
(Workers → plugwerk-telemetry-proxy → Settings → Variables, takes effect in
seconds, no deploy) or via `wrangler.toml` `[vars]` plus `wrangler deploy`.
While active, every request to `/v1/events` is refused with `503` before any
rate-limiter metering, body read, or PostHog forward. The client beacon fails
open (DEV-23), so no Plugwerk installation is affected. Unset (or set to
anything other than `"true"`) to resume.

### Rollback

Instant — remove the route (no redeploy of the Plugwerk server needed):

```bash
# Option A: delete the route in the Cloudflare dashboard (Workers Routes), or
# Option B: remove the `routes` entry from wrangler.toml and redeploy, or
wrangler delete           # tears down the Worker entirely
```

Because the beacon fails open, removing the route is harmless to clients — events
simply stop being collected.

## Rotation

To rotate the PostHog key, re-run `wrangler secret put POSTHOG_PROJECT_KEY` with the
new value and `npm run deploy`. No code change required.

After deploying the new secret, **regenerate/rotate the project API key in PostHog
→ Project settings** so the previous value is invalidated. Re-running
`wrangler secret put` alone leaves the old key valid: a leaked previous `phc_…`
ingestion key would still let anyone POST events to PostHog past the proxy
allowlist until it is regenerated.

## Files

| Path                  | Purpose                                                |
| --------------------- | ------------------------------------------------------ |
| `src/index.ts`        | Worker entry: routing, method/content-type/size guards, per-IP rate limit |
| `src/validate.ts`     | Pure zero-PII allowlist validator                      |
| `src/posthog.ts`      | PostHog forwarding (secret read from env, never logged) |
| `src/ratelimit.ts`    | Per-IP rate-limit binding type + metering helper       |
| `src/constants.ts`    | Allowlist, enums, limits, endpoints                    |
| `test/*.test.ts`      | Validator + handler unit tests (incl. rate-limit regression) |
| `wrangler.toml`       | Worker config + route + rate-limit binding (no secrets) |
| `smoke-test.sh`       | curl smoke test (204 valid / 400 extra-field)          |
| `go-live-check.sh`    | read-only go-live security verifier (DEV-54: zone rule, secret, Logpush) |
