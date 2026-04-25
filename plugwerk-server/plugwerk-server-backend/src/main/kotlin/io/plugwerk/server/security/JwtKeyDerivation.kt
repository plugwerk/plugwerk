/*
 * Copyright (c) 2025-present devtank42 GmbH
 *
 * This file is part of Plugwerk.
 *
 * Plugwerk is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Plugwerk is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Plugwerk. If not, see <https://www.gnu.org/licenses/>.
 */
package io.plugwerk.server.security

import io.plugwerk.server.PlugwerkProperties
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

/**
 * Derives all server-side HMAC-SHA256 keys from the single configured
 * `PLUGWERK_AUTH_JWT_SECRET` via HKDF-SHA256 (SBS-012 / #267).
 *
 * ## Why HKDF instead of using the secret bytes directly
 *
 * The naive pre-fix approach was `SecretKeySpec(jwtSecret.toByteArray(UTF_8), "HmacSHA256")`.
 * Two problems with that:
 *
 *  1. **Effective entropy below 256 bits.** A 32-character ASCII passphrase
 *     contains ~5–6 bits of entropy per character — well below the 256 bits
 *     HMAC-SHA256 expects from a uniformly-random key. HKDF's extract step
 *     concentrates whatever entropy the input does have into a full-strength
 *     pseudo-random key.
 *  2. **No domain separation.** Both [JwtConfiguration] (token signing) and
 *     [AccessKeyHmac] (refresh-token + access-key lookup hash) used the same
 *     raw bytes. Compromising one signing surface effectively compromised
 *     the other. Per-purpose `info` strings in HKDF make the derived keys
 *     cryptographically independent, even though they share an `IKM`.
 *
 * ## What HKDF does NOT protect against
 *
 * HKDF improves the *encoding* and *separation* of the secret. It does **not**
 * protect against a low-entropy input (e.g. a dictionary-word passphrase) being
 * brute-forced offline. The
 * [PlugwerkProperties.AuthProperties.jwtSecret] field still requires
 * `@Size(min = 32)` and the
 * [io.plugwerk.server.config.PlugwerkPropertiesValidator.BLOCKED_JWT_SECRETS]
 * blocklist still rejects copy-paste defaults — those are unrelated, complementary
 * checks.
 *
 * ## Salt choice
 *
 * RFC 5869 §3.1 explicitly permits a zero-filled salt (`HashLen` bytes), and
 * we use that. The audit recommendation mentioned a "per-server salt"; that
 * uniqueness is in practice already provided by the `IKM` itself (each
 * deployment has its own `jwtSecret`). Adding a separate per-server salt
 * variable would just be one more thing to misconfigure without raising the
 * security floor.
 *
 * ## Versioning
 *
 * Each purpose string carries a `-v1` suffix so a future key-rotation policy
 * can introduce `-v2` derivations side-by-side without breaking compatibility
 * with already-issued artefacts. Today only `-v1` exists.
 */
@Component
class JwtKeyDerivation(props: PlugwerkProperties) {

    private val ikm: ByteArray = props.auth.jwtSecret.toByteArray(StandardCharsets.UTF_8)

    /**
     * Returns the 32-byte HMAC-SHA256 [SecretKey] for the given [purpose].
     * The same `(jwtSecret, purpose)` pair always produces the same key.
     * Distinct purposes yield cryptographically independent keys.
     */
    fun deriveKey(purpose: Purpose): SecretKey {
        val okm = HkdfSha256.derive(
            salt = EMPTY_SALT,
            ikm = ikm,
            info = purpose.info.toByteArray(StandardCharsets.UTF_8),
            length = 32,
        )
        return SecretKeySpec(okm, "HmacSHA256")
    }

    /**
     * Closed enum of every HKDF purpose used inside the server. Adding a new
     * derivation site (e.g. a future password-reset-token MAC) means adding
     * an entry here — by design, so all key derivations are visible in one
     * place and impossible to accidentally collide.
     */
    enum class Purpose(val info: String) {
        /** Signs and verifies access JWTs in [io.plugwerk.server.config.JwtConfiguration]. */
        JWT_SIGNING("plugwerk-jwt-signing-v1"),

        /**
         * Computes [io.plugwerk.server.security.AccessKeyHmac] used for the
         * `namespace_access_key.key_lookup_hash` and the
         * `refresh_token.token_lookup_hash` columns.
         */
        ACCESS_KEY_HMAC("plugwerk-access-key-hmac-v1"),
    }

    private companion object {
        // RFC 5869 §3.1: an unspecified salt is treated as HashLen zeros.
        // We pass that explicitly so the call site is self-documenting.
        val EMPTY_SALT = ByteArray(32)
    }
}
