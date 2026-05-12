# ADR-0036: Scheduler control-plane (admin dashboard)

## Status

Accepted

## Context

Plugwerk ships five `@Scheduled` jobs (refresh-token cleanup, token-
revocation cleanup, password-reset sweep, email-verification sweep,
orphan-storage reaper). Their cron patterns and tuning constants live
in `application.yml`; their behaviour at runtime was previously
opaque to operators:

- no in-product way to see whether a job is enabled, what cron drives
  it, when it last ran, or whether the last run succeeded
- no way to disable a misbehaving job without a redeploy
- no way to flip the orphan reaper between dry-run and live without a
  redeploy
- no way to trigger an off-schedule "run now" for debugging or post-
  fix recovery

A redeploy is heavy for what is usually a one-line operational tweak.
Issue #516 laid out a hybrid solution.

## Decision

### Hybrid model — `application.yml` stays authoritative, the dashboard adds runtime hebel

We **do not** move cron patterns or tuning constants into the database:

- `application.yml` is in Git; DB-only config breaks the "git checkout
  ⇒ working state" property
- two sources of truth (annotation/yaml vs DB) for the same job is a
  long-term maintenance burden
- editing a cron via a web form is a foot-gun (`* * * * * *` would
  hammer the scheduler thread)
- in practice operators almost never want to change cron patterns,
  but routinely want enable/disable + visibility

The dashboard adds **only** the small set of toggles operators
actually need at runtime: `enabled`, `dryRun` (for jobs that support
it), and a "run now" trigger. Plus the runtime state every operator
asks about: last run timestamp, outcome, duration, total run count.

### Backend shape

- New table `scheduler_job` (Liquibase migration `0034_scheduler_job`)
  carries `name` (matches the `@SchedulerLock` lock name), `enabled`,
  `dry_run` (nullable — null = honour yaml default), and the
  `last_run_*` + `run_count_total` book-keeping fields.
- `SchedulerJobRegistry` is a singleton bean. Each scheduled service
  calls `register(SchedulerJobDescriptor)` from its `@PostConstruct`,
  publishing `name`, `description`, `cronExpression`,
  `supportsDryRun`, and a `runNowExecutor` reference.
- `SchedulerJobBootstrap` listens for `ApplicationReadyEvent` and
  seeds the table with rows for any registered jobs that do not yet
  have one. Idempotent: an `ON CONFLICT`-equivalent `findAll`-then-
  `INSERT` pattern means a peer instance booting in parallel cannot
  cause duplicates.
- `SchedulerJobService` is the read path. The toggle values are read
  on every scheduler tick, so we cache them with a Caffeine `expire-
  After-Write(30s)` to keep the gate cheap. Mutations from the admin
  endpoints `invalidate(name)` so the next tick on the same instance
  picks up the change immediately; peer instances see it after at
  most 30 seconds.
- Each scheduled method's body is now wrapped in
  `schedulerJobAuditor.gateAndRun(name) { … }`. The auditor's
  `gateAndRun` checks the toggle, returns `SKIPPED_DISABLED` if off,
  otherwise wraps the block in a `run` that captures duration and
  outcome (uncaught exceptions become `FAILED`). Audit writes happen
  in a `REQUIRES_NEW` transaction so an audit-side failure cannot
  rollback the actual job, and a job rollback cannot also undo the
  "this just failed" record.
- Run-now reuses the same Bean-method reference the cron triggers,
  going through the same `@SchedulerLock` advice. A concurrent
  regular tick simply blocks the manual run via the lock — desired
  behaviour, no separate lock-name suffix needed.

### Frontend shape

A superadmin-only `/admin/scheduler` page polls the list endpoint
every 15 s. Stats strip (active jobs, disabled, failed-last-run,
total runs) above a custom table with one row per job:

- monospace `name` + caption-sized description
- read-only cron chip (yaml-authoritative — the UI cannot edit it)
- enabled toggle
- 3-state dry-run cycle for jobs with `supportsDryRun = true`
  (`null → true → false → null`, where `null` means "honour yaml")
- last-run badge coloured by outcome (success = green, failed = red,
  skipped = neutral, aborted = warning)
- run-now button gated by `enabled`, with a confirm dialog

## Consequences

**Easier:**

- operators can react to a misbehaving job in seconds (toggle off,
  confirm with run-now after fix, toggle back on)
- the dashboard is the single visible answer to "is the cluster
  doing what it should be doing"
- adding a new scheduled job is a one-line `register(...)` call from
  the owning service — the bootstrap, gate, audit, and dashboard pick
  it up without controller or schema changes

**Harder / trade-offs:**

- enable/disable is now slightly stale (up to 30 s) on peer instances
  because we cache the read. Acceptable for hourly/daily jobs;
  unacceptable would mean LISTEN/NOTIFY plumbing for a feature where
  seconds of latency cost nothing.
- the bootstrap creates rows on first boot per cluster. A migration
  that pre-seeds the rows would have to know the registry contents
  at SQL-write time, which it does not.
- run-now respects the `enabled` toggle by design — an operator who
  disabled a job and clicks "run now" gets `SKIPPED_DISABLED`, not a
  surprise execution. The button is greyed out for disabled jobs.

## Alternatives considered

- **Full migration into the DB** (cron + tuning + toggles): rejected
  for the configuration-as-code reasons above
- **Separate `-manual` ShedLock name for run-now**: rejected because
  same-name lock gives clearer "concurrent tick wins" semantics with
  zero extra plumbing
- **LISTEN/NOTIFY for instant toggle propagation**: rejected as
  overkill for the latency tolerance of the use case

## References

- Issue #516 — Admin UI: scheduler dashboard
- ADR-0035 — Storage consistency + reaper (introduced ShedLock)
- PR #514 (#190), PR #515 (#496) — preceding storage admin surfaces
  this builds on
