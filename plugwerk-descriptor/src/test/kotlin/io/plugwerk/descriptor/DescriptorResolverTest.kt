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
}
