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
package io.plugwerk.server.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import javax.crypto.AEADBadTagException
import javax.crypto.BadPaddingException

class CryptoDiagnosticsTest {

    @Test
    fun `isBadPadding detects direct BadPaddingException`() {
        assertThat(BadPaddingException("decrypt").isBadPadding()).isTrue()
    }

    @Test
    fun `isBadPadding detects Spring-wrapped form (#501)`() {
        // Spring's Encryptors.delux() wraps BadPaddingException in IllegalStateException
        // with the literal "Unable to invoke Cipher due to bad padding" — exact form
        // a misconfigured PLUGWERK_AUTH_ENCRYPTION_KEY produces in production.
        val wrapped = IllegalStateException(
            "Unable to invoke Cipher due to bad padding",
            BadPaddingException("decrypt"),
        )

        assertThat(wrapped.isBadPadding()).isTrue()
    }

    @Test
    fun `isBadPadding detects AEADBadTagException as a BadPaddingException subclass`() {
        // GCM authentication failures land here. Same root cause from an operator's
        // perspective: the ciphertext can't be authenticated under the current key.
        assertThat(AEADBadTagException("tag mismatch").isBadPadding()).isTrue()
    }

    @Test
    fun `isBadPadding returns false for unrelated exceptions`() {
        assertThat(IllegalArgumentException("nope").isBadPadding()).isFalse()
        assertThat(RuntimeException("network down").isBadPadding()).isFalse()
        assertThat(NullPointerException().isBadPadding()).isFalse()
    }

    @Test
    fun `isBadPadding terminates on null cause chain`() {
        // Standard Throwable has cause == null at the bottom — must not loop.
        assertThat(RuntimeException("boom").isBadPadding()).isFalse()
    }

    @Test
    fun `isBadPadding bounds traversal via depth guard for very long chains`() {
        // Throwable.initCause forbids cause == this, so we cannot construct a
        // true self-referential cycle. The depth guard's job is to also bound
        // pathologically long but acyclic chains — build one deeper than the
        // guard and assert detection terminates without locating a non-existent
        // BadPaddingException.
        var chain: Throwable = RuntimeException("leaf")
        repeat(50) { chain = RuntimeException("level-$it", chain) }

        assertThat(chain.isBadPadding()).isFalse()
    }

    @Test
    fun `encryptionKeyMismatchMessage names the env var verbatim`() {
        // The literal string "PLUGWERK_AUTH_ENCRYPTION_KEY" must appear so operators
        // can `grep` their logs for the exact env-var name.
        val msg = encryptionKeyMismatchMessage(
            subject = "OIDC provider 'foo'",
            hint = "Re-enter the secret.",
        )

        assertThat(msg).contains("PLUGWERK_AUTH_ENCRYPTION_KEY")
        assertThat(msg).contains("OIDC provider 'foo'")
        assertThat(msg).contains("Re-enter the secret.")
    }
}
