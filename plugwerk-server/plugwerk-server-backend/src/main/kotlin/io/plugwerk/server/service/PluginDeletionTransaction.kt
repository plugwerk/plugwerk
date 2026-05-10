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
package io.plugwerk.server.service

import io.plugwerk.server.repository.PluginReleaseRepository
import io.plugwerk.server.repository.PluginRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * Phase 1 of `PluginService.delete` (#481): the database side, isolated
 * in its own Spring bean so the `@Transactional` proxy reliably wraps
 * the call from `PluginService`.
 *
 * **Why a separate component:** Spring's transaction interceptor sits
 * on the bean's proxy. Calling a `@Transactional` method via `this.foo()`
 * inside the same class bypasses the proxy and the annotation becomes a
 * no-op. Putting the DB-side logic in its own `@Component` means
 * `PluginService` calls in through the Spring proxy and the transaction
 * boundary is guaranteed.
 *
 * **Pattern:** the method commits the DB mutations and returns the list
 * of artifact keys that should be cleaned up from storage. The caller
 * is responsible for performing the storage cleanup *outside* this
 * transaction — see `PluginService.delete` and the issue body of #481
 * for the full rationale (storage I/O inside `@Transactional` blocks
 * the JDBC connection during a network call, and a storage failure
 * either rolls back already-committed-on-disk deletes or swallows the
 * error and leaves orphans).
 *
 * **Do NOT inject `PluginService` here.** That would create a
 * `PluginService -> PluginDeletionTransaction -> PluginService` cycle
 * and Spring would refuse to start. The dependencies are deliberately
 * limited to the two repositories plus `NamespaceService` for the
 * namespace lookup.
 */
@Component
class PluginDeletionTransaction(
    private val pluginRepository: PluginRepository,
    private val releaseRepository: PluginReleaseRepository,
    private val namespaceService: NamespaceService,
) {

    /**
     * Deletes the plugin and all its releases from the database in a
     * single transaction, returning the artifact keys that the caller
     * must clean up from storage afterwards.
     *
     * @throws PluginNotFoundException when no plugin with [pluginId]
     *   exists in the namespace identified by [namespaceSlug].
     */
    @Transactional
    fun deleteFromDb(namespaceSlug: String, pluginId: String): List<String> {
        val namespace = namespaceService.findBySlug(namespaceSlug)
        val plugin = pluginRepository.findByNamespaceAndPluginId(namespace, pluginId)
            .orElseThrow { PluginNotFoundException(namespaceSlug, pluginId) }
        val releases = releaseRepository.findAllByPluginOrderByCreatedAtDesc(plugin)
        val artifactKeys = releases.map { it.artifactKey }
        releases.forEach { releaseRepository.delete(it) }
        pluginRepository.delete(plugin)
        return artifactKeys
    }
}
