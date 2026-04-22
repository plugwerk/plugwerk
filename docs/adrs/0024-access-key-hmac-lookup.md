# ADR-0024: Access-key lookup via HMAC-SHA256

## Status

Accepted

## Context

The 1.0.0-beta.1 security audit (tracker [#255]) flagged finding SBS-008 / H-XX / [#291]: `NamespaceAccessKeyAuthFilter` performed its candidate lookup by the plaintext `key_prefix` column (`findByKeyPrefixAndRevokedFalse`). The DB round-trip cost differed between "prefix exists" (rows returned) and "prefix does not exist" (empty result), letting an attacker enumerate valid 8-character prefixes purely by measuring HTTP response latency — no full-secret guessing required. Once a valid prefix is known the remaining search space is tractable.

Two remediation shapes were considered:

1. **Constant-time flow over the existing schema.** Always execute a BCrypt comparison, using a fixed dummy hash on the miss path so the total request cost is dominated by the ~100 ms BCrypt verification either way. Simple to implement, no schema change. Downside: the DB round-trip itself still leaks sub-millisecond timing that is measurable over many samples — BCrypt's cost masks the leak but does not eliminate it.
2. **HMAC-based equality lookup.** Store `HMAC-SHA256(serverSecret, plainKey)` in a dedicated indexed column. A single equality probe on that column takes statistically equivalent time whether or not a row matches, by construction of the index. Requires a schema change and cannot back-fill existing rows (plaintext keys aren't stored), so every previously issued key is invalidated on upgrade.

## Decision

Adopt option 2. Remediation targets the root cause (DB timing) rather than masking it.

### Schema

Migration `0009_access_key_lookup_hash.yaml` adds:

- `key_lookup_hash varchar(64) NOT NULL UNIQUE` — hex-encoded HMAC-SHA256 (32 bytes → 64 chars).
- Unique constraint `uq_access_key_lookup_hash` backs the equality probe.
- Existing rows are back-filled with a sentinel (`'INVALIDATED-' || id::text`) that is guaranteed not to collide with a real HMAC output (real outputs are 64 lowercase hex chars). This renders every previously issued access key unusable — acceptable in the 1.0.0-beta.1 window because no GA deployment exists yet. Operators must re-issue keys after upgrading.
- `idx_access_key_prefix` (from `0001`) is dropped; `key_prefix` stays as a display-only column.

### HMAC secret

The HMAC key is derived from `plugwerk.auth.jwt-secret`. Both secrets live server-side only, both grant authentication-forgery capability if leaked, and both must be rotated together on compromise. Sharing them avoids adding an operator-facing env var. If the coupling becomes operationally inconvenient later, a dedicated `PLUGWERK_AUTH_ACCESS_KEY_HMAC_SECRET` can be introduced without changing the stored-hash shape — the new secret simply replaces the input to `AccessKeyHmac` and invalidates any access keys that were created under the old secret.

### Filter flow

`NamespaceAccessKeyAuthFilter.authenticateConstantTime` is rewired to:

1. Compute the HMAC of the presented header value.
2. One `findByKeyLookupHashAndRevokedFalse` equality lookup.
3. Exactly one BCrypt `matches()` call — against the row's stored `key_hash` on a hit, or against a fixed dummy BCrypt ciphertext on a miss. This preserves the defense-in-depth check without letting the miss path short-circuit to zero-cost.
4. Authenticate if (and only if) a non-expired row matched *and* the BCrypt check passed.

The BCrypt second check is retained because `key_lookup_hash` is keyed with a secret we already treat as compromise-sensitive, and a belt-and-braces BCrypt verify costs nothing on the happy path while guarding against a future mistake where the HMAC secret leaks but BCrypt hashes don't.

### Test coverage

- `AccessKeyHmacTest` pins determinism + output shape + secret independence.
- `NamespaceAccessKeyRepositoryTest` covers the new `findByKeyLookupHashAndRevokedFalse` query (hit / miss / revoked).
- `AccessKeyTimingIT` runs 300 interleaved hit/miss lookups against a real PostgreSQL container and asserts that `max(hit_median, miss_median) / min(...) < 2.0`. Pre-fix this ratio was > 10×; post-fix it is ~1.0. The assertion is ratio-based and uses the median so CI jitter does not produce false positives.

## Consequences

### Good

- Primary: the DB round-trip no longer signals prefix existence. An attacker has no observable oracle for partial matches; they must guess the full key.
- The lookup is `O(1)` on an indexed column (pre-fix it was `O(1)` too, but with a cardinality-dependent timing signature).
- `MessageDigest.isEqual`-level constant-time comparison is enforced transitively: the database's B-tree equality probe on a uniform-width hex column has no branch-dependent timing over the input.

### Breaking (alpha window)

- Every previously issued access key becomes unusable on upgrade. Operators must re-issue and re-distribute keys. Documented in `AGENTS.md`.
- Rotating `PLUGWERK_AUTH_JWT_SECRET` now also invalidates all access keys. Re-issuance is required. Acceptable because JWT rotation already invalidates all outstanding JWTs on the same deployment, so operators treat it as a credential-rotation event.

### Watch

- If the HMAC-secret / JWT-secret coupling proves inconvenient operationally (e.g. key rotation cadence should differ from JWT rotation), split to a dedicated `PLUGWERK_AUTH_ACCESS_KEY_HMAC_SECRET`. No schema change needed — only `AccessKeyHmac`.
- If Hibernate ever reorders fields in a way that reverses the `key_lookup_hash` index's uniqueness, the migration's unique constraint will still catch it at commit time.

## References

- Audit finding SBS-008 — `docs/audits/1.0.0-beta.1-artifacts/triage-SBS.csv`
- Issue [#291]
- Migration `plugwerk-server/plugwerk-server-backend/src/main/resources/db/changelog/migrations/0009_access_key_lookup_hash.yaml`
- Helper `plugwerk-server/plugwerk-server-backend/src/main/kotlin/io/plugwerk/server/security/AccessKeyHmac.kt`
- Filter `plugwerk-server/plugwerk-server-backend/src/main/kotlin/io/plugwerk/server/security/NamespaceAccessKeyAuthFilter.kt`
- Timing test `plugwerk-server/plugwerk-server-backend/src/test/kotlin/io/plugwerk/server/security/AccessKeyTimingIT.kt`

[#255]: https://github.com/plugwerk/plugwerk/issues/255
[#291]: https://github.com/plugwerk/plugwerk/issues/291
