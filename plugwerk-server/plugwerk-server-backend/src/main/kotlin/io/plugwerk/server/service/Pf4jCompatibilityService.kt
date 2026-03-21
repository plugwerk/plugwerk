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

import io.plugwerk.api.model.Pf4jPluginInfo
import io.plugwerk.api.model.Pf4jPluginsJson
import io.plugwerk.api.model.Pf4jReleaseInfo
import io.plugwerk.common.model.PluginStatus
import io.plugwerk.common.model.ReleaseStatus
import io.plugwerk.server.PlugwerkProperties
import io.plugwerk.server.repository.NamespaceRepository
import io.plugwerk.server.repository.PluginReleaseRepository
import io.plugwerk.server.repository.PluginRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.net.URI

@Service
@Transactional(readOnly = true)
class Pf4jCompatibilityService(
    private val namespaceRepository: NamespaceRepository,
    private val pluginRepository: PluginRepository,
    private val releaseRepository: PluginReleaseRepository,
    private val properties: PlugwerkProperties,
) {

    fun buildPluginsJson(namespaceSlug: String): Pf4jPluginsJson {
        val namespace = namespaceRepository.findBySlug(namespaceSlug)
            .orElseThrow { NamespaceNotFoundException(namespaceSlug) }

        val pf4jPlugins = pluginRepository.findAllByNamespaceAndStatus(namespace, PluginStatus.ACTIVE)
            .map { plugin ->
                val releases = releaseRepository.findAllByPluginAndStatus(plugin, ReleaseStatus.PUBLISHED)
                    .map { release ->
                        Pf4jReleaseInfo(
                            version = release.version,
                            url = URI(
                                "${properties.server.baseUrl}/api/v1/namespaces/$namespaceSlug/plugins/${plugin.pluginId}/releases/${release.version}/download",
                            ),
                            date = release.createdAt.toLocalDate(),
                            requires = release.requiresSystemVersion,
                        )
                    }
                Pf4jPluginInfo(
                    id = plugin.pluginId,
                    description = plugin.description,
                    provider = plugin.author,
                    projectUrl = plugin.homepage,
                    releases = releases,
                )
            }

        return Pf4jPluginsJson(plugins = pf4jPlugins)
    }
}
