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

import io.plugwerk.server.domain.UserEntity
import io.plugwerk.server.domain.UserSource
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.Optional
import java.util.UUID

interface UserRepository : JpaRepository<UserEntity, UUID> {

    /**
     * Lookup by username — used by the local-login flow only. After the
     * identity-hub split (#351), `username` is NULL on OIDC rows so OIDC
     * subjects cannot be returned by accident: the partial unique index
     * `uq_plugwerk_user_username_local` keeps the column unique within
     * `source = 'LOCAL'` only.
     */
    fun findByUsernameAndSource(username: String, source: UserSource): Optional<UserEntity>

    fun existsByUsernameAndSource(username: String, source: UserSource): Boolean

    fun findAllByEnabled(enabled: Boolean): List<UserEntity>

    /**
     * Bulk-disable users. Used by `OidcProviderService.delete` when a provider
     * is being removed (Politik C from issue #351): disable rather than
     * cascade-delete so the audit trail survives.
     */
    @Modifying
    @Query("UPDATE UserEntity u SET u.enabled = false WHERE u.id IN :ids")
    fun disableAll(@Param("ids") ids: Collection<UUID>): Int
}
