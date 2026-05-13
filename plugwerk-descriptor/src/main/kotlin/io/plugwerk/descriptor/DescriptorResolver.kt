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

/**
 * Reads a [PlugwerkDescriptor] from an arbitrary upload stream.
 *
 * The PF4J ecosystem ships plugins in two shapes:
 *
 *  - a single fat-JAR with everything in the JAR's root
 *  - a ZIP bundle that contains the plugin JAR plus its `lib/`
 *    dependencies
 *
 * The server cannot tell which shape was uploaded without reading the
 * stream, so the resolver tries both:
 *
 *  1. Treat the input as a JAR and ask [Pf4jManifestParser] to find a
 *     `MANIFEST.MF` (or `plugin.properties`) at the JAR root.
 *  2. If that fails, treat the input as a ZIP bundle. Iterate every
 *     entry, validate the entry name to defeat Zip-Slip, and try to
 *     parse the root-level JAR first, then any nested `lib/` JAR.
 *
 * The stream is fully buffered into memory (PF4J descriptors are tiny
 * and the upload endpoint already caps file size), so the resolver is
 * thread-safe and re-entrant across uploads.
 *
 * The resolver only throws [DescriptorNotFoundException] when neither
 * source yielded a descriptor — malformed manifests bubble up as
 * [DescriptorParseException], and unsafe ZIP entry names trigger an
 * [IllegalArgumentException] from [validateEntryName] so the upload
 * controller can return precise 4xx messages.
 *
 * @property manifestParser Pluggable parser, exposed for tests. The
 *   default constructor is the right choice for production.
 */
class DescriptorResolver(private val manifestParser: Pf4jManifestParser = Pf4jManifestParser()) {

    /**
     * Resolve a descriptor from an uploaded plugin stream.
     *
     * @param jarStream Caller-owned input stream. The resolver fully
     *   consumes it; the caller is responsible for closing the
     *   original source.
     * @return Parsed descriptor with all required fields populated.
     * @throws DescriptorNotFoundException No `MANIFEST.MF` /
     *   `plugin.properties` could be located in the JAR or in any
     *   bundled JAR inside the ZIP.
     * @throws DescriptorParseException Manifest was found but
     *   contained invalid attributes.
     * @throws IllegalArgumentException ZIP bundle contained an entry
     *   name that would escape the extraction root (Zip-Slip defence
     *   via [validateEntryName]).
     */
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
