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
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.Optional
import java.util.UUID

interface NamespaceAccessKeyRepository : JpaRepository<NamespaceAccessKeyEntity, UUID> {

    /**
     * Constant-time lookup by the deterministic HMAC of the plaintext key
     * (see [io.plugwerk.server.security.AccessKeyHmac]). The equality probe on
     * the indexed `key_lookup_hash` column takes the same time whether a row
     * matches or not, eliminating the prefix-enumeration timing leak addressed
     * by ADR-0024 (SBS-008 / #291).
     */
    @Query(
        "SELECT k FROM NamespaceAccessKeyEntity k JOIN FETCH k.namespace WHERE k.keyLookupHash = :keyLookupHash AND k.revoked = false",
    )
    fun findByKeyLookupHashAndRevokedFalse(
        @Param("keyLookupHash") keyLookupHash: String,
    ): Optional<NamespaceAccessKeyEntity>

    fun findAllByNamespace(namespace: NamespaceEntity): List<NamespaceAccessKeyEntity>

    fun findAllByNamespaceAndRevokedFalse(namespace: NamespaceEntity): List<NamespaceAccessKeyEntity>

    fun existsByNamespaceAndName(namespace: NamespaceEntity, name: String): Boolean
}
