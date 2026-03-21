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
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.Properties
import java.util.jar.Attributes
import java.util.jar.Manifest

class Pf4jManifestParserTest {

    private val parser = Pf4jManifestParser()

    @Test
    fun `parse MANIFEST MF with PF4J attributes`() {
        val manifest = Manifest().apply {
            mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0"
            mainAttributes.putValue("Plugin-Id", "acme-export")
            mainAttributes.putValue("Plugin-Version", "2.0.0")
            mainAttributes.putValue("Plugin-Description", "Export plugin")
            mainAttributes.putValue("Plugin-Provider", "ACME GmbH")
            mainAttributes.putValue("Plugin-Requires", ">=1.0.0")
            mainAttributes.putValue("Plugin-License", "MIT")
        }

        val descriptor = parser.parseManifest(manifest)

        assertEquals("acme-export", descriptor.id)
        assertEquals("2.0.0", descriptor.version)
        assertEquals("Export plugin", descriptor.name)
        assertEquals("Export plugin", descriptor.description)
        assertEquals("ACME GmbH", descriptor.author)
        assertEquals("MIT", descriptor.license)
        assertEquals(">=1.0.0", descriptor.requiresSystemVersion)
    }

    @Test
    fun `parse MANIFEST MF with plugin dependencies`() {
        val manifest = Manifest().apply {
            mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0"
            mainAttributes.putValue("Plugin-Id", "acme-export")
            mainAttributes.putValue("Plugin-Version", "1.0.0")
            mainAttributes.putValue("Plugin-Dependencies", "plugin-a@>=1.0.0,plugin-b@>=2.0.0")
        }

        val descriptor = parser.parseManifest(manifest)

        assertEquals(2, descriptor.pluginDependencies.size)
        assertEquals("plugin-a", descriptor.pluginDependencies[0].id)
        assertEquals(">=1.0.0", descriptor.pluginDependencies[0].version)
        assertEquals("plugin-b", descriptor.pluginDependencies[1].id)
        assertEquals(">=2.0.0", descriptor.pluginDependencies[1].version)
    }

    @Test
    fun `parse MANIFEST MF without version for dependency`() {
        val manifest = Manifest().apply {
            mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0"
            mainAttributes.putValue("Plugin-Id", "acme-export")
            mainAttributes.putValue("Plugin-Version", "1.0.0")
            mainAttributes.putValue("Plugin-Dependencies", "plugin-a")
        }

        val descriptor = parser.parseManifest(manifest)

        assertEquals(1, descriptor.pluginDependencies.size)
        assertEquals("plugin-a", descriptor.pluginDependencies[0].id)
        assertEquals("*", descriptor.pluginDependencies[0].version)
    }

    @Test
    fun `parse MANIFEST MF throws on missing Plugin-Id`() {
        val manifest = Manifest().apply {
            mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0"
            mainAttributes.putValue("Plugin-Version", "1.0.0")
        }

        assertThrows<DescriptorParseException> {
            parser.parseManifest(manifest)
        }
    }

    @Test
    fun `parse plugin properties`() {
        val props = Properties().apply {
            setProperty("plugin.id", "props-plugin")
            setProperty("plugin.version", "3.0.0")
            setProperty("plugin.description", "A properties plugin")
            setProperty("plugin.provider", "Test Corp")
            setProperty("plugin.requires", ">=2.0.0")
            setProperty("plugin.license", "Apache-2.0")
            setProperty("plugin.dependencies", "dep-a@>=1.0.0")
        }

        val descriptor = parser.parseProperties(props)

        assertEquals("props-plugin", descriptor.id)
        assertEquals("3.0.0", descriptor.version)
        assertEquals("A properties plugin", descriptor.name)
        assertEquals("Test Corp", descriptor.author)
        assertEquals(">=2.0.0", descriptor.requiresSystemVersion)
        assertEquals("Apache-2.0", descriptor.license)
        assertEquals(1, descriptor.pluginDependencies.size)
    }

    @Test
    fun `parse plugin properties throws on missing id`() {
        val props = Properties().apply {
            setProperty("plugin.version", "1.0.0")
        }

        assertThrows<DescriptorParseException> {
            parser.parseProperties(props)
        }
    }

    @Test
    fun `parse from JAR with MANIFEST`() {
        val manifest = Manifest().apply {
            mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0"
            mainAttributes.putValue("Plugin-Id", "jar-plugin")
            mainAttributes.putValue("Plugin-Version", "1.0.0")
        }
        val jarStream = createTestJarWithManifest(manifest)

        val descriptor = parser.parseFromJar(jarStream)

        assertEquals("jar-plugin", descriptor.id)
        assertEquals("1.0.0", descriptor.version)
    }

    @Test
    fun `parse from JAR with plugin properties`() {
        val props = "plugin.id=props-jar\nplugin.version=2.0.0\n"
        val jarStream = createTestJarWithEntry("plugin.properties", props)

        val descriptor = parser.parseFromJar(jarStream)

        assertEquals("props-jar", descriptor.id)
        assertEquals("2.0.0", descriptor.version)
    }

    @Test
    fun `parse from JAR without any descriptor throws`() {
        val baos = java.io.ByteArrayOutputStream()
        java.util.jar.JarOutputStream(baos).use { jar ->
            jar.putNextEntry(java.util.jar.JarEntry("some-file.txt"))
            jar.write("content".toByteArray())
            jar.closeEntry()
        }
        val jarStream = java.io.ByteArrayInputStream(baos.toByteArray())

        assertThrows<DescriptorNotFoundException> {
            parser.parseFromJar(jarStream)
        }
    }

    private fun createTestJarWithManifest(manifest: Manifest): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream()
        java.util.jar.JarOutputStream(baos, manifest).use { jar ->
            jar.putNextEntry(java.util.jar.JarEntry("dummy.txt"))
            jar.write("dummy".toByteArray())
            jar.closeEntry()
        }
        return java.io.ByteArrayInputStream(baos.toByteArray())
    }

    private fun createTestJarWithEntry(entryName: String, content: String): java.io.InputStream {
        val baos = java.io.ByteArrayOutputStream()
        java.util.jar.JarOutputStream(baos).use { jar ->
            jar.putNextEntry(java.util.jar.JarEntry(entryName))
            jar.write(content.toByteArray())
            jar.closeEntry()
        }
        return java.io.ByteArrayInputStream(baos.toByteArray())
    }
}
