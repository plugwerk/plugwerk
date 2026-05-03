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
 * Audit row SBS-003 / ADR-0022.
 *
 * The encryptor encrypts OIDC provider client secrets at rest via
 * Spring Security's `Encryptors.text()`, which derives an AES-256 key from the
 * supplied password with `PBKDF2WithHmacSHA1`. The tests below verify:
 *
 * 1. A 16-char password (historical lower bound) round-trips successfully.
 * 2. A 32-char password (recommended length) round-trips successfully.
 * 3. Ciphertext produced with one password cannot be decrypted with another —
 *    this is the migration invariant that makes key rotation a re-enter-secrets
 *    operation, not an in-place upgrade.
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
     * `AesBytesEncryptor` uses a random 16-byte IV per encryption, so two calls
     * with the same plaintext must never emit identical output — otherwise
     * equal ciphertexts would leak equal plaintexts.
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
}
