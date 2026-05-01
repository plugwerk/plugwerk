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

import io.plugwerk.server.domain.OidcIdentityEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional
import java.util.UUID

interface OidcIdentityRepository : JpaRepository<OidcIdentityEntity, UUID> {

    /**
     * Lookup by `(provider, sub)` — the upstream identifier pair. Used by
     * `OidcIdentityService.upsertOnLogin` on every OIDC callback to decide
     * whether to issue a fresh `plugwerk_user` row or bump
     * `plugwerk_user.last_login_at` on an existing one.
     */
    fun findByOidcProviderIdAndSubject(oidcProviderId: UUID, subject: String): Optional<OidcIdentityEntity>

    /**
     * `UNIQUE(user_id)` on the table guarantees this returns at most one row —
     * Plugwerk does not support identity linking, see issue #351.
     */
    fun findByUserId(userId: UUID): Optional<OidcIdentityEntity>

    /**
     * Used by `OidcProviderService.delete` to enumerate the users that need
     * to be disabled before the SQL cascade runs (Politik C from #351).
     */
    fun findAllByOidcProviderId(oidcProviderId: UUID): List<OidcIdentityEntity>
}
