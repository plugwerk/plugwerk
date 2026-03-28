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
    fun `resolve prefers plugwerk yml over manifest`() {
        val yaml = """
            plugwerk:
              id: "yaml-plugin"
              version: "1.0.0"
              name: "YAML Plugin"
        """.trimIndent()
        val manifest = Manifest().apply {
            mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0"
            mainAttributes.putValue("Plugin-Id", "manifest-plugin")
            mainAttributes.putValue("Plugin-Version", "2.0.0")
        }
        val jar = createJarWithManifestAndEntry(manifest, "plugwerk.yml", yaml)

        val descriptor = resolver.resolve(jar)

        assertEquals("yaml-plugin", descriptor.id)
        assertEquals("1.0.0", descriptor.version)
    }

    @Test
    fun `resolve falls back to manifest when no plugwerk yml`() {
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
    fun `resolve finds plugwerk yml inside root-level JAR in ZIP bundle`() {
        val yaml = """
            plugwerk:
              id: "zip-bundle-plugin"
              version: "1.2.3"
              name: "ZIP Bundle Plugin"
        """.trimIndent()
        val innerJar = createJarWithEntry("plugwerk.yml", yaml)
        val zip = createZipWithEntry("my-plugin.jar", innerJar.readBytes())

        val descriptor = resolver.resolve(zip)

        assertEquals("zip-bundle-plugin", descriptor.id)
        assertEquals("1.2.3", descriptor.version)
    }

    @Test
    fun `resolve prefers root-level JAR over lib subdirectory JAR in ZIP bundle`() {
        val rootYaml = """
            plugwerk:
              id: "root-plugin"
              version: "1.0.0"
              name: "Root Plugin"
        """.trimIndent()
        val libYaml = """
            plugwerk:
              id: "lib-plugin"
              version: "2.0.0"
              name: "Lib Plugin"
        """.trimIndent()
        val rootJarBytes = createJarWithEntry("plugwerk.yml", rootYaml).readBytes()
        val libJarBytes = createJarWithEntry("plugwerk.yml", libYaml).readBytes()
        val zip = createZipWithEntries(
            "my-plugin.jar" to rootJarBytes,
            "lib/dependency.jar" to libJarBytes,
        )

        val descriptor = resolver.resolve(zip)

        assertEquals("root-plugin", descriptor.id)
    }

    @Test
    fun `resolve finds descriptor in lib subdirectory JAR when no root-level JAR has descriptor`() {
        val libYaml = """
            plugwerk:
              id: "lib-only-plugin"
              version: "3.0.0"
              name: "Lib Only Plugin"
        """.trimIndent()
        val libJarBytes = createJarWithEntry("plugwerk.yml", libYaml).readBytes()
        val zip = createZipWithEntries(
            "lib/my-plugin.jar" to libJarBytes,
        )

        val descriptor = resolver.resolve(zip)

        assertEquals("lib-only-plugin", descriptor.id)
    }

    @Test
    fun `resolve falls back to manifest in nested JAR inside ZIP bundle`() {
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
    fun `resolve propagates parse error for malformed plugwerk yml`() {
        val badYaml = """
            plugwerk:
              version: "1.0.0"
              name: "No ID"
        """.trimIndent()
        val jar = createJarWithEntry("plugwerk.yml", badYaml)

        assertThrows<DescriptorParseException> {
            resolver.resolve(jar)
        }
    }

    private fun createJarWithManifestAndEntry(
        manifest: Manifest,
        entryName: String,
        content: String,
    ): ByteArrayInputStream {
        val baos = ByteArrayOutputStream()
        JarOutputStream(baos, manifest).use { jar ->
            jar.putNextEntry(JarEntry(entryName))
            jar.write(content.toByteArray())
            jar.closeEntry()
        }
        return ByteArrayInputStream(baos.toByteArray())
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
