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

import io.plugwerk.server.PlugwerkProperties
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

/**
 * Pins the round-trip contract for `SecurityConfiguration.textEncryptor()`.
 * Audit row SBS-003 / ADR-0022 / ADR-0033.
 *
 * The encryptor protects OIDC provider client secrets and PASSWORD-typed
 * application settings at rest via Spring Security's `Encryptors.delux()`,
 * which wraps `Encryptors.stronger()` (AES-256-GCM with a fresh random
 * 16-byte nonce per encryption) in a hex-encoding `TextEncryptor`. The
 * tests below verify:
 *
 * 1. A 16-char password (historical lower bound) round-trips successfully.
 * 2. A 32-char password (recommended length) round-trips successfully.
 * 3. Ciphertext produced with one password cannot be decrypted with another —
 *    this is the migration invariant documented in ADR-0022: rotating the
 *    encryption key invalidates every existing `client_secret_encrypted` row,
 *    which is why operators must re-enter each OIDC provider's client secret
 *    through the admin UI after rotation.
 * 4. Two encryptions of the same plaintext produce different ciphertexts —
 *    GCM uses a fresh random nonce per call, so a database dump cannot leak
 *    plaintext-equality between rows by comparing ciphertexts.
 * 5. A tampered ciphertext is rejected on decrypt — GCM is AEAD and detects
 *    any modification of the ciphertext or its associated data. This is the
 *    integrity guarantee that the previous CBC-based `Encryptors.text()`
 *    did not provide and is the primary reason for ADR-0033's migration.
 */
class SecurityConfigurationTextEncryptorTest {

    private fun buildSecurityConfig(encryptionKey: String): SecurityConfiguration {
        val props = PlugwerkProperties(
            auth = PlugwerkProperties.AuthProperties(
                jwtSecret = "a".repeat(32),
                encryptionKey = encryptionKey,
            ),
        )
        return SecurityConfiguration(
            loginRateLimitFilter = mock(),
            registerRateLimitFilter = mock(),
            passwordResetRateLimitFilter = mock(),
            refreshRateLimitFilter = mock(),
            changePasswordRateLimitFilter = mock(),
            apiKeyAuthFilter = mock(),
            publicNamespaceFilter = mock(),
            passwordChangeRequiredFilter = mock(),
            props = props,
            oidcProviderRegistry = mock(),
            localJwtDecoder = mock(),
            namespaceAuthorizationService = mock(),
        )
    }

    @Test
    fun `16-character key round-trips ciphertext`() {
        val encryptor = buildSecurityConfig("k".repeat(16)).textEncryptor()
        val plaintext = "oidc-client-secret-value"
        val ciphertext = encryptor.encrypt(plaintext)

        assertThat(ciphertext).isNotEqualTo(plaintext)
        assertThat(encryptor.decrypt(ciphertext)).isEqualTo(plaintext)
    }

    @Test
    fun `32-character key round-trips ciphertext`() {
        val encryptor = buildSecurityConfig("k".repeat(32)).textEncryptor()
        val plaintext = "oidc-client-secret-value"
        val ciphertext = encryptor.encrypt(plaintext)

        assertThat(ciphertext).isNotEqualTo(plaintext)
        assertThat(encryptor.decrypt(ciphertext)).isEqualTo(plaintext)
    }

    /**
     * Ciphertext produced with password A must not be decryptable with password B.
     * This is the migration invariant documented in ADR-0022: rotating the
     * encryption key invalidates every existing `client_secret_encrypted` row,
     * which is why operators must re-enter each OIDC provider's client secret
     * through the admin UI after rotation.
     */
    @Test
    fun `ciphertext from one key is not decryptable with a different key`() {
        val encryptorA = buildSecurityConfig("a".repeat(32)).textEncryptor()
        val encryptorB = buildSecurityConfig("b".repeat(32)).textEncryptor()
        val ciphertext = encryptorA.encrypt("sensitive-value")

        assertThatThrownBy { encryptorB.decrypt(ciphertext) }
            .isInstanceOf(Exception::class.java)
    }

    /**
     * Each `encrypt()` call must produce different ciphertext for the same plaintext.
     * `AesBytesEncryptor` (in GCM mode, see ADR-0033) generates a fresh random
     * 16-byte nonce per encryption, so two calls with the same plaintext must
     * never emit identical output — otherwise equal ciphertexts would leak equal
     * plaintexts to anyone with database read access.
     */
    @Test
    fun `same plaintext encrypts to different ciphertexts (random IV)`() {
        val encryptor = buildSecurityConfig("k".repeat(32)).textEncryptor()
        val plaintext = "oidc-client-secret-value"

        val first = encryptor.encrypt(plaintext)
        val second = encryptor.encrypt(plaintext)

        assertThat(first).isNotEqualTo(second)
        assertThat(encryptor.decrypt(first)).isEqualTo(plaintext)
        assertThat(encryptor.decrypt(second)).isEqualTo(plaintext)
    }

    /**
     * GCM's authenticated-encryption tag must reject any ciphertext that has
     * been modified after encryption. This is the integrity property AES-CBC
     * never had — under the previous `Encryptors.text()` (CBC mode without a
     * MAC), a flipped bit decrypted to garbage instead of throwing, so the
     * caller could not distinguish a corrupted row from a valid one and might
     * silently feed garbage credentials to an OIDC provider. With GCM the
     * decrypt call throws on tampering, surfacing the corruption clearly.
     *
     * The test flips one byte in the middle of the hex-encoded ciphertext;
     * any single-byte modification anywhere in the ciphertext or in the GCM
     * tag must invalidate the AEAD check.
     */
    @Test
    fun `tampered ciphertext is rejected on decrypt (GCM AEAD integrity)`() {
        val encryptor = buildSecurityConfig("k".repeat(32)).textEncryptor()
        val plaintext = "oidc-client-secret-value"
        val ciphertext = encryptor.encrypt(plaintext)

        // Flip one byte (= two hex chars) somewhere in the middle. The exact
        // offset does not matter as long as it is not a no-op; the GCM tag
        // covers the entire ciphertext + nonce.
        val mid = ciphertext.length / 2
        val flippedChar = if (ciphertext[mid] == '0') '1' else '0'
        val tampered = ciphertext.substring(0, mid) + flippedChar + ciphertext.substring(mid + 1)

        assertThatThrownBy { encryptor.decrypt(tampered) }
            .isInstanceOf(Exception::class.java)
    }
}
