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
package io.plugwerk.server.service

import io.plugwerk.server.domain.NamespaceEntity
import io.plugwerk.server.repository.NamespaceRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.Optional
import kotlin.test.assertFailsWith

@ExtendWith(MockitoExtension::class)
class NamespaceServiceTest {

    @Mock
    lateinit var namespaceRepository: NamespaceRepository

    @InjectMocks
    lateinit var namespaceService: NamespaceService

    @Test
    fun `findBySlug returns namespace when it exists`() {
        val entity = NamespaceEntity(slug = "acme", ownerOrg = "ACME Corp")
        whenever(namespaceRepository.findBySlug("acme")).thenReturn(Optional.of(entity))

        val result = namespaceService.findBySlug("acme")

        assertThat(result.slug).isEqualTo("acme")
        assertThat(result.ownerOrg).isEqualTo("ACME Corp")
    }

    @Test
    fun `findBySlug throws NamespaceNotFoundException when not found`() {
        whenever(namespaceRepository.findBySlug("missing")).thenReturn(Optional.empty())

        assertFailsWith<NamespaceNotFoundException> {
            namespaceService.findBySlug("missing")
        }
    }

    @Test
    fun `create saves and returns new namespace`() {
        whenever(namespaceRepository.existsBySlug("new-ns")).thenReturn(false)
        val saved = NamespaceEntity(slug = "new-ns", ownerOrg = "Org")
        whenever(namespaceRepository.save(any<NamespaceEntity>())).thenReturn(saved)

        val result = namespaceService.create("new-ns", "Org")

        assertThat(result.slug).isEqualTo("new-ns")
        verify(namespaceRepository).save(any<NamespaceEntity>())
    }

    @Test
    fun `create throws NamespaceAlreadyExistsException when slug is taken`() {
        whenever(namespaceRepository.existsBySlug("existing")).thenReturn(true)

        assertFailsWith<NamespaceAlreadyExistsException> {
            namespaceService.create("existing", "Org")
        }

        verify(namespaceRepository, never()).save(any<NamespaceEntity>())
    }

    @Test
    fun `update changes ownerOrg and settings`() {
        val entity = NamespaceEntity(slug = "acme", ownerOrg = "Old Org")
        whenever(namespaceRepository.findBySlug("acme")).thenReturn(Optional.of(entity))
        whenever(namespaceRepository.save(any<NamespaceEntity>())).thenReturn(entity)

        namespaceService.update("acme", ownerOrg = "New Org", settings = """{"key":"val"}""")

        assertThat(entity.ownerOrg).isEqualTo("New Org")
        assertThat(entity.settings).isEqualTo("""{"key":"val"}""")
    }

    @Test
    fun `delete removes namespace`() {
        val entity = NamespaceEntity(slug = "to-delete", ownerOrg = "Org")
        whenever(namespaceRepository.findBySlug("to-delete")).thenReturn(Optional.of(entity))

        namespaceService.delete("to-delete")

        verify(namespaceRepository).delete(entity)
    }

    @Test
    fun `delete throws NamespaceNotFoundException when namespace missing`() {
        whenever(namespaceRepository.findBySlug("missing")).thenReturn(Optional.empty())

        assertFailsWith<NamespaceNotFoundException> {
            namespaceService.delete("missing")
        }
    }
}
