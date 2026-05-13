# ADR-0037: Branding-asset storage location

## Status

Accepted

## Context

Issue #254 introduces three operator-uploadable branding slots
(`logo_light`, `logo_dark`, `logomark`) per Plugwerk instance. The
files are small (max 512 KB each, 1.5 MB total) but referenced from
authentication-free surfaces (login page, OG metadata) and need
ETag/Cache-Control on the read path.

The issue listed three storage options and recommended the existing
`storage/` directory (Option B, "fits the layout we already have for
plugin artefacts"). Between the issue being filed and this ADR being
written, two new pieces of infrastructure landed that change the
trade-off:

- **#190 (Storage Consistency)** — `StorageConsistencyService.scan()`
  enumerates all keys in `ArtifactStorageService` and compares them
  against `plugin_release.artifact_key`. Anything without a matching
  release row is reported as an orphan in the admin UI.
- **#496 (Orphan Reaper)** — a scheduled job deletes those orphans
  after a 24 h grace period.

Both treat any object reachable through the FS / S3 backend as
"a plugin artefact that should have a database row." Branding assets,
which by design have no `plugin_release` row, would be classified as
orphans on the next nightly tick and silently deleted.

## Decision

Branding bytes live in PostgreSQL, in a dedicated
`application_asset` table with a `bytea` column. **Option A** from
the issue.

Schema (Liquibase `0035_application_asset`):

| Column        | Type           | Notes                                  |
|---------------|----------------|----------------------------------------|
| `id`          | uuid PK        |                                        |
| `slot`        | varchar(32) UQ | `logo_light` / `logo_dark` / `logomark`|
| `content_type`| varchar(64)    | `image/svg+xml` / `image/png` / `webp` |
| `content`     | bytea          | the asset bytes                        |
| `sha256`      | varchar(64)    | hex digest, used as the HTTP ETag      |
| `size_bytes`  | bigint         |                                        |
| `uploaded_at` | timestamptz    |                                        |
| `uploaded_by` | uuid FK        | superadmin who performed the upload    |

Re-uploading a slot replaces the existing row in the same transaction.

## Why not the existing `ArtifactStorageService`

1. **Reaper collision** — the orphan reaper would delete every
   branding asset on its next tick because it has no
   `plugin_release` row. Mitigation would mean teaching the reaper,
   the consistency scan, the bulk-delete endpoints, and the admin
   UI about a `branding/` prefix — a four-file kollateraländerung
   for a feature that does not need the storage abstraction at all.
2. **Backup story** — operators back up the database religiously
   but routinely forget the `storage/` volume. Putting the assets in
   the database means a `pg_dump` is sufficient for full-state
   restore.
3. **Stateless replicas** — no shared volume needed; every replica
   reads the same DB row.
4. **Public read path** — branding is fetched anonymously from the
   login page and email metadata; the `ArtifactStorageService` read
   path is wired into the authenticated download endpoint with all
   its filters. A separate public controller is the cleaner shape.
5. **Conceptual fit** — branding is configuration with binary
   payload, not user content. It belongs alongside
   `application_setting` rows, not alongside megabytes of plugin
   JARs.

## Why not Option C (separate S3 bucket)

A separate S3 bucket would mean an extra infrastructure dependency
for 1.5 MB of files, with no benefit. The public read path already
gets `Cache-Control: immutable` because the bytes are content-
addressed (sha256 in the URL), so a CDN in front of the application
stays equally effective.

## Public read endpoint

`GET /api/v1/branding/{slot}` is registered in `SecurityConfiguration`
with `permitAll`. It returns:

- `200` with the bytes, `ETag: "<sha256>"`, and
  `Cache-Control: public, max-age=31536000, immutable` when the slot
  has a custom asset
- `404` when the slot is at its default — the frontend's
  `useBranding()` hook then renders the bundled SVG it shipped with

The frontend pins URLs with `?v=<sha256>` so a re-upload invalidates
the immutable cache immediately.

## Consequences

**Easier:**

- Branding survives backup-restore as part of the standard DB-only
  workflow. Operators do not have to remember a second volume.
- Reaper / consistency scan stay narrowly focused on plugin
  artefacts; no prefix-aware special cases.
- New branding slots in the future (e.g. an OG image override) are
  one new row and one frontend tile.

**Harder / trade-offs:**

- Branding bytes ride in every `pg_dump`; for the 1.5 MB envelope
  this is irrelevant, for a hypothetical multi-megabyte hero asset
  it would not be. If a future use case needs large media we revisit
  this with a dedicated SPI rather than retrofitting the artefact
  storage.
- Sanitising SVG safely is a hand-rolled DOM walker because the
  project does not pull in jsoup; the `SvgSanitizer` test suite
  carries OWASP-style XSS vectors to keep it honest.

## References

- Issue #254 — Custom logo upload (Corporate Identity)
- ADR-0035 — Storage / DB consistency check + orphan reaper (the
  reason Option B is unsafe)
- ADR-0034 — S3 storage backend (the artefact-storage SPI)
