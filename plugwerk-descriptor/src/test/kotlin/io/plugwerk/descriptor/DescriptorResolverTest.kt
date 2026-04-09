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

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.jar.Attributes
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import java.util.jar.Manifest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class DescriptorResolverTest {

    private val resolver = DescriptorResolver()

    @Test
    fun `resolve reads from MANIFEST MF`() {
        val manifest = Manifest().apply {
            mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0"
            mainAttributes.putValue("Plugin-Id", "manifest-plugin")
            mainAttributes.putValue("Plugin-Version", "3.0.0")
        }
        val jar = createJarWithManifest(manifest)

        val descriptor = resolver.resolve(jar)

        assertEquals("manifest-plugin", descriptor.id)
        assertEquals("3.0.0", descriptor.version)
    }

    @Test
    fun `resolve falls back to plugin properties`() {
        val props = "plugin.id=props-plugin\nplugin.version=4.0.0\n"
        val jar = createJarWithEntry("plugin.properties", props)

        val descriptor = resolver.resolve(jar)

        assertEquals("props-plugin", descriptor.id)
        assertEquals("4.0.0", descriptor.version)
    }

    @Test
    fun `resolve throws when no descriptor found`() {
        val jar = createJarWithEntry("some-file.txt", "content")

        assertThrows<DescriptorNotFoundException> {
            resolver.resolve(jar)
        }
    }

    // --- ZIP bundle tests ---

    @Test
    fun `resolve finds manifest inside root-level JAR in ZIP bundle`() {
        val manifest = Manifest().apply {
            mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0"
            mainAttributes.putValue("Plugin-Id", "zip-bundle-plugin")
            mainAttributes.putValue("Plugin-Version", "1.2.3")
        }
        val innerJar = createJarWithManifest(manifest)
        val zip = createZipWithEntry("my-plugin.jar", innerJar.readBytes())

        val descriptor = resolver.resolve(zip)

        assertEquals("zip-bundle-plugin", descriptor.id)
        assertEquals("1.2.3", descriptor.version)
    }

    @Test
    fun `resolve prefers root-level JAR over lib subdirectory JAR in ZIP bundle`() {
        val rootManifest = Manifest().apply {
            mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0"
            mainAttributes.putValue("Plugin-Id", "root-plugin")
            mainAttributes.putValue("Plugin-Version", "1.0.0")
        }
        val libManifest = Manifest().apply {
            mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0"
            mainAttributes.putValue("Plugin-Id", "lib-plugin")
            mainAttributes.putValue("Plugin-Version", "2.0.0")
        }
        val rootJarBytes = createJarWithManifest(rootManifest).readBytes()
        val libJarBytes = createJarWithManifest(libManifest).readBytes()
        val zip = createZipWithEntries(
            "my-plugin.jar" to rootJarBytes,
            "lib/dependency.jar" to libJarBytes,
        )

        val descriptor = resolver.resolve(zip)

        assertEquals("root-plugin", descriptor.id)
    }

    @Test
    fun `resolve finds descriptor in lib subdirectory JAR when no root-level JAR has descriptor`() {
        val libManifest = Manifest().apply {
            mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0"
            mainAttributes.putValue("Plugin-Id", "lib-only-plugin")
            mainAttributes.putValue("Plugin-Version", "3.0.0")
        }
        val libJarBytes = createJarWithManifest(libManifest).readBytes()
        val zip = createZipWithEntries(
            "lib/my-plugin.jar" to libJarBytes,
        )

        val descriptor = resolver.resolve(zip)

        assertEquals("lib-only-plugin", descriptor.id)
    }

    @Test
    fun `resolve finds manifest in nested JAR inside ZIP bundle`() {
        val manifest = Manifest().apply {
            mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0"
            mainAttributes.putValue("Plugin-Id", "manifest-in-zip")
            mainAttributes.putValue("Plugin-Version", "5.0.0")
        }
        val innerJarBytes = createJarWithManifest(manifest).readBytes()
        val zip = createZipWithEntry("plugin.jar", innerJarBytes)

        val descriptor = resolver.resolve(zip)

        assertEquals("manifest-in-zip", descriptor.id)
        assertEquals("5.0.0", descriptor.version)
    }

    @Test
    fun `resolve throws when ZIP bundle contains no JAR with descriptor`() {
        val innerJarBytes = createJarWithEntry("some-file.txt", "content").readBytes()
        val zip = createZipWithEntry("plugin.jar", innerJarBytes)

        assertThrows<DescriptorNotFoundException> {
            resolver.resolve(zip)
        }
    }

    @Test
    fun `resolve throws when ZIP bundle contains no JAR entries at all`() {
        val zip = createZipWithEntry("readme.txt", "no jars here".toByteArray())

        assertThrows<DescriptorNotFoundException> {
            resolver.resolve(zip)
        }
    }

    @Test
    fun `resolve propagates validation error for invalid plugin id in manifest`() {
        val manifest = Manifest().apply {
            mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0"
            mainAttributes.putValue("Plugin-Id", "../traversal")
            mainAttributes.putValue("Plugin-Version", "1.0.0")
        }
        val jar = createJarWithManifest(manifest)

        assertThrows<DescriptorValidationException> {
            resolver.resolve(jar)
        }
    }

    @Test
    fun `resolve reads custom Plugin attributes from MANIFEST MF`() {
        val manifest = Manifest().apply {
            mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0"
            mainAttributes.putValue("Plugin-Id", "full-plugin")
            mainAttributes.putValue("Plugin-Version", "1.0.0")
            mainAttributes.putValue("Plugin-Name", "Full Plugin")
            mainAttributes.putValue("Plugin-Provider", "ACME")
            mainAttributes.putValue("Plugin-Tags", "pdf, csv")
            mainAttributes.putValue("Plugin-Homepage", "https://example.com")
        }
        val jar = createJarWithManifest(manifest)

        val descriptor = resolver.resolve(jar)

        assertEquals("Full Plugin", descriptor.name)
        assertEquals("ACME", descriptor.provider)
        assertEquals(listOf("pdf", "csv"), descriptor.tags)
        assertEquals("https://example.com", descriptor.homepage)
    }

    private fun createJarWithManifest(manifest: Manifest): ByteArrayInputStream {
        val baos = ByteArrayOutputStream()
        JarOutputStream(baos, manifest).use { jar ->
            jar.putNextEntry(JarEntry("dummy.txt"))
            jar.write("dummy".toByteArray())
            jar.closeEntry()
        }
        return ByteArrayInputStream(baos.toByteArray())
    }

    private fun createJarWithEntry(entryName: String, content: String): ByteArrayInputStream {
        val baos = ByteArrayOutputStream()
        JarOutputStream(baos).use { jar ->
            jar.putNextEntry(JarEntry(entryName))
            jar.write(content.toByteArray())
            jar.closeEntry()
        }
        return ByteArrayInputStream(baos.toByteArray())
    }

    private fun createZipWithEntry(entryName: String, content: ByteArray): ByteArrayInputStream {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zip ->
            zip.putNextEntry(ZipEntry(entryName))
            zip.write(content)
            zip.closeEntry()
        }
        return ByteArrayInputStream(baos.toByteArray())
    }

    private fun createZipWithEntries(vararg entries: Pair<String, ByteArray>): ByteArrayInputStream {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zip ->
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content)
                zip.closeEntry()
            }
        }
        return ByteArrayInputStream(baos.toByteArray())
    }
}
