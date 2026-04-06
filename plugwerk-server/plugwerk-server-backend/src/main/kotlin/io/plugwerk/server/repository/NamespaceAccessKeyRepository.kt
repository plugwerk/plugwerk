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

import io.plugwerk.server.domain.NamespaceAccessKeyEntity
import io.plugwerk.server.domain.NamespaceEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional
import java.util.UUID

interface NamespaceAccessKeyRepository : JpaRepository<NamespaceAccessKeyEntity, UUID> {

    fun findByKeyHash(keyHash: String): Optional<NamespaceAccessKeyEntity>

    fun findAllByNamespace(namespace: NamespaceEntity): List<NamespaceAccessKeyEntity>

    fun findAllByNamespaceAndRevokedFalse(namespace: NamespaceEntity): List<NamespaceAccessKeyEntity>

    fun existsByNamespaceAndName(namespace: NamespaceEntity, name: String): Boolean
}
