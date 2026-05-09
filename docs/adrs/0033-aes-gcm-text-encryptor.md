# ADR-0033: AES-GCM (AEAD) for `TextEncryptor` — `Encryptors.text()` → `Encryptors.delux()`

## Status

Accepted

## Context

[ADR-0022](0022-encryption-key-size.md) documented the contract of the
`textEncryptor` bean (used to encrypt OIDC client secrets and PASSWORD-typed
application settings at rest) and listed three "Revisit conditions". One of
them was:

> **AEAD required.** A shift from CBC to GCM (or equivalent AEAD) would give
> integrity guarantees in addition to confidentiality, at the cost of a
> ciphertext migration.

Issue [#476] re-opened that discussion. The original write-up of #476 claimed
the CBC-based `Encryptors.text()` produced **deterministic** ciphertexts
("identical plaintexts → identical ciphertexts"). Verification against the
Spring Security 7.0.5 source proved that claim incorrect — `Encryptors.text()`
wraps `AesBytesEncryptor` with `KeyGenerators.secureRandom(16)`, which
generates a **fresh random IV per encryption**. The pre-existing test
`same plaintext encrypts to different ciphertexts (random IV)` in
`SecurityConfigurationTextEncryptorTest` was already passing under the old
code. The original "deterministic ciphertext" framing was therefore a
misreading of the Spring API; see the corrective comment on #476.

What **is** worth fixing is the lack of **AEAD integrity**:

- Under CBC, a tampered ciphertext silently decrypts to garbage. The caller
  cannot distinguish a corrupted row from a valid one and may pass garbage
  credentials to an OIDC provider, surface a meaningless string in an admin
  UI, or write further state derived from corrupted plaintext.
- Under GCM, a tampered ciphertext fails the authenticated-encryption tag
  check and the decrypt call throws. The corruption is loud.

Plus two collateral reasons:

- `Encryptors.text()` is deprecated in modern Spring Security versions in
  favour of GCM-based alternatives. Staying on the deprecated API accumulates
  upgrade risk.
- AEAD (specifically AES-GCM with random nonce) is the modern default for
  at-rest secret encryption.

## Decision

Switch the `textEncryptor()` bean from `Encryptors.text(...)` (AES-256-CBC,
no MAC) to `Encryptors.delux(...)` (AES-256-GCM, AEAD, hex-encoded
`TextEncryptor` wrapper around `Encryptors.stronger()`).

Both functions return the same `TextEncryptor` Spring interface — the bean
type and every consumer's constructor signature stays identical. The only
caller-visible difference is that GCM ciphertexts are slightly longer than
CBC ciphertexts (12-byte nonce + 16-byte tag overhead) and that any
modification to a stored ciphertext now throws on decrypt instead of
returning garbage.

### Updated contract

| Aspect | Value |
|---|---|
| Cipher | **AES-256-GCM (AEAD)** |
| Key size | 256 bits (fixed by `AesBytesEncryptor`, unchanged) |
| Key derivation | `PBKDF2WithHmacSHA1`, 1024 iterations (unchanged — see "Out of scope" below) |
| Salt | Deterministic, SHA-256(password) first 8 bytes hex-encoded (unchanged) |
| Nonce / IV | **16-byte random per ciphertext, with GCM 16-byte authentication tag** |
| Encoding | Hex-encoded (unchanged — `HexEncodingTextEncryptor` wraps both `text()` and `delux()`) |
| Stored in | `oidc_provider.client_secret_encrypted`, `application_setting.value` (PASSWORD-typed) (unchanged) |
| Validation | `@NotBlank` + `@Size(min = 16, max = 256)` (unchanged) |
| Recommendation | 32+ characters (e.g. `openssl rand -base64 32`) (unchanged) |

### Why no ciphertext migration

The standard concern when changing cipher modes is that pre-existing
ciphertexts encrypted with the old algorithm cannot be decrypted with the
new one. We are at Plugwerk Phase 2 alpha. There are no production
deployments holding `oidc_provider`-rows or PASSWORD-typed
`application_setting`-rows that need to be preserved across this change.
The greenfield assumption was confirmed before adopting this decision.

For any deployment that does have existing CBC-encrypted secrets, the
operator-facing remediation is the same as for an encryption-key rotation
(see ADR-0022 "Rotation"): re-enter each OIDC provider's client secret
through the admin UI, and re-set any PASSWORD-typed application settings.
The behaviour is identical to a fresh deployment from that point forward.

If a future Plugwerk version ships into a customer base that holds CBC
ciphertexts, a Liquibase `customChange` migration that decrypts with the
legacy bean and re-encrypts with the GCM bean would be the path. That work
is explicitly **not** part of this ADR; track it separately if and when it
becomes relevant.

### Out of scope

- **PBKDF2 iteration count.** ADR-0022 already flagged 1024 iterations as a
  legacy default. This ADR does not address it. Same risk profile, different
  fix, deserves its own decision.
- **Argon2id / scrypt KDF migration.** Same.
- **First-class encryption-key rotation workflow.** Still on the
  ADR-0022 revisit list.
- **Re-encryption migration for existing CBC ciphertexts.** Out of scope by
  the greenfield assumption above. If/when needed, separate ADR + Liquibase
  customChange.

## Rotation

Unchanged from ADR-0022 — rotating `PLUGWERK_AUTH_ENCRYPTION_KEY` still
invalidates every encrypted row because the PBKDF2-derived AES key changes
with the password. Operators re-enter secrets after rotation. The cipher
mode (CBC vs. GCM) does not affect rotation semantics.

## Consequences

### Good

- **AEAD integrity.** Tampered ciphertexts fail the GCM tag check and throw
  on decrypt instead of silently returning garbage. The
  `tampered ciphertext is rejected on decrypt` test in
  `SecurityConfigurationTextEncryptorTest` pins this property as a
  regression guard.
- **Off the deprecated API.** `Encryptors.text()` is deprecated; this moves
  to a non-deprecated alternative without changing any consumer code.
- **Modern default.** AES-256-GCM is the current standard for at-rest
  secret encryption. Future Plugwerk versions and security audits will
  expect this baseline.

### Neutral

- **No DB migration in this PR.** Greenfield assumption — see "Why no
  ciphertext migration" above.
- **Bean type and consumer signatures unchanged.** `Encryptors.delux()`
  returns the same `TextEncryptor` interface as `Encryptors.text()`. None
  of `OidcProviderService`, `DbClientRegistrationRepository`, or
  `ApplicationSettingsService` change.
- **Hex encoding unchanged.** Both `text()` and `delux()` wrap their
  underlying `BytesEncryptor` in `HexEncodingTextEncryptor`. Column
  storage characteristics (charset, hex digits) are identical.

### Watch

- **Ciphertext size increase.** GCM adds ~28 bytes (12-byte nonce + 16-byte
  tag) over the raw CBC equivalent, hex-encoded ~56 chars more. The
  `oidc_provider.client_secret_encrypted` column is `length = 1024`, well
  above realistic OIDC client-secret sizes after the GCM overhead.
  `application_setting.value` is `TEXT` (no fixed limit). No schema change
  needed.
- **Greenfield assumption.** If a deployment with pre-existing CBC
  ciphertexts is encountered, every decrypt of an old row will throw. The
  symptom is OIDC login failure or "Could not decrypt smtp.password" at
  startup. Recovery: re-enter the secrets via admin UI.

## References

- Issue [#476] (with corrective comment on the original "deterministic
  ciphertext" claim)
- ADR-0022 — encryption-key size, originally listed AEAD as a revisit
  condition
- Spring Security source: `spring-security-crypto-7.0.5-sources.jar` →
  `Encryptors.delux`, `Encryptors.stronger`, `AesBytesEncryptor`
- `plugwerk-server/plugwerk-server-backend/src/main/kotlin/io/plugwerk/server/config/SecurityConfiguration.kt`
- `plugwerk-server/plugwerk-server-backend/src/test/kotlin/io/plugwerk/server/config/SecurityConfigurationTextEncryptorTest.kt`

[#476]: https://github.com/plugwerk/plugwerk/issues/476
