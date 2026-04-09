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
package io.plugwerk.client.updater

import io.plugwerk.api.model.InstalledPluginInfo
import io.plugwerk.api.model.PluginUpdateInfo
import io.plugwerk.api.model.UpdateCheckRequest
import io.plugwerk.api.model.UpdateCheckResponse
import io.plugwerk.client.PlugwerkClient
import io.plugwerk.client.internal.toReleaseInfo
import io.plugwerk.spi.extension.PlugwerkUpdateChecker
import io.plugwerk.spi.model.UpdateInfo

/** HTTP-backed implementation of [PlugwerkUpdateChecker]. Calls the server's `/updates/check` endpoint. */
internal class PlugwerkUpdateCheckerImpl(private val client: PlugwerkClient) : PlugwerkUpdateChecker {
    override fun checkForUpdates(installedPlugins: Map<String, String>): List<UpdateInfo> {
        if (installedPlugins.isEmpty()) return emptyList()
        val requestBody =
            UpdateCheckRequest(
                plugins = installedPlugins.map { (id, version) -> InstalledPluginInfo(id, version) },
            )
        return client.post<UpdateCheckResponse>("updates/check", requestBody).updates.map { it.toUpdateInfo() }
    }
}

private fun PluginUpdateInfo.toUpdateInfo(): UpdateInfo = UpdateInfo(
    pluginId = pluginId,
    currentVersion = currentVersion,
    availableVersion = latestVersion,
    release = release.toReleaseInfo(),
)
