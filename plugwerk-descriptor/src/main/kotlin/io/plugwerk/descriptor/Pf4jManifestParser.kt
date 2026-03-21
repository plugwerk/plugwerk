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

        return PlugwerkDescriptor(
            id = id,
            version = version,
            name = description ?: id,
            description = description,
            author = provider,
            license = license,
            requiresSystemVersion = requires,
            pluginDependencies = dependencies,
        )
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

        return PlugwerkDescriptor(
            id = id,
            version = version,
            name = description ?: id,
            description = description,
            author = provider,
            license = license,
            requiresSystemVersion = requires,
            pluginDependencies = dependencies,
        )
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
}
