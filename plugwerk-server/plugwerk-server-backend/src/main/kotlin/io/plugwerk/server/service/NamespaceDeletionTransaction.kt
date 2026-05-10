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

import io.plugwerk.server.repository.NamespaceRepository
import io.plugwerk.server.repository.PluginReleaseRepository
import io.plugwerk.server.repository.PluginRepository
import io.plugwerk.server.service.settings.UserSettingsService
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * Phase 1 of `NamespaceService.delete` (#481): the database side, isolated
 * in its own Spring bean so the `@Transactional` proxy reliably wraps the
 * call from `NamespaceService`. See `PluginDeletionTransaction` for the
 * detailed rationale on the two-phase pattern and the self-invocation
 * trap that this split avoids.
 *
 * Returns the artifact keys that should be cleaned up from storage; the
 * caller is responsible for performing the cleanup *outside* this
 * transaction. The DB cascade on `NamespaceEntity` (verified in
 * `NamespaceServiceIntegrationTest.delete cascades to plugins, releases,
 * members, access keys, and storage`) handles the row removal — only
 * `userSettings.clearDefaultNamespace` is invoked explicitly because
 * user-settings live in a separate aggregate.
 */
@Component
class NamespaceDeletionTransaction(
    private val namespaceRepository: NamespaceRepository,
    private val pluginRepository: PluginRepository,
    private val pluginReleaseRepository: PluginReleaseRepository,
    private val userSettingsService: UserSettingsService,
) {

    /**
     * Deletes the namespace from the database in a single transaction
     * and returns the artifact keys that the caller must clean up from
     * storage afterwards.
     *
     * @throws NamespaceNotFoundException when no namespace with [slug] exists.
     */
    @Transactional
    fun deleteFromDb(slug: String): List<String> {
        val namespace = namespaceRepository.findBySlug(slug)
            .orElseThrow { NamespaceNotFoundException(slug) }
        val plugins = pluginRepository.findAllByNamespace(namespace)
        val artifactKeys = if (plugins.isEmpty()) {
            emptyList()
        } else {
            pluginReleaseRepository.findAllByPluginInOrderByCreatedAtDesc(plugins)
                .map { it.artifactKey }
        }
        userSettingsService.clearDefaultNamespace(slug)
        namespaceRepository.delete(namespace)
        return artifactKeys
    }
}
