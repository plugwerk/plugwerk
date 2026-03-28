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

import tools.jackson.core.JacksonException
import tools.jackson.core.StreamReadConstraints
import tools.jackson.dataformat.yaml.YAMLFactory
import tools.jackson.dataformat.yaml.YAMLMapper
import tools.jackson.module.kotlin.kotlinModule
import java.io.InputStream
import java.util.jar.JarInputStream

class PlugwerkDescriptorParser {

    private val yamlMapper: YAMLMapper = run {
        val factory = YAMLFactory.builder()
            .streamReadConstraints(
                StreamReadConstraints.builder()
                    .maxDocumentLength(512_000L)
                    .maxStringLength(65_536)
                    .maxNestingDepth(20)
                    .maxNumberLength(20)
                    .build(),
            )
            .build() as YAMLFactory
        YAMLMapper.builder(factory)
            .addModule(kotlinModule())
            .build()
    }

    fun parse(inputStream: InputStream): PlugwerkDescriptor {
        val root = try {
            yamlMapper.readValue(inputStream, PlugwerkYamlRoot::class.java)
        } catch (e: JacksonException) {
            throw DescriptorParseException("Failed to parse plugwerk.yml: ${e.message}", e)
        }
        return toDescriptor(root.plugwerk)
    }

    fun parseFromJar(jarStream: InputStream): PlugwerkDescriptor {
        JarInputStream(jarStream).use { jar ->
            var entry = jar.nextJarEntry
            while (entry != null) {
                validateEntryName(entry.name)
                if (entry.name == "plugwerk.yml") {
                    return parse(jar)
                }
                entry = jar.nextJarEntry
            }
        }
        throw DescriptorNotFoundException("No plugwerk.yml found in JAR")
    }

    private fun toDescriptor(yaml: PlugwerkYamlDescriptor): PlugwerkDescriptor {
        val id = yaml.id
            ?: throw DescriptorParseException("Required field 'id' is missing in plugwerk.yml")
        val version = yaml.version
            ?: throw DescriptorParseException("Required field 'version' is missing in plugwerk.yml")
        val name = yaml.name
            ?: throw DescriptorParseException("Required field 'name' is missing in plugwerk.yml")

        val dependencies = yaml.requires?.plugins?.map { dep ->
            PluginDependency(
                id = dep.id ?: throw DescriptorParseException("Plugin dependency missing 'id'"),
                version = dep.version ?: "*",
            )
        } ?: emptyList()

        val descriptor = PlugwerkDescriptor(
            id = id,
            version = version,
            name = name,
            description = yaml.description,
            author = yaml.author,
            license = yaml.license,
            categories = yaml.categories,
            tags = yaml.tags,
            requiresSystemVersion = yaml.requires?.systemVersion,
            requiresApiLevel = yaml.requires?.apiLevel,
            pluginDependencies = dependencies,
            icon = yaml.icon,
            screenshots = yaml.screenshots,
            homepage = yaml.homepage,
            repository = yaml.repository,
        )
        DescriptorValidator.validate(descriptor)
        return descriptor
    }
}

internal fun validateEntryName(name: String) {
    require(!name.contains("..") && !name.contains('\u0000')) {
        "Suspicious JAR entry name: $name"
    }
}
