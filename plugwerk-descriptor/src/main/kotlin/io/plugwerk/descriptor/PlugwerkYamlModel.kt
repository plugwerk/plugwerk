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
package io.plugwerk.descriptor

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
data class PlugwerkYamlRoot(val plugwerk: PlugwerkYamlDescriptor)

@JsonIgnoreProperties(ignoreUnknown = true)
data class PlugwerkYamlDescriptor(
    val id: String? = null,
    val version: String? = null,
    val name: String? = null,
    val description: String? = null,
    val author: String? = null,
    val license: String? = null,
    val requires: PlugwerkYamlRequires? = null,
    val namespace: String? = null,
    val categories: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val icon: String? = null,
    val screenshots: List<String> = emptyList(),
    val homepage: String? = null,
    val repository: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class PlugwerkYamlRequires(
    @JsonProperty("system-version")
    val systemVersion: String? = null,
    @JsonProperty("api-level")
    val apiLevel: Int? = null,
    val plugins: List<PlugwerkYamlPluginDependency> = emptyList(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class PlugwerkYamlPluginDependency(val id: String? = null, val version: String? = null)
