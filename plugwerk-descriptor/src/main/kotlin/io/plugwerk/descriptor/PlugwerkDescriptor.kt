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
package io.plugwerk.descriptor

/**
 * Parsed metadata for a single PF4J plugin artefact (JAR or ZIP).
 *
 * The descriptor is produced by [DescriptorResolver] from one of the
 * two sources the PF4J ecosystem accepts:
 *
 *  - the JAR's `META-INF/MANIFEST.MF` (`Plugin-*` attributes)
 *  - a `plugin.properties` file at the JAR root
 *
 * Both sources map to the same 13 logical fields. Required-vs-optional
 * follows the PF4J convention: [id] (`Plugin-Id`), [version]
 * (`Plugin-Version`), and [name] (`Plugin-Name`) are mandatory;
 * everything else is optional and absent fields are represented as
 * `null` / `emptyList()`.
 *
 * Plugin authors do not construct this class directly — they declare
 * the values in their `MANIFEST.MF` and let Plugwerk read them.
 *
 * @property id Stable plugin identifier (`Plugin-Id`). Used as the
 *   primary key for releases, dependency references, and update
 *   checks. Convention: reverse-DNS, e.g. `io.example.my-plugin`.
 * @property version Semantic version (`Plugin-Version`). Releases of
 *   the same plugin compete on this field — clients only see the
 *   `LATEST` row per (namespace, id).
 * @property name Human-readable display name (`Plugin-Name`).
 * @property description Long-form description (`Plugin-Description`).
 * @property provider Author or organisation (`Plugin-Provider`).
 * @property license SPDX identifier (`Plugin-License`) — e.g. `MIT`,
 *   `Apache-2.0`. Rendered as a chip in the catalog.
 * @property tags Comma-separated tags from the manifest, split into a
 *   list (`Plugin-Tags`). Used for catalog filtering.
 * @property requiresSystemVersion SemVer range the host application
 *   must satisfy for the plugin to load (`Plugin-Requires`).
 *   Evaluated by the update checker.
 * @property pluginDependencies Other plugins this plugin needs at
 *   runtime, parsed from `Plugin-Dependencies`.
 * @property icon Icon URL or path relative to the JAR
 *   (`Plugin-Icon`).
 * @property screenshots Comma-separated screenshot URLs
 *   (`Plugin-Screenshots`).
 * @property homepage Project homepage URL (`Plugin-Homepage`).
 * @property repository Source-repository URL (`Plugin-Repository`).
 */
data class PlugwerkDescriptor(
    val id: String,
    val version: String,
    val name: String,
    val description: String? = null,
    val provider: String? = null,
    val license: String? = null,
    val tags: List<String> = emptyList(),
    val requiresSystemVersion: String? = null,
    val pluginDependencies: List<PluginDependency> = emptyList(),
    val icon: String? = null,
    val screenshots: List<String> = emptyList(),
    val homepage: String? = null,
    val repository: String? = null,
)

/**
 * One plugin-to-plugin dependency parsed from the comma-separated
 * `Plugin-Dependencies` manifest attribute.
 *
 * The version part is a SemVer range string in the format the update
 * checker accepts (`1.2.3`, `>=1.0`, `[1.0,2.0)`); range validation
 * lives in `DescriptorValidator` and the update-check service, not
 * in this data class.
 *
 * @property id Target plugin's `Plugin-Id`.
 * @property version SemVer range expression for compatible versions.
 */
data class PluginDependency(val id: String, val version: String)
