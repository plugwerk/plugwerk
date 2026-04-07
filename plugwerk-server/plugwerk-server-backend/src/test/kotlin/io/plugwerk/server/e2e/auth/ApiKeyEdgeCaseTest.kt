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
package io.plugwerk.server.e2e.auth

import io.plugwerk.server.domain.NamespaceAccessKeyEntity
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.OffsetDateTime
import java.util.UUID

/**
 * Edge-case tests specific to API key authentication:
 * revoked keys, expired keys, cross-namespace access, malformed keys, and write operations.
 */
class ApiKeyEdgeCaseTest : AbstractAuthorizationTest() {

    @Test
    fun `valid NS1 API key can access NS1 catalog`() {
        val key = requireNotNull(apiKeyCache[Actor.API_KEY_NS1])
        mockMvc.perform(
            get("/api/v1/namespaces/$NS1/plugins")
                .header("X-Api-Key", key),
        )
            .andExpect(status().isOk)
    }

    @Test
    fun `valid NS1 API key on NS2 catalog returns PUBLIC visibility`() {
        // API keys for a different namespace are authenticated but resolveVisibility returns PUBLIC,
        // so NS2 returns its public plugins (200) rather than 403.
        val key = requireNotNull(apiKeyCache[Actor.API_KEY_NS1])
        mockMvc.perform(
            get("/api/v1/namespaces/$NS2/plugins")
                .header("X-Api-Key", key),
        )
            .andExpect(status().isOk)
    }

    @Test
    fun `NS2 API key can access NS2 private catalog`() {
        val key = requireNotNull(apiKeyCache[Actor.API_KEY_NS2])
        mockMvc.perform(
            get("/api/v1/namespaces/$NS2/plugins")
                .header("X-Api-Key", key),
        )
            .andExpect(status().isOk)
    }

    @Test
    fun `revoked API key is treated as anonymous`() {
        // Create a key, then revoke it
        val namespace = namespaceRepository.findBySlug(NS1).orElseThrow()
        val plainKey = "pwk_revoked" + (1..32).map { "abcdefghijklmnopqrstuvwxyz"[it % 26] }.joinToString("")
        val keyHash = requireNotNull(passwordEncoder.encode(plainKey))
        accessKeyRepository.save(
            NamespaceAccessKeyEntity(
                namespace = namespace,
                keyHash = keyHash,
                keyPrefix = plainKey.take(8),
                name = "revoked-test-key-${UUID.randomUUID().toString().take(8)}",
                revoked = true,
            ),
        )

        // Revoked key on public NS1 → falls through as anonymous → 401 (Spring Security 6 rejects anonymous)
        mockMvc.perform(
            get("/api/v1/namespaces/$NS1/plugins")
                .header("X-Api-Key", plainKey),
        )
            .andExpect(status().isUnauthorized)

        // Revoked key on private NS2 → anonymous → 401 (no PublicNamespaceFilter for private)
        mockMvc.perform(
            get("/api/v1/namespaces/$NS2/plugins")
                .header("X-Api-Key", plainKey),
        )
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `expired API key is treated as anonymous`() {
        val namespace = namespaceRepository.findBySlug(NS1).orElseThrow()
        val plainKey = "pwk_expired" + (1..31).map { "abcdefghijklmnopqrstuvwxyz"[it % 26] }.joinToString("")
        val keyHash = requireNotNull(passwordEncoder.encode(plainKey))
        accessKeyRepository.save(
            NamespaceAccessKeyEntity(
                namespace = namespace,
                keyHash = keyHash,
                keyPrefix = plainKey.take(8),
                name = "expired-test-key-${UUID.randomUUID().toString().take(8)}",
                expiresAt = OffsetDateTime.now().minusDays(1),
            ),
        )

        // Expired key on public NS1 → anonymous → 401 (Spring Security 6 rejects anonymous)
        mockMvc.perform(
            get("/api/v1/namespaces/$NS1/plugins")
                .header("X-Api-Key", plainKey),
        )
            .andExpect(status().isUnauthorized)

        // Expired key on private NS2 → anonymous → 401
        mockMvc.perform(
            get("/api/v1/namespaces/$NS2/plugins")
                .header("X-Api-Key", plainKey),
        )
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `unknown API key is treated as anonymous`() {
        mockMvc.perform(
            get("/api/v1/namespaces/$NS1/plugins")
                .header("X-Api-Key", "pwk_unknownkeyvaluethatdoesnotexistindb12345678"),
        )
            .andExpect(status().isUnauthorized) // Unknown key → anonymous → 401 (Spring Security 6)

        mockMvc.perform(
            get("/api/v1/namespaces/$NS2/plugins")
                .header("X-Api-Key", "pwk_unknownkeyvaluethatdoesnotexistindb12345678"),
        )
            .andExpect(status().isUnauthorized) // Private NS2 → no anonymous access
    }

    @Test
    fun `API key cannot perform write operations`() {
        val key = requireNotNull(apiKeyCache[Actor.API_KEY_NS1])
        val jarBytes = buildMinimalJar("api-key-write-test", "1.0.0")
        val artifact = MockMultipartFile("artifact", "test.jar", "application/java-archive", jarBytes)

        val request = multipart("/api/v1/namespaces/$NS1/plugin-releases")
            .file(artifact)
        mockMvc.perform(
            request.header("X-Api-Key", key),
        )
            .andExpect(status().isForbidden)
    }

    @Test
    fun `API key cannot access admin endpoints`() {
        val key = requireNotNull(apiKeyCache[Actor.API_KEY_NS1])
        mockMvc.perform(
            get("/api/v1/admin/users")
                .header("X-Api-Key", key),
        )
            .andExpect(status().isForbidden)
    }
}
