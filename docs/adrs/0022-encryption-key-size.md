# ADR-0022: Encryption-key size — PBKDF2 password, not AES-128 literal

## Status

Accepted

## Context

The 1.0.0-beta.1 security audit (tracker [#255]) flagged finding SBS-003 / M-02 / [#264]: "`PlugwerkProperties.AuthProperties.encryptionKey` is constrained to `@Size(min = 16, max = 16)`, which forces AES-128 for OIDC provider client secrets at rest. Document the AES-256 upgrade path."

Verification against Spring Security 7.0.4 sources (`spring-security-crypto-7.0.4-sources.jar`) shows the audit's framing is technically incorrect:

```java
// Encryptors.java
public static TextEncryptor text(CharSequence password, CharSequence salt) {
    return new HexEncodingTextEncryptor(standard(password, salt));
}

public static BytesEncryptor standard(CharSequence password, CharSequence salt) {
    return new AesBytesEncryptor(password.toString(), salt, KeyGenerators.secureRandom(16));
}

// AesBytesEncryptor constructor
public AesBytesEncryptor(String password, CharSequence salt, BytesKeyGenerator ivGenerator, CipherAlgorithm alg) {
    this(CipherUtils.newSecretKey("PBKDF2WithHmacSHA1",
            new PBEKeySpec(password.toCharArray(), Hex.decode(salt), 1024, 256)),
        ivGenerator, alg);
}
```

Reading the contract:

- `encryptionKey` is a **password**, not raw AES key bytes.
- Spring derives the AES key with `PBKDF2WithHmacSHA1`, 1024 iterations, **256-bit** output.
- The AES key size is hardcoded at 256 bits regardless of password length.
- Default cipher is `CBC`. `KeyGenerators.secureRandom(16)` produces a 16-byte random IV per encryption — 16 here is the IV width, not the key width.

So the 16-char minimum never forced AES-128; it only bounded the PBKDF2 input entropy. The remediation is therefore documentation + relaxed validation, not a crypto rewrite.

## Decision

Relax the `@Size` constraint on `PlugwerkProperties.AuthProperties.encryptionKey` from `min = 16, max = 16` to `min = 16, max = 256`, and restate the contract everywhere it is documented.

### Contract

| Aspect | Value |
|---|---|
| Cipher | AES-256-CBC |
| Key size | 256 bits (fixed by `AesBytesEncryptor`) |
| Key derivation | `PBKDF2WithHmacSHA1`, 1024 iterations |
| Salt | Deterministic, SHA-256(password) first 8 bytes hex-encoded |
| IV | 16-byte random per ciphertext (authenticated only as part of CBC — no AEAD) |
| Stored in | `oidc_provider.client_secret_encrypted` column |
| Validation | `@NotBlank` + `@Size(min = 16, max = 256)` |
| Recommendation | 32+ characters (e.g. `openssl rand -base64 32`) |

### Why a documentation fix, not a crypto rewrite

- **AES-256 is already in effect.** Every existing deployment's ciphertext is AES-256, not AES-128.
- **No DB migration is needed.** Relaxing the `@Size` constraint accepts longer passwords without touching already-encrypted rows. Increasing the password length on a running system still requires manual re-encryption because the derived key changes (see "Rotation" below) — that procedure is unrelated to this change.
- **Switching cipher would be a contract break.** Moving to AES-GCM or an upgraded PBKDF2 iteration count would require ciphertext migration for every OIDC provider row. That work is tracked separately (see "Revisit conditions" below) and explicitly out of scope for #264.

### Validation

```kotlin
@field:NotBlank(message = "plugwerk.auth.encryption-key must not be blank — set PLUGWERK_AUTH_ENCRYPTION_KEY")
@field:Size(
    min = 16,
    max = 256,
    message = "plugwerk.auth.encryption-key must be at least 16 characters (32+ recommended)",
)
val encryptionKey: String = "",
```

16 is retained as the minimum for continuity with existing deployments (`openssl rand -hex 8` produces a 16-char password that was valid before this change and remains valid after). 32+ is the recommendation in all operator-facing docs. 256 is the upper bound — anything beyond that is almost certainly a copy-paste accident.

## Rotation

Rotating `PLUGWERK_AUTH_ENCRYPTION_KEY` invalidates every `oidc_provider.client_secret_encrypted` value in the database because the PBKDF2-derived AES key changes with the password. There is no in-place upgrade: operators must re-enter each OIDC provider's client secret through the admin UI (or re-encrypt ciphertext offline using both keys) after rotation.

Same-key migrations — e.g. extending a 16-character password to 32 characters — are still full rotations from the encryptor's perspective: a different password derives a different key.

## Revisit conditions

Re-open this ADR (or supersede it) when any of the following holds:

- **PBKDF2 iteration count is materially too low.** 1024 iterations is well below current OWASP/NIST recommendations (600,000+ for PBKDF2-HMAC-SHA1/256). Follow-up work to swap in a modern KDF (Argon2id, scrypt, or PBKDF2 with a high iteration count) plus a ciphertext migration path is tracked separately. This ADR only documents the existing contract — it does not endorse 1024 iterations as sufficient indefinitely.
- **AEAD required.** A shift from CBC to GCM (or equivalent AEAD) would give integrity guarantees in addition to confidentiality, at the cost of a ciphertext migration.
- **Key rotation UX.** A first-class rotation workflow (re-encrypt existing rows on boot given both the old and new key) becomes a reasonable operator expectation once we exit alpha.

## Consequences

### Good

- The contract is now readable from the code and from every operator-facing doc. No cryptography audit or source-diving needed to understand what `PLUGWERK_AUTH_ENCRYPTION_KEY` actually is.
- Longer, higher-entropy passwords are now accepted without tripping validation. Existing 16-char keys continue to work unchanged.
- Future PBKDF2 / cipher upgrade work has a named ADR to supersede instead of bolting onto an unrelated change.

### Neutral

- No breaking change for existing deployments. Existing 16-char keys are still valid; existing ciphertext is still decryptable.

### Watch

- PBKDF2-HMAC-SHA1 with 1024 iterations is a legacy default. It is correct to flag this separately as a follow-up; it is out of scope for #264, which was explicitly about documenting the AES-256 path.
- The deterministic salt means two deployments using the same password also share the same derived AES key. Not exploitable inside a single deployment, but noted as a consequence of Spring's `Encryptors.text()` shape.

## References

- Audit finding SBS-003 / M-02 — `docs/audits/1.0.0-beta.1-artifacts/triage-SBS.csv`
- Issue [#264]
- Spring Security source: `spring-security-crypto-7.0.4-sources.jar` → `Encryptors`, `AesBytesEncryptor`
- `plugwerk-server/plugwerk-server-backend/src/main/kotlin/io/plugwerk/server/PlugwerkProperties.kt`
- `plugwerk-server/plugwerk-server-backend/src/main/kotlin/io/plugwerk/server/config/SecurityConfiguration.kt`

[#255]: https://github.com/plugwerk/plugwerk/issues/255
[#264]: https://github.com/plugwerk/plugwerk/issues/264
