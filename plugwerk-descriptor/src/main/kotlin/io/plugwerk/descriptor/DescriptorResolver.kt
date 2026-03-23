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

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

class DescriptorResolver(
    private val plugwerkParser: PlugwerkDescriptorParser = PlugwerkDescriptorParser(),
    private val manifestParser: Pf4jManifestParser = Pf4jManifestParser(),
) {

    fun resolve(jarStream: InputStream): PlugwerkDescriptor {
        val bytes = jarStream.readAllBytes()

        return tryParse { plugwerkParser.parseFromJar(ByteArrayInputStream(bytes)) }
            ?: tryParse { manifestParser.parseFromJar(ByteArrayInputStream(bytes)) }
            ?: tryParse { resolveFromZipBundle(bytes) }
            ?: throw DescriptorNotFoundException(
                "No descriptor found in artifact (tried plugwerk.yml, MANIFEST.MF, plugin.properties, ZIP bundle)",
            )
    }

    private fun resolveFromZipBundle(bytes: ByteArray): PlugwerkDescriptor {
        val rootJars = mutableListOf<ByteArray>()
        val libJars = mutableListOf<ByteArray>()

        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory && entry.name.endsWith(".jar")) {
                    validateEntryName(entry.name)
                    val jarBytes = zip.readBytes()
                    if ('/' !in entry.name) {
                        rootJars += jarBytes
                    } else {
                        libJars += jarBytes
                    }
                }
                entry = zip.nextEntry
            }
        }

        for (jarBytes in rootJars + libJars) {
            val descriptor = tryParse { plugwerkParser.parseFromJar(ByteArrayInputStream(jarBytes)) }
                ?: tryParse { manifestParser.parseFromJar(ByteArrayInputStream(jarBytes)) }
            if (descriptor != null) return descriptor
        }

        throw DescriptorNotFoundException("No descriptor found in any JAR inside ZIP bundle")
    }

    private fun tryParse(block: () -> PlugwerkDescriptor): PlugwerkDescriptor? = try {
        block()
    } catch (_: DescriptorNotFoundException) {
        null
    }
}
