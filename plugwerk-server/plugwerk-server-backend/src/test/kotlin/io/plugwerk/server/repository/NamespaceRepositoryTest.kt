/*
 * Plugwerk — Plugin Marketplace for the PF4J Ecosystem
 * Copyright (C) 2026 devtank42 GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package io.plugwerk.server.repository

import io.plugwerk.server.AbstractRepositoryTest
import io.plugwerk.server.domain.NamespaceEntity
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DataIntegrityViolationException
import kotlin.test.assertFailsWith

open class NamespaceRepositoryTest : AbstractRepositoryTest() {

    @Autowired
    lateinit var namespaceRepository: NamespaceRepository

    @Test
    fun `findBySlug returns namespace when slug exists`() {
        val namespace =
            namespaceRepository.save(
                NamespaceEntity(slug = "acme", ownerOrg = "ACME Corp"),
            )

        val found = namespaceRepository.findBySlug("acme")

        assertThat(found).isPresent
        assertThat(found.get().id).isEqualTo(namespace.id!!)
        assertThat(found.get().ownerOrg).isEqualTo("ACME Corp")
    }

    @Test
    fun `findBySlug returns empty when slug does not exist`() {
        val found = namespaceRepository.findBySlug("does-not-exist")

        assertThat(found).isEmpty
    }

    @Test
    fun `existsBySlug returns true when slug exists`() {
        namespaceRepository.save(NamespaceEntity(slug = "exists-ns", ownerOrg = "Org"))

        assertThat(namespaceRepository.existsBySlug("exists-ns")).isTrue()
        assertThat(namespaceRepository.existsBySlug("missing-ns")).isFalse()
    }

    @Test
    fun `save persists settings as JSON`() {
        val namespace =
            namespaceRepository.save(
                NamespaceEntity(
                    slug = "ns-with-settings",
                    ownerOrg = "Org",
                    settings = """{"maxPlugins": 100}""",
                ),
            )

        val found = namespaceRepository.findById(namespace.id!!).orElseThrow()

        assertThat(found.settings).contains("maxPlugins")
    }

    @Test
    fun `save fails on duplicate slug`() {
        namespaceRepository.save(NamespaceEntity(slug = "duplicate", ownerOrg = "Org A"))
        namespaceRepository.flush()

        assertFailsWith<DataIntegrityViolationException> {
            namespaceRepository.saveAndFlush(NamespaceEntity(slug = "duplicate", ownerOrg = "Org B"))
        }
    }

    @Test
    fun `delete removes namespace`() {
        val namespace = namespaceRepository.save(NamespaceEntity(slug = "to-delete", ownerOrg = "Org"))

        namespaceRepository.delete(namespace)

        assertThat(namespaceRepository.findById(namespace.id!!)).isEmpty
    }
}
