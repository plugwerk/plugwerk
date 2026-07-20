# Plugwerk Privacy Policy

_Last updated: 2026-06-13_

Plugwerk is self-hosted, open-source software. **You run your own instance**, so
for everything that happens inside it — accounts, namespaces, plugins, uploads —
**you are the data controller** and this policy does not govern that data. How you
handle your end users' data is your responsibility.

There is exactly **one** thing the Plugwerk software sends back to its maintainers,
**devtank42 GmbH**: an opt-out **telemetry beacon**. This policy describes that
beacon and nothing else.

## What the telemetry beacon collects

The beacon sends **exactly four fields, and there is no code path that adds a
fifth** (enforced by construction — see
[ADR-0039](docs/adrs/0039-telemetry-beacon.md)):

| Field         | Example            | What it is                                                                                                   |
| ------------- | ------------------ | ------------------------------------------------------------------------------------------------------------ |
| `installId`   | `7c9e6f…` (UUID v4) | A random identifier generated **once** per installation and stored locally. Synthetic — not derived from anything and unlinkable to a person. |
| `version`     | `1.1.0-SNAPSHOT`   | The Plugwerk version the instance is running.                                                                |
| `installType` | `docker-compose`   | How it is deployed: `docker-compose`, `jar`, `k8s`, or `unknown`.                                            |
| `event`       | `server_start`     | The lifecycle event: `server_start` or `heartbeat` (and, for later activation funnel events, `namespace_created` / `plugin_published`). |

**No personal data is in the payload.** The beacon payload is exactly the four
fields above — no hostnames, IP addresses, namespaces, usernames, plugin names,
account data, or request contents. (As with any HTTPS request, the instance's IP
address is visible as transient connection metadata to our reverse-proxy
provider; it is never part of the telemetry payload, is not forwarded to PostHog,
and is not used for analytics.) The events carry **no person profiles** — the
analytics processor is configured event-only (`$process_person_profile: false`).

## Why we collect it (purpose & legal basis)

We use this pseudonymous signal solely to **size the install base, understand
which versions and deployment types are in use, and prioritise engineering work**.
It is product/activation analytics, not advertising or profiling.

Insofar as the synthetic `installId` is treated as personal data at all, the legal
basis is our **legitimate interest** (Art. 6(1)(f) GDPR) in understanding adoption
of our own software, balanced against a strictly pseudonymous, PII-free payload and
a one-line opt-out (below).

## Who processes it (processor & data residency)

- **Controller:** devtank42 GmbH (Plugwerk maintainers).
- **Processor:** **PostHog**, used on **PostHog EU Cloud** — telemetry is stored and
  processed **within the EU** (`eu.i.posthog.com`). A **Data Processing Agreement
  (DPA)** is in place with PostHog before any production data is ingested (see the
  DPA-acceptance runbook in
  [`telemetry-proxy/README.md`](telemetry-proxy/README.md#dpa-acceptance-do-this-first)).
- **Sub-processor (transport):** **Cloudflare**, which operates the telemetry
  reverse proxy (a Cloudflare Worker on the `workers.dev` platform domain)
  under its standard DPA. It processes the
  connection (including source IP) transiently to route the request; the IP is not
  forwarded to PostHog.
- **Transport:** beacons go to a Plugwerk-operated HTTPS reverse proxy (a
  Cloudflare Worker) that validates the payload against the four-field
  allowlist and forwards it to PostHog. Request bodies are never logged.

## Retention

Telemetry is retained only as long as it is useful for the analytics purpose above
and is subject to PostHog EU Cloud's storage. Because the payload contains no PII,
there is no personal data to erase; resetting an instance's `installId` (Admin →
Settings, or a fresh install) detaches all future beacons from any prior series.

## How to turn it off

Telemetry is **opt-out**. Disabling it stops everything — no install ID is
generated, no heartbeat is scheduled, and no HTTP call is ever made:

```bash
PLUGWERK_TELEMETRY=false
```

See the [Telemetry & Privacy](README.md#telemetry--privacy) section of the README
for the full list of related environment variables.

## Your rights & contact

For questions about this beacon, the DPA, or to exercise data-subject rights
regarding any data we hold as controller, contact **devtank42 GmbH** at
`privacy@plugwerk.io`.

## Changes

We will update this document if the telemetry payload, purpose, processor, or
residency ever changes. Material changes are reflected in the "Last updated" date
above and the project changelog.
