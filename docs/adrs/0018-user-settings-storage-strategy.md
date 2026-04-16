# ADR-0018: User Settings Storage Strategy — Separate Table with Lazy Creation

## Status

Accepted

## Context

Issue [#209](https://github.com/plugwerk/plugwerk/issues/209) requires persistent storage for
user-specific settings such as preferred language, theme, and default namespace. The Profile
Settings UI (`ProfileSettingsPage.tsx`) already displays these fields, but changes are not
persisted — the save handler is a TODO stub.

Three storage options were evaluated:

- **A** — Extend `plugwerk_user` with preference columns.
- **B** — Separate `user_setting` table with FK to `plugwerk_user`.
- **C** — Separate `user_setting` table keyed by `user_subject` (no FK to `plugwerk_user`),
  created lazily on first preference change.

Option A mixes identity with preferences and does not work for OIDC users who have no
`plugwerk_user` row. Option B requires a FK to `plugwerk_user`, which again excludes OIDC
users. Option C decouples settings from the identity table entirely.

## Decision

Adopt **Option C** — a separate `user_setting` table keyed by `user_subject` with lazy
creation:

1. **`user_setting` table** stores one row per user per setting key:

   | Column | Type | Description |
   |---|---|---|
   | `id` | UUID (PK) | UUIDv7 |
   | `user_subject` | VARCHAR(255), NOT NULL | Username (local) or OIDC `sub` claim |
   | `setting_key` | VARCHAR(128), NOT NULL | Dotted key from `UserSettingKey` enum |
   | `setting_value` | TEXT, nullable | Stringified value, null = use default |
   | `updated_at` | TIMESTAMPTZ | Last modification |

   Unique constraint on `(user_subject, setting_key)`.

2. **No FK to `plugwerk_user`.** The `user_subject` column is a logical identifier extracted
   from the JWT `sub` claim (`Authentication.name` in Spring Security). This works for both
   local users (where `sub` = username) and OIDC users (where `sub` = provider subject).

3. **Lazy creation.** No rows are seeded. A user's settings rows are created on first
   `PATCH /api/v1/users/me/settings`. Users who never customize preferences have zero rows.

4. **`UserSettingKey` enum** is the single registry of supported per-user settings, mirroring
   the `SettingKey` pattern from ADR-0016. Each entry declares its key, value type, default
   value, and optional validator.

5. **Initial settings:**

   | Key | Type | Default | Description |
   |---|---|---|---|
   | `preferred_language` | ENUM | `en` | User's preferred UI language |
   | `default_namespace` | STRING | `""` (empty) | Default namespace for navigation |
   | `theme` | ENUM | `system` | UI theme: `light`, `dark`, or `system` |

6. **API endpoints** are scoped to the authenticated user only:

   - `GET /api/v1/users/me/settings` — returns all settings with defaults filled in.
   - `PATCH /api/v1/users/me/settings` — upserts changed settings, validates values.

   No admin override for user settings — each user manages their own.

### Relationship to ADR-0016

ADR-0016 established the pattern for **global admin-managed** application settings. This ADR
reuses the same structural patterns (enum registry, typed validation, key-value storage) but
differs in scope:

| Aspect | ADR-0016 (Application Settings) | ADR-0018 (User Settings) |
|---|---|---|
| Scope | Global, process-wide | Per-user |
| Access | Superadmin only | Authenticated user (own settings) |
| Table | `application_setting` | `user_setting` |
| Key type | `SettingKey` | `UserSettingKey` |
| Caching | In-memory `AtomicReference` (hot path) | No cache (per-user, low frequency) |
| Seeding | Liquibase inserts defaults | No seed (lazy creation) |

## Consequences

### Positive

- **OIDC-compatible.** No dependency on `plugwerk_user` — works for any authentication method
  that provides a stable `sub` claim.
- **Clean separation.** Identity data stays in `plugwerk_user`; preferences live in
  `user_setting`. Neither table needs columns from the other.
- **Zero storage overhead** for users who never customize preferences.
- **Consistent pattern** with ADR-0016 — same enum-registry + validation approach.
- **Self-service.** Users manage their own settings; no admin involvement needed.

### Negative / Trade-offs

- **Extra join for user profile views.** If a UI page needs both identity and preferences, two
  queries are needed (or a service-layer merge). Acceptable for the low-frequency profile page.
- **No cascade delete.** If a `plugwerk_user` is deleted, their `user_setting` rows are
  orphaned (the FK-less design means no cascade). A cleanup task or explicit deletion in the
  user deletion flow is needed.
- **`user_subject` is opaque.** For OIDC users, the subject string is provider-specific and
  may not be human-readable. This is acceptable — the column is a lookup key, not a display
  value.

## Alternatives Considered

- **Option A (extend `plugwerk_user`):** Rejected — OIDC users may not have a row.
- **Option B (FK to `plugwerk_user`):** Rejected — same OIDC limitation.
- **JSON column on `plugwerk_user`:** Rejected — same OIDC limitation, plus schema-less
  storage loses per-key validation and makes migration harder.
