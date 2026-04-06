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
        namespace = namespaceRepository.save(NamespaceEntity(slug = "apikey-ns", ownerOrg = "Org"))
    }

    @Test
    fun `findByKeyPrefixAndRevokedFalse returns active keys matching prefix`() {
        apiKeyRepository.save(
            NamespaceAccessKeyEntity(
                namespace = namespace,
                keyHash = "\$2a\$10\$fakebcrypthash1",
                keyPrefix = "pwk_abcd",
                name = "key-1",
            ),
        )
        apiKeyRepository.save(
            NamespaceAccessKeyEntity(
                namespace = namespace,
                keyHash = "\$2a\$10\$fakebcrypthash2",
                keyPrefix = "pwk_abcd",
                name = "key-2",
                revoked = true,
            ),
        )

        val found = apiKeyRepository.findByKeyPrefixAndRevokedFalse("pwk_abcd")

        assertThat(found).hasSize(1)
        assertThat(found[0].name).isEqualTo("key-1")
    }

    @Test
    fun `findByKeyPrefixAndRevokedFalse returns empty for unknown prefix`() {
        val found = apiKeyRepository.findByKeyPrefixAndRevokedFalse("pwk_zzzz")

        assertThat(found).isEmpty()
    }

    @Test
    fun `findAllByNamespaceAndRevokedFalse returns only active keys`() {
        apiKeyRepository.save(
            NamespaceAccessKeyEntity(
                namespace = namespace,
                keyHash = "\$2a\$10\$hashA",
                keyPrefix = "pwk_aaaa",
                name = "key-1",
                revoked = false,
            ),
        )
        apiKeyRepository.save(
            NamespaceAccessKeyEntity(
                namespace = namespace,
                keyHash = "\$2a\$10\$hashB",
                keyPrefix = "pwk_bbbb",
                name = "key-2",
                revoked = false,
            ),
        )
        apiKeyRepository.save(
            NamespaceAccessKeyEntity(
                namespace = namespace,
                keyHash = "\$2a\$10\$hashC",
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
