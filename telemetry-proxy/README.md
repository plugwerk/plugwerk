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
| `502`  | PostHog forwarding failed (non-2xx, network error, or timeout)             |

Unknown fields are **rejected, not silently stripped**. Request bodies and field
values are **never logged** (defense-in-depth, even though the payload is PII-free).

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
| `src/index.ts`        | Worker entry: routing, method/content-type/size guards |
| `src/validate.ts`     | Pure zero-PII allowlist validator                      |
| `src/posthog.ts`      | PostHog forwarding (secret read from env, never logged) |
| `src/constants.ts`    | Allowlist, enums, limits, endpoints                    |
| `test/*.test.ts`      | Validator + handler unit tests                         |
| `wrangler.toml`       | Worker config + route (no secrets)                     |
| `smoke-test.sh`       | curl smoke test (204 valid / 400 extra-field)          |
