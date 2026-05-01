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
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.Optional
import java.util.UUID

/**
 * Spring Data interface-based projection used by
 * [OidcIdentityRepository.findProviderNamesForUsers]. Carries only the two
 * columns the admin user listing needs for its EXTERNAL-user provider hint
 * (`UserDto.providerName`) — no full entity load, no Hibernate lazy-init.
 */
interface UserProviderProjection {
    val userId: UUID
    val providerName: String
}

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

    /**
     * Batch lookup of `(userId, providerName)` pairs for an admin-user-listing
     * page (issue #412). One JPQL query joining `oidc_identity → oidc_provider`,
     * single SQL round-trip — explicitly designed to avoid an N+1 across
     * potentially thousands of EXTERNAL users.
     *
     * Callers must short-circuit on an empty input collection: some JDBC
     * drivers reject an empty `IN (...)` predicate. The current
     * [io.plugwerk.server.controller.AdminUserController] guards this.
     *
     * Returns at most one row per input id (the table has `UNIQUE(user_id)`).
     * Ids without a matching `oidc_identity` row are simply absent from the
     * result — that means "INTERNAL user" or, defensively, "data inconsistency
     * we should not 500 over"; the caller renders `providerName = null` in
     * either case.
     */
    @Query(
        """
        SELECT i.user.id AS userId, i.oidcProvider.name AS providerName
        FROM OidcIdentityEntity i
        WHERE i.user.id IN :userIds
        """,
    )
    fun findProviderNamesForUsers(@Param("userIds") userIds: Collection<UUID>): List<UserProviderProjection>
}
