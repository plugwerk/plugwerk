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
package io.plugwerk.spi.model

/**
 * Compact reference to a single plugin currently installed on the host. Used
 * as the input shape for
 * [io.plugwerk.spi.extension.PlugwerkUpdateChecker.checkForUpdates] (#504),
 * replacing the previous weakly-typed `Map<String, String>` parameter so the
 * compiler — not a comment — guarantees `pluginId` and `version` cannot be
 * accidentally swapped at the call site.
 *
 * Carries only what the update checker needs: the plugin id and the version
 * string currently installed on the host. Everything else about the plugin
 * (display name, license, description, …) lives in [PluginInfo] and is the
 * job of [io.plugwerk.spi.extension.PlugwerkCatalog].
 *
 * Kotlin:
 * ```kotlin
 * val installed = pluginManager.plugins.map {
 *     InstalledPluginRef(it.pluginId, it.descriptor.version)
 * }
 * val updates = checker.checkForUpdates(installed)
 * ```
 *
 * Java:
 * ```java
 * List<InstalledPluginRef> installed = pluginManager.getPlugins().stream()
 *     .map(p -> new InstalledPluginRef(p.getPluginId(), p.getDescriptor().getVersion()))
 *     .toList();
 * List<UpdateInfo> updates = checker.checkForUpdates(installed);
 * ```
 *
 * @property pluginId unique identifier of the plugin within its namespace
 *   (typically reverse-domain notation, e.g. `"io.example.my-plugin"`)
 * @property version  SemVer string of the version currently installed on the
 *   host (e.g. `"1.0.0"`). The class name encodes the "currently installed"
 *   semantic, so the field reads `installed.version`, not `currentVersion`.
 */
data class InstalledPluginRef(val pluginId: String, val version: String)
