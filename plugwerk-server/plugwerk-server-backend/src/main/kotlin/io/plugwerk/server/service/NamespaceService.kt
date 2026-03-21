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
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class NamespaceService(private val namespaceRepository: NamespaceRepository) {

    fun findBySlug(slug: String): NamespaceEntity = namespaceRepository.findBySlug(slug)
        .orElseThrow { NamespaceNotFoundException(slug) }

    fun findAll(): List<NamespaceEntity> = namespaceRepository.findAll()

    @Transactional
    fun create(slug: String, ownerOrg: String, settings: String? = null): NamespaceEntity {
        if (namespaceRepository.existsBySlug(slug)) throw NamespaceAlreadyExistsException(slug)
        return namespaceRepository.save(NamespaceEntity(slug = slug, ownerOrg = ownerOrg, settings = settings))
    }

    @Transactional
    fun update(slug: String, ownerOrg: String? = null, settings: String? = null): NamespaceEntity {
        val entity = findBySlug(slug)
        ownerOrg?.let { entity.ownerOrg = it }
        settings?.let { entity.settings = it }
        return namespaceRepository.save(entity)
    }

    @Transactional
    fun delete(slug: String) {
        val entity = findBySlug(slug)
        namespaceRepository.delete(entity)
    }
}
