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
package io.plugwerk.client.catalog

import io.plugwerk.api.model.PluginDto
import io.plugwerk.api.model.PluginPagedResponse
import io.plugwerk.api.model.PluginReleaseDto
import io.plugwerk.api.model.ReleasePagedResponse
import io.plugwerk.client.PlugwerkClient
import io.plugwerk.client.internal.toReleaseInfo
import io.plugwerk.spi.extension.PlugwerkCatalog
import io.plugwerk.spi.model.PluginInfo
import io.plugwerk.spi.model.PluginReleaseInfo
import io.plugwerk.spi.model.PluginStatus
import io.plugwerk.spi.model.SearchCriteria

/** HTTP-backed implementation of [PlugwerkCatalog]. All requests are scoped to the namespace in [client.config]. */
internal class PlugwerkCatalogImpl(private val client: PlugwerkClient) : PlugwerkCatalog {
    override fun listPlugins(): List<PluginInfo> =
        client.get<PluginPagedResponse>("plugins").content.map { it.toPluginInfo() }

    override fun getPlugin(pluginId: String): PluginInfo? =
        client.getOrNull<PluginDto>("plugins/$pluginId")?.toPluginInfo()

    override fun searchPlugins(criteria: SearchCriteria): List<PluginInfo> {
        val query = buildString {
            criteria.query?.let { append("q=${it.urlEncode()}&") }
            criteria.category?.let { append("category=${it.urlEncode()}&") }
            criteria.tag?.let { append("tag=${it.urlEncode()}&") }
            criteria.compatibleWith?.let { append("compatibleWith=${it.urlEncode()}&") }
        }.trimEnd('&')
        val path = if (query.isBlank()) "plugins" else "plugins?$query"
        return client.get<PluginPagedResponse>(path).content.map { it.toPluginInfo() }
    }

    override fun getPluginReleases(pluginId: String): List<PluginReleaseInfo> =
        client.get<ReleasePagedResponse>("plugins/$pluginId/releases").content.map { it.toReleaseInfo() }

    override fun getPluginRelease(pluginId: String, version: String): PluginReleaseInfo? =
        client.getOrNull<PluginReleaseDto>("plugins/$pluginId/releases/$version")?.toReleaseInfo()

    /** Returns the artifact download URL for the given plugin release. */
    fun downloadUrl(pluginId: String, version: String): String =
        client.url("plugins/$pluginId/releases/$version/download")
}

private fun PluginDto.toPluginInfo(): PluginInfo = PluginInfo(
    pluginId = pluginId,
    name = name,
    description = description,
    author = author,
    license = license,
    namespace = namespace,
    categories = categories ?: emptyList(),
    tags = tags ?: emptyList(),
    latestVersion = latestRelease?.version,
    status = status.toPluginStatus(),
    icon = icon?.toString(),
    homepage = homepage?.toString(),
    repository = repository?.toString(),
)

private fun PluginDto.Status.toPluginStatus(): PluginStatus = when (this) {
    PluginDto.Status.ACTIVE -> PluginStatus.ACTIVE
    PluginDto.Status.SUSPENDED -> PluginStatus.SUSPENDED
    PluginDto.Status.ARCHIVED -> PluginStatus.ARCHIVED
}

private fun String.urlEncode(): String = java.net.URLEncoder.encode(this, Charsets.UTF_8)
