# ADR-0035: Storage / DB consistency check and orphan reaper

## Status

Accepted

## Context

Plugwerk stores plugin artifacts in two places that can drift apart:

1. **PostgreSQL** — `plugin_release` rows reference an `artifact_key` for each
   release.
2. **Object storage** — files on the filesystem or in S3, behind the
   `ArtifactStorageService` SPI (see ADR-0034).

Drift modes we have observed or expect:

- **Missing artifact** — a `plugin_release` row points to a storage key that
  is no longer present (file deleted manually, S3 lifecycle expired the
  object, failed restore from backup).
- **Orphaned artifact** — a storage object that has no corresponding
  `plugin_release` row (publish job crashed after upload but before commit,
  release deleted but storage delete failed, manual upload tooling).

Issues #190 (consistency check) and #496 (orphan reaper) cover both
directions. They are addressed jointly because they share the same diff
algorithm and the same race-window concerns.

### Horizontal scaling

Plugwerk runs as multiple instances against a single PostgreSQL database.
Any scheduled work (token cleanup, orphan reaper, …) needs to coordinate
between instances:

- Without coordination, every instance scans the bucket on every tick — N×
  cost on S3 and N× database churn for nothing.
- For the reaper specifically, two instances could each see the same
  orphan and race on `delete()`, racing against a third instance that just
  committed a publish for the same key.

We adopt **ShedLock** as a lightweight cluster-wide mutex for `@Scheduled`
methods. The lock table is created by Liquibase migration `0033_shedlock`,
the lock provider is wired in `SchedulerLockConfig`, and the four existing
scheduled jobs (refresh-token cleanup, token revocation cleanup,
password-reset sweep, email-verification sweep) are retrofitted with
`@SchedulerLock` so that only one instance does the work per tick.

ShedLock alone does **not** close the TOCTOU window between "list bucket"
and "delete object". An instance can finish a publish (`PUT` + INSERT)
between the moment the reaper enumerated the key and the moment it would
issue the `DELETE`. We mitigate this with two layers:

1. **Grace period** — orphan candidates with `lastModified` newer than
   `plugwerk.storage.reaper.grace-period` (default 24h) are skipped. This
   means the reaper cannot delete an artifact that was uploaded within the
   last day, which is far longer than the longest plausible publish
   transaction.
2. **DB recheck inside the delete transaction** — before each storage
   `delete()`, the admin remediation service queries
   `findAllArtifactKeys()` again and skips any candidate that has since
   gained a database row. This narrows the window to "between recheck and
   delete," which is sub-millisecond and acceptable.

## Decision

1. **Storage SPI gains `listObjects()`.** The existing `listKeys()` returns
   keys only; reconciliation needs `lastModified` and `sizeBytes` for the
   grace-period filter and for the admin UI. `listObjects()` is the
   primary method; `listKeys()` becomes a default `map { it.key }` wrapper
   for backward compatibility.

2. **Two-sided scan with a hash-set diff.**
   `StorageConsistencyService.scan()` collects all `artifactKey`s from the
   DB and all keys from storage and emits two lists: missing (DB → storage
   gap) and orphaned (storage → DB gap). The scan is paged through
   `plugwerk.storage.consistency.max-keys-per-scan` (default 100 000) and
   throws `StorageScanLimitExceededException` mapped to HTTP 409 when
   exceeded — admins are expected to use a targeted prefix in that case.

3. **Admin remediation via dedicated endpoints.**
   `AdminStorageConsistencyController` exposes
   `GET /api/v1/admin/storage/consistency` (report),
   `DELETE /api/v1/admin/storage/consistency/releases/{id}` (delete an
   orphaned DB row + its storage entry, idempotent), and
   `DELETE /api/v1/admin/storage/consistency/artifacts` (bulk-delete
   orphaned storage keys, returning `deleted` / `skipped` so freshly
   re-published keys are visible). Both delete paths recheck the DB inside
   the transaction before issuing the storage delete.

4. **Superadmin only.** All three endpoints are guarded by `@PreAuthorize`
   plus an inline `requireSuperadmin()` defense-in-depth call. Namespace
   admins are intentionally **not** allowed because the storage layer is
   global and the operation is cross-tenant.

5. **ShedLock retrofit, additive.** All four existing `@Scheduled` jobs
   get `@SchedulerLock(name = "<job>", lockAtMostFor = "PT15M")`. A
   `@ConditionalOnProperty(plugwerk.scheduler.shedlock.enabled,
   matchIfMissing = true)` gate, paired with a `NoOpLockProvider` fallback,
   keeps `@SpringBootTest` slices using H2 working without running the
   shedlock migration. Production / Docker / Compose run on PostgreSQL and
   pick up the real `JdbcTemplateLockProvider`.

6. **Reaper as a separate, opt-in scheduled job (PR2).** The orphan reaper
   (#496) lands in a follow-up PR with `@Scheduled` + `@SchedulerLock`,
   `dry-run` mode by default, a configurable grace period, a Micrometer
   counter, and a cluster integration test using two embedded Plugwerk
   contexts against one PostgreSQL.

## Consequences

**Easier:**

- Operators can detect drift before users hit "404 on download" or
  unbounded bucket growth.
- The admin UI surfaces both sides of drift in one view.
- Adding new scheduled jobs is now a one-line `@SchedulerLock` addition;
  the wiring already exists.
- Filesystem and S3 backends behave the same because they implement the
  same SPI method (`listObjects`).

**Harder / trade-offs:**

- The grace period delays reaper deletions by 24h by default; this is a
  conscious correctness-over-eagerness trade.
- A 10M-key bucket cannot be scanned in one call. The 409-with-limit
  exception nudges admins toward targeted prefix scans (planned in PR2).
- The shedlock table must exist in production PostgreSQL. Liquibase
  migration `0033_shedlock` creates it on the next deploy; rollback is a
  plain `DROP TABLE shedlock`.
- ShedLock uses JVM time, not DB time, because `usingDbTime()` is not
  H2-compatible. This is acceptable for NTP-synced hosts; clock-skew >
  `lockAtLeastFor` could let two instances run a job in quick succession,
  but never concurrently within the lock window.

## References

- Issue #190 — Plugin storage consistency check
- Issue #496 — Orphan storage artifact reaper
- ADR-0034 — S3 storage backend
- Spring Modulith `@SchedulerLock` docs
