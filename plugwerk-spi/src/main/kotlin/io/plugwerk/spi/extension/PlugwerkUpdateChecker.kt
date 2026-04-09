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
package io.plugwerk.spi.extension

import io.plugwerk.spi.model.UpdateInfo
import org.pf4j.ExtensionPoint

/**
 * Extension point for detecting available updates for installed plugins.
 *
 * Implement this interface to compare the set of locally installed plugins against
 * the versions published on the Plugwerk server and report which ones have a newer
 * release available.
 *
 * Typical usage in a host application:
 *
 * Kotlin:
 * ```kotlin
 * val checker = pluginManager.getExtensions(PlugwerkUpdateChecker::class.java).first()
 * val installed = pluginManager.plugins.associate { it.pluginId to it.descriptor.version }
 * val updates = checker.checkForUpdates(installed)
 * updates.forEach { println("Update available: ${it.pluginId} ${it.currentVersion} → ${it.availableVersion}") }
 * ```
 *
 * Java:
 * ```java
 * PlugwerkUpdateChecker checker = pluginManager.getExtensions(PlugwerkUpdateChecker.class).get(0);
 * Map<String, String> installed = pluginManager.getPlugins().stream()
 *     .collect(Collectors.toMap(p -> p.getPluginId(), p -> p.getDescriptor().getVersion()));
 * List<UpdateInfo> updates = checker.checkForUpdates(installed);
 * updates.forEach(u -> System.out.println(
 *     "Update available: " + u.getPluginId() + " " + u.getCurrentVersion() + " → " + u.getAvailableVersion()));
 * ```
 *
 * @see PlugwerkMarketplace for a unified facade
 */
interface PlugwerkUpdateChecker : ExtensionPoint {
    /**
     * Checks whether newer published releases exist for any of the given installed plugins.
     *
     * Only [ReleaseStatus.PUBLISHED][io.plugwerk.spi.model.ReleaseStatus.PUBLISHED] releases
     * with a higher SemVer than the installed version are reported.
     * Plugins in [installedPlugins] that are unknown to the server are silently ignored.
     *
     * @param installedPlugins map of plugin ID → currently installed SemVer version string
     *   (e.g. `mapOf("io.example.my-plugin" to "1.0.0")`)
     * @return list of [UpdateInfo] entries, one per plugin that has a newer version available;
     *   empty list if everything is up-to-date
     */
    fun checkForUpdates(installedPlugins: Map<String, String>): List<UpdateInfo>

    /**
     * Returns all updates available in the marketplace regardless of what is locally installed.
     *
     * **Not yet implemented** — Phase 2 will add a dedicated server endpoint for this.
     * Use [checkForUpdates] with a map of currently installed plugin IDs and versions instead.
     *
     * @throws UnsupportedOperationException always, until Phase 2 is implemented
     */
    fun getAvailableUpdates(): List<UpdateInfo> = throw UnsupportedOperationException(
        "getAvailableUpdates is not yet implemented — use checkForUpdates(installedPlugins) instead",
    )
}
