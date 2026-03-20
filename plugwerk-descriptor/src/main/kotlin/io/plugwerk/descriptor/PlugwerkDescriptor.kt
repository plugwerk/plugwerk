package io.plugwerk.descriptor

/**
 * Represents a parsed plugwerk.yml descriptor.
 * Full implementation with parser and validator will be added in Milestone 2 (T-2.4).
 */
data class PlugwerkDescriptor(
    val id: String,
    val version: String,
    val name: String,
    val description: String? = null,
    val author: String? = null,
    val license: String? = null,
    val namespace: String? = null,
    val categories: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val requiresSystemVersion: String? = null,
    val requiresApiLevel: Int? = null,
    val pluginDependencies: List<PluginDependency> = emptyList(),
)

data class PluginDependency(val id: String, val version: String)
