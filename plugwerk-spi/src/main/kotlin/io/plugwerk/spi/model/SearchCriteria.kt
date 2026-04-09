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
 * Filter criteria for [io.plugwerk.spi.extension.PlugwerkCatalog.searchPlugins].
 *
 * All fields are optional. When multiple fields are set they are combined with AND semantics —
 * only plugins that satisfy **all** non-null criteria are returned.
 * An instance with all fields `null` is equivalent to calling
 * [io.plugwerk.spi.extension.PlugwerkCatalog.listPlugins].
 *
 * Example — find plugins tagged "experimental" compatible with host version 3.1.0:
 *
 * Kotlin:
 * ```kotlin
 * val criteria = SearchCriteria(tag = "experimental", compatibleWith = "3.1.0")
 * val results = catalog.searchPlugins(criteria)
 * ```
 *
 * Java:
 * ```java
 * SearchCriteria criteria = new SearchCriteria(null, "experimental", "3.1.0");
 * List<PluginInfo> results = catalog.searchPlugins(criteria);
 * ```
 *
 * @property query          free-text search string matched against plugin ID, name, description,
 *   and tags; `null` disables full-text filtering
 * @property tag            exact tag to filter by (e.g. `"experimental"`);
 *   `null` disables tag filtering
 * @property compatibleWith SemVer version string of the host application; when set, only releases
 *   whose `requiresSystemVersion` range includes this version are returned;
 *   `null` disables compatibility filtering
 */
data class SearchCriteria(val query: String? = null, val tag: String? = null, val compatibleWith: String? = null)
