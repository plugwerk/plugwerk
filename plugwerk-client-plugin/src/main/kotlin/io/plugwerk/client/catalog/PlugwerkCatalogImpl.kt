/*
 * Copyright (c) 2025-present devtank42 GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
    provider = provider,
    license = license,
    namespace = namespace,
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
