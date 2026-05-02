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
import org.slf4j.LoggerFactory

/** HTTP-backed implementation of [PlugwerkCatalog]. All requests are scoped to the namespace in [client.config]. */
internal class PlugwerkCatalogImpl(private val client: PlugwerkClient) : PlugwerkCatalog {
    private val log = LoggerFactory.getLogger(PlugwerkCatalogImpl::class.java)

    override fun listPlugins(): List<PluginInfo> = paginate(
        fetchPage = { p -> client.get<PluginPagedResponse>("plugins?page=$p") },
        contentOf = { it.content },
        totalPagesOf = { it.totalPages },
    ).map { it.toPluginInfo() }

    override fun getPlugin(pluginId: String): PluginInfo? =
        client.getOrNull<PluginDto>("plugins/$pluginId")?.toPluginInfo()

    override fun searchPlugins(criteria: SearchCriteria): List<PluginInfo> {
        val query = buildString {
            criteria.query?.let { append("q=${it.urlEncode()}&") }
            criteria.tag?.let { append("tag=${it.urlEncode()}&") }
            criteria.compatibleWith?.let { append("compatibleWith=${it.urlEncode()}&") }
        }.trimEnd('&')
        return paginate(
            fetchPage = { p -> client.get<PluginPagedResponse>(pageUrl("plugins", query, p)) },
            contentOf = { it.content },
            totalPagesOf = { it.totalPages },
        ).map { it.toPluginInfo() }
    }

    override fun getPluginReleases(pluginId: String): List<PluginReleaseInfo> = paginate(
        fetchPage = { p -> client.get<ReleasePagedResponse>("plugins/$pluginId/releases?page=$p") },
        contentOf = { it.content },
        totalPagesOf = { it.totalPages },
    ).map { it.toReleaseInfo() }

    override fun getPluginRelease(pluginId: String, version: String): PluginReleaseInfo? =
        client.getOrNull<PluginReleaseDto>("plugins/$pluginId/releases/$version")?.toReleaseInfo()

    /** Returns the artifact download URL for the given plugin release. */
    fun downloadUrl(pluginId: String, version: String): String =
        client.url("plugins/$pluginId/releases/$version/download")

    /**
     * Fetches every page of a paginated server response and concatenates the
     * `content` lists. Closes #428 — the pre-fix code only consumed `.content`
     * of page 0 and silently dropped everything else.
     *
     * Capped at [MAX_PAGES] iterations as a defence against a server that
     * returns inconsistent `totalPages` (would otherwise loop forever). At the
     * server's default page size of 20 the cap covers up to 2000 items per
     * call — far beyond any realistic catalog size today. If the cap is hit
     * we log a warning and return what we have so far rather than throwing,
     * because the alternative (throwing) would brick existing callers.
     */
    private inline fun <P, T> paginate(
        fetchPage: (Int) -> P,
        contentOf: (P) -> List<T>,
        totalPagesOf: (P) -> Int,
    ): List<T> {
        val all = mutableListOf<T>()
        var page = 0
        var totalPages: Int
        do {
            val response = fetchPage(page)
            all.addAll(contentOf(response))
            totalPages = totalPagesOf(response)
            page++
            if (page >= MAX_PAGES && page < totalPages) {
                log.warn(
                    "Paginated fetch hit the {}-page safety cap (server reported totalPages={}); " +
                        "returning {} items collected so far. If this is legitimate, paginate explicitly.",
                    MAX_PAGES,
                    totalPages,
                    all.size,
                )
                break
            }
        } while (page < totalPages)
        return all
    }

    private fun pageUrl(path: String, query: String, page: Int): String = when {
        query.isBlank() -> "$path?page=$page"
        else -> "$path?$query&page=$page"
    }

    private companion object {
        /** Defence against a misbehaving server returning inconsistent `totalPages`. */
        const val MAX_PAGES = 100
    }
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
