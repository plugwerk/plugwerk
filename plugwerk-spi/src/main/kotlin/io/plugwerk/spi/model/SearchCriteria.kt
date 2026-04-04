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
