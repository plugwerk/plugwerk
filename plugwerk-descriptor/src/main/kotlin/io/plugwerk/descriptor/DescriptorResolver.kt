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

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

class DescriptorResolver(private val manifestParser: Pf4jManifestParser = Pf4jManifestParser()) {

    fun resolve(jarStream: InputStream): PlugwerkDescriptor {
        val bytes = jarStream.readAllBytes()

        return tryParse { manifestParser.parseFromJar(ByteArrayInputStream(bytes)) }
            ?: tryParse { resolveFromZipBundle(bytes) }
            ?: throw DescriptorNotFoundException(
                "No descriptor found in artifact (tried MANIFEST.MF, plugin.properties, ZIP bundle)",
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
            val descriptor = tryParse { manifestParser.parseFromJar(ByteArrayInputStream(jarBytes)) }
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
