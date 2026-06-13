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

import io.plugwerk.server.domain.NamespaceEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.OffsetDateTime
import java.util.Optional
import java.util.UUID

interface NamespaceRepository : JpaRepository<NamespaceEntity, UUID> {

    fun findBySlug(slug: String): Optional<NamespaceEntity>

    fun existsBySlug(slug: String): Boolean

    /**
     * Atomically stamps the first-publish timestamp on a namespace, but **only**
     * if it has never been published before (`first_published_at IS NULL`).
     *
     * This is the race-safe gate behind the `first_plugin_publish` activation
     * telemetry event (DEV-24): two concurrent first publishes both run this
     * `UPDATE ... WHERE first_published_at IS NULL`, but the database row lock
     * serialises them and the `IS NULL` predicate guarantees exactly one returns
     * `1` (the winner) while the other returns `0`. The caller emits the event
     * iff this returns `1`, so it fires at most once per namespace lifetime
     * regardless of concurrency or which publish path (auto-approve upload vs.
     * review approval) reached it first.
     *
     * @return the number of rows updated — `1` on the first publish, `0` thereafter.
     */
    @Modifying
    @Query(
        "UPDATE NamespaceEntity n SET n.firstPublishedAt = :now " +
            "WHERE n.id = :id AND n.firstPublishedAt IS NULL",
    )
    fun markFirstPublishedIfAbsent(@Param("id") id: UUID, @Param("now") now: OffsetDateTime): Int
}
