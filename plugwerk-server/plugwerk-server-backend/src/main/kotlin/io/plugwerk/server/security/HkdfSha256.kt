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

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * HMAC-based Extract-and-Expand Key Derivation Function (HKDF) over SHA-256.
 *
 * Implementation of [RFC 5869](https://datatracker.ietf.org/doc/html/rfc5869).
 * Pure JDK — no Bouncycastle dependency. Conformance is pinned by
 * [io.plugwerk.server.security.HkdfSha256Test] against the published RFC test
 * vectors (Appendix A.1 + A.2); do not "fix" that test file if it ever fails.
 *
 * ## Two-step process
 *
 *  - **Extract:** `PRK = HMAC-SHA256(salt, IKM)` — concentrates the entropy
 *    of the input keying material into a 32-byte pseudo-random key.
 *  - **Expand:** `OKM = HMAC-SHA256(PRK, info || counter)`, repeated and
 *    chained until the requested output length is reached.
 *
 * ## Production use
 *
 * Used by [JwtKeyDerivation] to derive distinct, cryptographically independent
 * HMAC keys for the JWT signer, the access-key lookup HMAC, and any future
 * server-side secret — all from the single `PLUGWERK_AUTH_JWT_SECRET` input,
 * but with per-purpose `info` strings that guarantee domain separation
 * (SBS-012 / #267).
 */
object HkdfSha256 {

    private const val ALGO = "HmacSHA256"
    private const val HASH_LEN = 32
    private const val MAX_OUTPUT_BLOCKS = 255

    /**
     * Derives [length] bytes of output keying material.
     *
     * @param salt non-secret randomness; an empty array is RFC-allowed and is
     *   internally treated as `HashLen` zeros (the RFC default).
     * @param ikm input keying material — the shared secret being expanded.
     * @param info per-purpose context string used to separate derivations from
     *   the same `ikm`. MUST differ for each distinct downstream use.
     * @param length number of OKM bytes to produce. Must be in
     *   `1..255 * HashLen`, i.e. `1..8160` for SHA-256 (RFC 5869 §2.3).
     */
    fun derive(salt: ByteArray, ikm: ByteArray, info: ByteArray, length: Int): ByteArray {
        require(length in 1..(MAX_OUTPUT_BLOCKS * HASH_LEN)) {
            "length must be between 1 and ${MAX_OUTPUT_BLOCKS * HASH_LEN}, got $length"
        }
        val effectiveSalt = if (salt.isEmpty()) ByteArray(HASH_LEN) else salt
        val prk = hmac(effectiveSalt, ikm)
        return expand(prk, info, length)
    }

    private fun expand(prk: ByteArray, info: ByteArray, length: Int): ByteArray {
        val blocks = (length + HASH_LEN - 1) / HASH_LEN
        val output = ByteArray(blocks * HASH_LEN)
        var previousBlock = ByteArray(0)
        for (i in 1..blocks) {
            val mac = Mac.getInstance(ALGO)
            mac.init(SecretKeySpec(prk, ALGO))
            mac.update(previousBlock)
            mac.update(info)
            mac.update(i.toByte()) // counter as a single byte (i is in 1..255)
            previousBlock = mac.doFinal()
            previousBlock.copyInto(output, (i - 1) * HASH_LEN)
        }
        return output.copyOf(length)
    }

    private fun hmac(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance(ALGO)
        mac.init(SecretKeySpec(key, ALGO))
        return mac.doFinal(data)
    }
}
