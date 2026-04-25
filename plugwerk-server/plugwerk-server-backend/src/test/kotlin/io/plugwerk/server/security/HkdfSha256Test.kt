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

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

/**
 * Conformance tests for [HkdfSha256] against the RFC 5869 published test vectors.
 *
 * The IETF test vectors live in RFC 5869 Appendix A. Test Case 1 (SHA-256, basic
 * "with salt and info") is the gold standard for any HKDF-SHA256 implementation;
 * passing it proves the bit-exact correctness of both the Extract and the
 * Expand steps. We additionally check Test Case 2 (longer inputs) so a regression
 * in multi-block expand cannot slip through.
 *
 * If a future refactor breaks this file, *do not "fix" the expected output*.
 * The expected values come straight from the RFC and are the canonical
 * definition of correct HKDF-SHA256 behaviour.
 */
class HkdfSha256Test {

    @Test
    fun `RFC 5869 Test Case 1 — basic SHA-256, 22B IKM, 13B salt, 10B info, 42B OKM`() {
        // From RFC 5869 Appendix A.1
        val ikm = hex("0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b")
        val salt = hex("000102030405060708090a0b0c")
        val info = hex("f0f1f2f3f4f5f6f7f8f9")
        val expectedOkm = hex(
            "3cb25f25faacd57a90434f64d0362f2a" +
                "2d2d0a90cf1a5a4c5db02d56ecc4c5bf" +
                "34007208d5b887185865",
        )

        val okm = HkdfSha256.derive(salt = salt, ikm = ikm, info = info, length = 42)

        assertThat(okm).isEqualTo(expectedOkm)
    }

    @Test
    fun `RFC 5869 Test Case 2 — 80B IKM, 80B salt, 80B info, 82B OKM (multi-block expand)`() {
        // From RFC 5869 Appendix A.2 — exercises the multi-block expand loop
        // (82B output > 32B HashLen requires 3 blocks).
        val ikm = hex(
            "000102030405060708090a0b0c0d0e0f" +
                "101112131415161718191a1b1c1d1e1f" +
                "202122232425262728292a2b2c2d2e2f" +
                "303132333435363738393a3b3c3d3e3f" +
                "404142434445464748494a4b4c4d4e4f",
        )
        val salt = hex(
            "606162636465666768696a6b6c6d6e6f" +
                "707172737475767778797a7b7c7d7e7f" +
                "808182838485868788898a8b8c8d8e8f" +
                "909192939495969798999a9b9c9d9e9f" +
                "a0a1a2a3a4a5a6a7a8a9aaabacadaeaf",
        )
        val info = hex(
            "b0b1b2b3b4b5b6b7b8b9babbbcbdbebf" +
                "c0c1c2c3c4c5c6c7c8c9cacbcccdcecf" +
                "d0d1d2d3d4d5d6d7d8d9dadbdcdddedf" +
                "e0e1e2e3e4e5e6e7e8e9eaebecedeeef" +
                "f0f1f2f3f4f5f6f7f8f9fafbfcfdfeff",
        )
        val expectedOkm = hex(
            "b11e398dc80327a1c8e7f78c596a4934" +
                "4f012eda2d4efad8a050cc4c19afa97c" +
                "59045a99cac7827271cb41c65e590e09" +
                "da3275600c2f09b8367793a9aca3db71" +
                "cc30c58179ec3e87c14c01d5c1f3434f" +
                "1d87",
        )

        val okm = HkdfSha256.derive(salt = salt, ikm = ikm, info = info, length = 82)

        assertThat(okm).isEqualTo(expectedOkm)
    }

    @Test
    fun `same purpose plus same IKM is deterministic across invocations`() {
        val salt = ByteArray(32)
        val ikm = "fixed-secret".toByteArray()
        val info = "purpose".toByteArray()

        val first = HkdfSha256.derive(salt, ikm, info, 32)
        val second = HkdfSha256.derive(salt, ikm, info, 32)

        assertThat(first).isEqualTo(second)
    }

    @Test
    fun `different info strings yield different keys (domain separation)`() {
        val salt = ByteArray(32)
        val ikm = "fixed-secret".toByteArray()

        val a = HkdfSha256.derive(salt, ikm, "purpose-a".toByteArray(), 32)
        val b = HkdfSha256.derive(salt, ikm, "purpose-b".toByteArray(), 32)

        assertThat(a).isNotEqualTo(b)
    }

    @Test
    fun `length must be at least 1`() {
        assertThatThrownBy {
            HkdfSha256.derive(ByteArray(32), "ikm".toByteArray(), "info".toByteArray(), 0)
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `length must not exceed 255 times HashLen (RFC 5869 spec maximum)`() {
        // 255 * 32 = 8160 is the spec ceiling for SHA-256
        assertThatThrownBy {
            HkdfSha256.derive(ByteArray(32), "ikm".toByteArray(), "info".toByteArray(), 8161)
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    private fun hex(s: String): ByteArray {
        require(s.length % 2 == 0) { "hex string must have even length" }
        val out = ByteArray(s.length / 2)
        for (i in out.indices) {
            out[i] = ((s[i * 2].digitToInt(16) shl 4) or s[i * 2 + 1].digitToInt(16)).toByte()
        }
        return out
    }
}
