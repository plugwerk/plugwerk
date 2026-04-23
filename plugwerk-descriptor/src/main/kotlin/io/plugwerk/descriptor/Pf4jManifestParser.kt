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

import java.io.InputStream
import java.util.Properties
import java.util.jar.JarInputStream
import java.util.jar.Manifest

class Pf4jManifestParser {

    fun parseManifest(manifest: Manifest): PlugwerkDescriptor = buildDescriptor(
        source = "MANIFEST.MF",
        keys = MANIFEST_KEYS,
        lookup = { key -> manifest.mainAttributes.getValue(key) },
    )

    fun parseProperties(properties: Properties): PlugwerkDescriptor = buildDescriptor(
        source = "plugin.properties",
        keys = PROPERTIES_KEYS,
        lookup = { key -> properties.getProperty(key) },
    )

    fun parseFromJar(jarStream: InputStream): PlugwerkDescriptor {
        JarInputStream(jarStream).use { jar ->
            val manifest = jar.manifest
            if (manifest != null && manifest.mainAttributes.getValue("Plugin-Id") != null) {
                return parseManifest(manifest)
            }

            var entry = jar.nextJarEntry
            while (entry != null) {
                validateEntryName(entry.name)
                if (entry.name == "plugin.properties") {
                    val props = Properties()
                    props.load(jar)
                    return parseProperties(props)
                }
                entry = jar.nextJarEntry
            }
        }
        throw DescriptorNotFoundException(
            "No PF4J descriptor found in JAR (neither MANIFEST.MF with Plugin-Id nor plugin.properties)",
        )
    }

    /**
     * Shared extraction + construction path for MANIFEST.MF and plugin.properties.
     *
     * Both descriptor sources expose the same 13 logical fields under different key strings
     * (`Plugin-Id` vs. `plugin.id` etc.). Before KT-022 / #288 these two key sets lived in
     * twin 35-line methods that each rebuilt the same [PlugwerkDescriptor] constructor call.
     * This single factory reads the fields through a caller-supplied [lookup] function,
     * consults the caller-supplied [keys], and runs [DescriptorValidator.validate] exactly
     * once — adding a new descriptor field now touches one struct ([DescriptorKeys]), the
     * two key tables, this method, and the `PlugwerkDescriptor` constructor, all in this
     * file.
     */
    private fun buildDescriptor(
        source: String,
        keys: DescriptorKeys,
        lookup: (String) -> String?,
    ): PlugwerkDescriptor {
        val id = lookup(keys.id)
            ?: throw DescriptorParseException("$source missing required '${keys.id}'")
        val version = lookup(keys.version)
            ?: throw DescriptorParseException("$source missing required '${keys.version}'")
        val description = lookup(keys.description)
        val provider = lookup(keys.provider)
        val requires = lookup(keys.requires)
        val license = lookup(keys.license)
        val dependencies = parseDependencyString(lookup(keys.dependencies))
        val name = lookup(keys.name) ?: description ?: id
        val tags = parseCommaSeparatedList(lookup(keys.tags))
        val icon = lookup(keys.icon)
        val screenshots = parseCommaSeparatedList(lookup(keys.screenshots))
        val homepage = lookup(keys.homepage)
        val repository = lookup(keys.repository)

        val descriptor = PlugwerkDescriptor(
            id = id,
            version = version,
            name = name,
            description = description,
            provider = provider,
            license = license,
            tags = tags,
            requiresSystemVersion = requires,
            pluginDependencies = dependencies,
            icon = icon,
            screenshots = screenshots,
            homepage = homepage,
            repository = repository,
        )
        DescriptorValidator.validate(descriptor)
        return descriptor
    }

    /**
     * Descriptor key names — one instance per descriptor source (MANIFEST.MF vs.
     * plugin.properties). Grouped so [buildDescriptor] can treat the two sources uniformly.
     */
    private data class DescriptorKeys(
        val id: String,
        val version: String,
        val description: String,
        val provider: String,
        val requires: String,
        val license: String,
        val dependencies: String,
        val name: String,
        val tags: String,
        val icon: String,
        val screenshots: String,
        val homepage: String,
        val repository: String,
    )

    private fun parseDependencyString(deps: String?): List<PluginDependency> {
        if (deps.isNullOrBlank()) return emptyList()
        return deps.split(",").map { it.trim() }.filter { it.isNotEmpty() }.map { entry ->
            val parts = entry.split("@", limit = 2)
            PluginDependency(
                id = parts[0].trim(),
                version = if (parts.size > 1) parts[1].trim() else "*",
            )
        }
    }

    private fun parseCommaSeparatedList(value: String?): List<String> {
        if (value.isNullOrBlank()) return emptyList()
        return value.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }

    companion object {
        private val MANIFEST_KEYS = DescriptorKeys(
            id = "Plugin-Id",
            version = "Plugin-Version",
            description = "Plugin-Description",
            provider = "Plugin-Provider",
            requires = "Plugin-Requires",
            license = "Plugin-License",
            dependencies = "Plugin-Dependencies",
            name = "Plugin-Name",
            tags = "Plugin-Tags",
            icon = "Plugin-Icon",
            screenshots = "Plugin-Screenshots",
            homepage = "Plugin-Homepage",
            repository = "Plugin-Repository",
        )

        private val PROPERTIES_KEYS = DescriptorKeys(
            id = "plugin.id",
            version = "plugin.version",
            description = "plugin.description",
            provider = "plugin.provider",
            requires = "plugin.requires",
            license = "plugin.license",
            dependencies = "plugin.dependencies",
            name = "plugin.name",
            tags = "plugin.tags",
            icon = "plugin.icon",
            screenshots = "plugin.screenshots",
            homepage = "plugin.homepage",
            repository = "plugin.repository",
        )
    }
}
