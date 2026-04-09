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

    fun parseManifest(manifest: Manifest): PlugwerkDescriptor {
        val attrs = manifest.mainAttributes
        val id = attrs.getValue("Plugin-Id")
            ?: throw DescriptorParseException("MANIFEST.MF missing required 'Plugin-Id' attribute")
        val version = attrs.getValue("Plugin-Version")
            ?: throw DescriptorParseException("MANIFEST.MF missing required 'Plugin-Version' attribute")
        val description = attrs.getValue("Plugin-Description")
        val provider = attrs.getValue("Plugin-Provider")
        val requires = attrs.getValue("Plugin-Requires")
        val license = attrs.getValue("Plugin-License")
        val dependencies = parseDependencyString(attrs.getValue("Plugin-Dependencies"))
        val name = attrs.getValue("Plugin-Name") ?: description ?: id
        val tags = parseCommaSeparatedList(attrs.getValue("Plugin-Tags"))
        val icon = attrs.getValue("Plugin-Icon")
        val screenshots = parseCommaSeparatedList(attrs.getValue("Plugin-Screenshots"))
        val homepage = attrs.getValue("Plugin-Homepage")
        val repository = attrs.getValue("Plugin-Repository")

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

    fun parseProperties(properties: Properties): PlugwerkDescriptor {
        val id = properties.getProperty("plugin.id")
            ?: throw DescriptorParseException("plugin.properties missing required 'plugin.id'")
        val version = properties.getProperty("plugin.version")
            ?: throw DescriptorParseException("plugin.properties missing required 'plugin.version'")
        val description = properties.getProperty("plugin.description")
        val provider = properties.getProperty("plugin.provider")
        val requires = properties.getProperty("plugin.requires")
        val license = properties.getProperty("plugin.license")
        val dependencies = parseDependencyString(properties.getProperty("plugin.dependencies"))
        val name = properties.getProperty("plugin.name") ?: description ?: id
        val tags = parseCommaSeparatedList(properties.getProperty("plugin.tags"))
        val icon = properties.getProperty("plugin.icon")
        val screenshots = parseCommaSeparatedList(properties.getProperty("plugin.screenshots"))
        val homepage = properties.getProperty("plugin.homepage")
        val repository = properties.getProperty("plugin.repository")

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
}
