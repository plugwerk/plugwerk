# ADR-0016: Application Settings Precedence — Database Is Authoritative for Admin-Manageable Settings

## Status

Accepted

## Context

Before this ADR, global application settings lived in two uncoordinated places:

1. **`application.yml`** — admin-manageable values such as `plugwerk.upload.max-file-size-mb` and
   `plugwerk.tracking.*` were exposed as environment variables but could only be changed by
   editing the file and restarting the server.
2. **Admin Settings UI** (`GeneralSection.tsx`) — displayed "Default language" and "Max upload
   size" form fields that were not persisted anywhere. Changes were lost on page reload.

Issue [#208](https://github.com/plugwerk/plugwerk/issues/208) enumerated three options for a
unified settings strategy:

- **A** — DB overrides YAML (YAML is fallback).
- **B** — Infra settings stay in YAML, app-level settings live in DB.
- **C** — DB is the single source of truth, YAML only for bootstrap.

Option A preserves the dual-source problem. Option B still forces us to pick a side per setting
and maintain two read paths. Option C is the cleanest, but bare C does not account for settings
that genuinely require a YAML value at boot (security/infra secrets, Spring multipart limits).

## Decision

Adopt a refined **Option C** — **"DB is authoritative, YAML is infra-only"**:

1. **Admin-manageable settings live exclusively in the `application_setting` database table.**
   There is no YAML fallback for them. Liquibase seeds defaults on first installation via a
   dedicated `insert` change set in migration `0005_application_settings.yaml`. Subsequent
   migrations modify the defaults only for brand-new installations; existing values are
   preserved.

2. **`application.yml` contains only infra/security/bootstrap settings** — the values the JVM
   or Spring framework must know before the `ApplicationContext` is fully initialized, or that
   are security-critical and must not be writable at runtime:

   | Stays in YAML | Reason |
   |---|---|
   | `spring.datasource.*` | Needed before Liquibase runs |
   | `plugwerk.storage.*` | Filesystem root, resolved at startup |
   | `plugwerk.auth.jwt-secret` | Security-critical, must not be DB-writable |
   | `plugwerk.auth.encryption-key` | Security-critical, must not be DB-writable |
   | `plugwerk.auth.token-validity-hours` | Affects JWT signing, low-frequency change |
   | `plugwerk.auth.rate-limit.*` | Hot-path, loaded once at startup |
   | `plugwerk.server.base-url` | Used to construct absolute URLs at boot |
   | `server.port` | Servlet container init |

3. **Removed from YAML entirely** (migrated to `application_setting`):

   | Key | Old YAML path | Value type |
   |---|---|---|
   | `general.default_language` | *(never in YAML, new)* | `ENUM(en,de)` |
   | `general.site_name` | *(never in YAML, new)* | `STRING` |
   | `upload.max_file_size_mb` | `plugwerk.upload.max-file-size-mb` | `INTEGER` |
   | `tracking.enabled` | `plugwerk.tracking.enabled` | `BOOLEAN` |
   | `tracking.capture_ip` | `plugwerk.tracking.capture-ip` | `BOOLEAN` |
   | `tracking.anonymize_ip` | `plugwerk.tracking.anonymize-ip` | `BOOLEAN` |
   | `tracking.capture_user_agent` | `plugwerk.tracking.capture-user-agent` | `BOOLEAN` |

   The `PLUGWERK_UPLOAD_MAX_FILE_SIZE_MB` and `PLUGWERK_TRACKING_*` environment variables are
   **removed**. This is a breaking change for existing deployments — see the release notes.

4. **`SettingKey` enum is the single registry.** Every supported key lists its type, its
   hard-coded default (used only for the Liquibase seed and as a last-resort fallback when the
   row is missing), a `requiresRestart` flag, and an optional validator.

5. **The `MultipartConfigElement` bean** reads the `upload.max_file_size_mb` value from
   `GeneralSettingsService` at bean-construction time (which runs after Liquibase but before
   the servlet container is fully wired). This means the filesystem upload ceiling is set from
   the DB value on startup. Changing the value at runtime still requires a server restart for
   the multipart filter to take effect — see issue [TBD: follow-up] for a runtime-override
   design. The Admin UI displays a restart notice whenever `upload.max_file_size_mb` has been
   changed since boot.

6. **A hard-coded safety ceiling** (`MAX_ALLOWED_UPLOAD_MB = 1024`) in `SettingKey` bounds the
   allowed DB value for `upload.max_file_size_mb`. The `PATCH` endpoint rejects values above
   the ceiling with HTTP 400.

### Read path

```
Service code  ─▶  GeneralSettingsService.get<T>(SettingKey)
                      │
                      ├─▶ in-memory cache (AtomicReference<Map<String,Any>>)
                      │        │
                      │        └─▶ hit   ─▶ return typed value
                      │
                      └─▶ miss ─▶ ApplicationSettingRepository.findBySettingKey()
                                       │
                                       ├─▶ row present ─▶ parse + cache + return
                                       │
                                       └─▶ row missing ─▶ return SettingKey.default
```

### Write path

```
PATCH /api/v1/admin/settings  (superadmin only)
        │
        ▼
Validate against SettingKey.validator
        │
        ▼
Upsert into application_setting
        │
        ▼
Refresh in-memory cache (atomic swap)
        │
        ▼
Return updated snapshot with `source` + `restartPending` per key
```

## Consequences

### Positive

- **Single source of truth** for every admin-manageable value — no more split-brain between
  YAML and DB.
- **Admin UI becomes functional** — changes persist across restarts.
- **`application.yml` shrinks and its purpose becomes clear**: bootstrap-only, security-only.
- **No more dual env-variable / UI confusion** for `max-file-size-mb` and `tracking.*`.
- **Fewer `@ConfigurationProperties` classes** to maintain (`UploadProperties` and
  `TrackingProperties` are deleted).
- **Adding a new admin setting** is a one-line addition to `SettingKey` plus one Liquibase
  seed row — no `application.yml` edit, no new env variable, no new `@ConfigurationProperties`
  field.

### Negative / Trade-offs

- **Breaking change:** `PLUGWERK_UPLOAD_MAX_FILE_SIZE_MB` and `PLUGWERK_TRACKING_*` environment
  variables are ignored from this version onward. Operators must configure these values via
  the Admin UI after the first startup (Liquibase seeds them with the previous defaults, so no
  action is required unless the deployment had overrides).
- **`upload.max_file_size_mb` still requires a restart** for the multipart filter to pick up a
  new value. The UI warns about this. A follow-up issue tracks a runtime-override
  implementation (e.g. custom `MultipartResolver`).
- **In-memory cache is per-instance.** Multi-node deployments would need a cache-invalidation
  channel, but Phase 1/2 targets single-node self-hosted deployments and this is acceptable.
- **No per-setting audit log** in this ADR — only the `updated_at` and `updated_by` columns on
  the row. A full audit trail is out of scope.

## Alternatives Considered

- **Option A (DB overrides YAML):** rejected because every read site has to check both
  sources, every setting has two possible states, and the "correct" value is ambiguous when
  env variables exist.
- **Option B (split YAML/DB by infra vs app):** rejected because the split is subjective and
  requires ongoing case-by-case decisions. `max_file_size_mb` does not fit neatly into either
  category.
- **Reload multipart config at runtime instead of requiring restart:** deferred to a follow-up
  issue — the implementation touches Tomcat's `MultipartConfigElement` wiring and is large
  enough to warrant its own change.
- **Spring Cloud Config / external config server:** disproportionate for a single-binary
  self-hosted server.
