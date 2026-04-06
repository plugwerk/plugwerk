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
import org.springframework.dao.DataIntegrityViolationException
import java.time.OffsetDateTime
import kotlin.test.assertFailsWith

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
    fun `findByKeyHash returns key when hash exists`() {
        apiKeyRepository.save(
            NamespaceAccessKeyEntity(namespace = namespace, keyHash = "deadbeef01234567", name = "test-key"),
        )

        val found = apiKeyRepository.findByKeyHash("deadbeef01234567")

        assertThat(found).isPresent
        assertThat(found.get().namespace.id).isEqualTo(namespace.id!!)
    }

    @Test
    fun `findByKeyHash returns empty for unknown hash`() {
        val found = apiKeyRepository.findByKeyHash("unknown-hash")

        assertThat(found).isEmpty
    }

    @Test
    fun `findAllByNamespaceAndRevokedFalse returns only active keys`() {
        apiKeyRepository.save(
            NamespaceAccessKeyEntity(namespace = namespace, keyHash = "hash-active-1", name = "key-1", revoked = false),
        )
        apiKeyRepository.save(
            NamespaceAccessKeyEntity(namespace = namespace, keyHash = "hash-active-2", name = "key-2", revoked = false),
        )
        apiKeyRepository.save(
            NamespaceAccessKeyEntity(namespace = namespace, keyHash = "hash-revoked", name = "key-3", revoked = true),
        )

        val activeKeys = apiKeyRepository.findAllByNamespaceAndRevokedFalse(namespace)

        assertThat(activeKeys).hasSize(2)
        assertThat(activeKeys.map { it.keyHash }).containsExactlyInAnyOrder("hash-active-1", "hash-active-2")
    }

    @Test
    fun `save persists optional fields correctly`() {
        val expiresAt = OffsetDateTime.now().plusDays(30)
        val key =
            apiKeyRepository.save(
                NamespaceAccessKeyEntity(
                    namespace = namespace,
                    keyHash = "full-key-hash",
                    name = "CI/CD key",
                    expiresAt = expiresAt,
                ),
            )

        val found = apiKeyRepository.findById(key.id!!).orElseThrow()

        assertThat(found.name).isEqualTo("CI/CD key")
        assertThat(found.expiresAt).isNotNull()
        assertThat(found.revoked).isFalse()
    }

    @Test
    fun `save fails on duplicate key_hash`() {
        apiKeyRepository.save(
            NamespaceAccessKeyEntity(namespace = namespace, keyHash = "duplicate-hash", name = "test-key"),
        )
        apiKeyRepository.flush()

        assertFailsWith<DataIntegrityViolationException> {
            apiKeyRepository.saveAndFlush(
                NamespaceAccessKeyEntity(namespace = namespace, keyHash = "duplicate-hash", name = "test-key"),
            )
        }
    }
}
