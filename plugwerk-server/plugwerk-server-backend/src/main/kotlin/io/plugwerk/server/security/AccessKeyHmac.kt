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
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Computes the deterministic `namespace_access_key.key_lookup_hash` value used for
 * constant-time access-key lookup (ADR-0024, audit row SBS-008 / #291).
 *
 * The HMAC-SHA256 key is derived from the JWT signing secret — both secrets live
 * server-side only, both grant authentication-forgery capability if leaked, and
 * both must be rotated together on compromise. Sharing them keeps the operator
 * surface small at the cost of coupling key rotation to JWT rotation; if that
 * coupling ever becomes a problem, a dedicated `PLUGWERK_AUTH_ACCESS_KEY_HMAC_SECRET`
 * env var can be introduced without changing the stored-hash shape.
 *
 * The returned value is lowercase hex of the 32-byte MAC (64 chars). `MessageDigest.isEqual`
 * isn't needed at the caller site because equality is enforced by the database's
 * indexed lookup on `key_lookup_hash` — a hit or miss emits the same `SELECT … WHERE hash = ?`
 * round-trip regardless of contents.
 */
@Component
class AccessKeyHmac(private val props: PlugwerkProperties) {

    private val macKey: SecretKeySpec = SecretKeySpec(props.auth.jwtSecret.toByteArray(Charsets.UTF_8), MAC_ALGO)

    fun compute(plainKey: String): String {
        val mac = Mac.getInstance(MAC_ALGO)
        mac.init(macKey)
        val digest = mac.doFinal(plainKey.toByteArray(Charsets.UTF_8))
        return digest.toHexString()
    }

    private fun ByteArray.toHexString(): String = buildString(size * 2) {
        for (b in this@toHexString) {
            append(HEX[(b.toInt() ushr 4) and 0x0F])
            append(HEX[b.toInt() and 0x0F])
        }
    }

    companion object {
        private const val MAC_ALGO = "HmacSHA256"
        private val HEX = "0123456789abcdef".toCharArray()
    }
}
