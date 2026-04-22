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
package io.plugwerk.server.repository

import io.plugwerk.server.AbstractRepositoryTest
import io.plugwerk.server.domain.NamespaceAccessKeyEntity
import io.plugwerk.server.domain.NamespaceEntity
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.OffsetDateTime

open class NamespaceAccessKeyRepositoryTest : AbstractRepositoryTest() {

    @Autowired
    lateinit var namespaceRepository: NamespaceRepository

    @Autowired
    lateinit var apiKeyRepository: NamespaceAccessKeyRepository

    lateinit var namespace: NamespaceEntity

    @BeforeEach
    fun setup() {
        namespace = namespaceRepository.save(NamespaceEntity(slug = "apikey-ns", name = "Org"))
    }

    @Test
    fun `findByKeyLookupHashAndRevokedFalse returns active key matching lookup hash`() {
        apiKeyRepository.save(
            NamespaceAccessKeyEntity(
                namespace = namespace,
                keyHash = "\$2a\$10\$fakebcrypthash1",
                keyLookupHash = "a".repeat(64),
                keyPrefix = "pwk_abcd",
                name = "key-1",
            ),
        )
        apiKeyRepository.save(
            NamespaceAccessKeyEntity(
                namespace = namespace,
                keyHash = "\$2a\$10\$fakebcrypthash2",
                keyLookupHash = "b".repeat(64),
                keyPrefix = "pwk_abcd",
                name = "key-2-revoked",
                revoked = true,
            ),
        )

        val found = apiKeyRepository.findByKeyLookupHashAndRevokedFalse("a".repeat(64)).orElse(null)

        assertThat(found).isNotNull
        assertThat(found.name).isEqualTo("key-1")
    }

    @Test
    fun `findByKeyLookupHashAndRevokedFalse returns empty for unknown hash`() {
        val found = apiKeyRepository.findByKeyLookupHashAndRevokedFalse("z".repeat(64))

        assertThat(found).isEmpty
    }

    @Test
    fun `findByKeyLookupHashAndRevokedFalse excludes revoked keys`() {
        apiKeyRepository.save(
            NamespaceAccessKeyEntity(
                namespace = namespace,
                keyHash = "\$2a\$10\$hash",
                keyLookupHash = "c".repeat(64),
                keyPrefix = "pwk_rvkd",
                name = "revoked-key",
                revoked = true,
            ),
        )

        val found = apiKeyRepository.findByKeyLookupHashAndRevokedFalse("c".repeat(64))

        assertThat(found).isEmpty
    }

    @Test
    fun `findAllByNamespaceAndRevokedFalse returns only active keys`() {
        apiKeyRepository.save(
            NamespaceAccessKeyEntity(
                namespace = namespace,
                keyHash = "\$2a\$10\$hashA",
                keyLookupHash = "1".repeat(64),
                keyPrefix = "pwk_aaaa",
                name = "key-1",
                revoked = false,
            ),
        )
        apiKeyRepository.save(
            NamespaceAccessKeyEntity(
                namespace = namespace,
                keyHash = "\$2a\$10\$hashB",
                keyLookupHash = "2".repeat(64),
                keyPrefix = "pwk_bbbb",
                name = "key-2",
                revoked = false,
            ),
        )
        apiKeyRepository.save(
            NamespaceAccessKeyEntity(
                namespace = namespace,
                keyHash = "\$2a\$10\$hashC",
                keyLookupHash = "3".repeat(64),
                keyPrefix = "pwk_cccc",
                name = "key-3",
                revoked = true,
            ),
        )

        val activeKeys = apiKeyRepository.findAllByNamespaceAndRevokedFalse(namespace)

        assertThat(activeKeys).hasSize(2)
    }

    @Test
    fun `save persists optional fields correctly`() {
        val expiresAt = OffsetDateTime.now().plusDays(30)
        val key =
            apiKeyRepository.save(
                NamespaceAccessKeyEntity(
                    namespace = namespace,
                    keyHash = "\$2a\$10\$fullhash",
                    keyLookupHash = "4".repeat(64),
                    keyPrefix = "pwk_full",
                    name = "CI/CD key",
                    expiresAt = expiresAt,
                ),
            )

        val found = apiKeyRepository.findById(key.id!!).orElseThrow()

        assertThat(found.name).isEqualTo("CI/CD key")
        assertThat(found.keyPrefix).isEqualTo("pwk_full")
        assertThat(found.expiresAt).isNotNull()
        assertThat(found.revoked).isFalse()
    }
}
