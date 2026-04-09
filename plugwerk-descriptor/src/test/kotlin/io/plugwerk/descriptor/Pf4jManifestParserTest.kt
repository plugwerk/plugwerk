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
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
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
        assertEquals("ACME GmbH", descriptor.provider)
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
        assertEquals("Test Corp", descriptor.provider)
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
    fun `parse MANIFEST MF with custom Plugin attributes`() {
        val manifest = Manifest().apply {
            mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0"
            mainAttributes.putValue("Plugin-Id", "acme-export")
            mainAttributes.putValue("Plugin-Version", "2.0.0")
            mainAttributes.putValue("Plugin-Name", "ACME Export")
            mainAttributes.putValue("Plugin-Description", "Export plugin")
            mainAttributes.putValue("Plugin-Tags", "pdf, report, template")
            mainAttributes.putValue("Plugin-Icon", "icon.png")
            mainAttributes.putValue("Plugin-Screenshots", "screenshot-1.png, screenshot-2.png")
            mainAttributes.putValue("Plugin-Homepage", "https://example.com")
            mainAttributes.putValue("Plugin-Repository", "https://github.com/acme/export")
        }

        val descriptor = parser.parseManifest(manifest)

        assertEquals("ACME Export", descriptor.name)
        assertEquals("Export plugin", descriptor.description)
        assertEquals(listOf("pdf", "report", "template"), descriptor.tags)
        assertEquals("icon.png", descriptor.icon)
        assertEquals(listOf("screenshot-1.png", "screenshot-2.png"), descriptor.screenshots)
        assertEquals("https://example.com", descriptor.homepage)
        assertEquals("https://github.com/acme/export", descriptor.repository)
    }

    @Test
    fun `Plugin-Name takes precedence over Plugin-Description for name`() {
        val manifest = Manifest().apply {
            mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0"
            mainAttributes.putValue("Plugin-Id", "acme-export")
            mainAttributes.putValue("Plugin-Version", "1.0.0")
            mainAttributes.putValue("Plugin-Name", "Custom Display Name")
            mainAttributes.putValue("Plugin-Description", "Some description")
        }

        val descriptor = parser.parseManifest(manifest)

        assertEquals("Custom Display Name", descriptor.name)
        assertEquals("Some description", descriptor.description)
    }

    @Test
    fun `name falls back to description when Plugin-Name is absent`() {
        val manifest = Manifest().apply {
            mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0"
            mainAttributes.putValue("Plugin-Id", "acme-export")
            mainAttributes.putValue("Plugin-Version", "1.0.0")
            mainAttributes.putValue("Plugin-Description", "Some description")
        }

        val descriptor = parser.parseManifest(manifest)

        assertEquals("Some description", descriptor.name)
    }

    @Test
    fun `name falls back to id when both Plugin-Name and Plugin-Description are absent`() {
        val manifest = Manifest().apply {
            mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0"
            mainAttributes.putValue("Plugin-Id", "acme-export")
            mainAttributes.putValue("Plugin-Version", "1.0.0")
        }

        val descriptor = parser.parseManifest(manifest)

        assertEquals("acme-export", descriptor.name)
    }

    @Test
    fun `comma-separated lists handle whitespace and empty entries`() {
        val manifest = Manifest().apply {
            mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0"
            mainAttributes.putValue("Plugin-Id", "test-plugin")
            mainAttributes.putValue("Plugin-Version", "1.0.0")
            mainAttributes.putValue("Plugin-Tags", " tag1 , , tag2 , tag3 ")
        }

        val descriptor = parser.parseManifest(manifest)

        assertEquals(listOf("tag1", "tag2", "tag3"), descriptor.tags)
    }

    @Test
    fun `parse properties with custom Plugin attributes`() {
        val props = Properties().apply {
            setProperty("plugin.id", "props-plugin")
            setProperty("plugin.version", "1.0.0")
            setProperty("plugin.name", "Props Display Name")
            setProperty("plugin.tags", "cli")
            setProperty("plugin.icon", "icon.svg")
            setProperty("plugin.screenshots", "s1.png")
            setProperty("plugin.homepage", "https://example.com")
            setProperty("plugin.repository", "https://github.com/test/repo")
        }

        val descriptor = parser.parseProperties(props)

        assertEquals("Props Display Name", descriptor.name)
        assertEquals(listOf("cli"), descriptor.tags)
        assertEquals("icon.svg", descriptor.icon)
        assertEquals(listOf("s1.png"), descriptor.screenshots)
        assertEquals("https://example.com", descriptor.homepage)
        assertEquals("https://github.com/test/repo", descriptor.repository)
    }

    // ---- Validation via DescriptorValidator ----

    @Test
    fun `manifest with invalid plugin id throws DescriptorValidationException`() {
        val manifest = Manifest().apply {
            mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0"
            mainAttributes.putValue("Plugin-Id", "../traversal")
            mainAttributes.putValue("Plugin-Version", "1.0.0")
        }
        val ex = assertThrows<DescriptorValidationException> {
            parser.parseManifest(manifest)
        }
        assertTrue(ex.violations.any { it.contains("id") })
    }

    @Test
    fun `manifest with non-SemVer version throws DescriptorValidationException`() {
        val manifest = Manifest().apply {
            mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0"
            mainAttributes.putValue("Plugin-Id", "valid-plugin")
            mainAttributes.putValue("Plugin-Version", "not-a-version")
        }
        val ex = assertThrows<DescriptorValidationException> {
            parser.parseManifest(manifest)
        }
        assertTrue(ex.violations.any { it.contains("version") })
    }

    @Test
    fun `manifest with oversized provider throws DescriptorValidationException`() {
        val manifest = Manifest().apply {
            mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0"
            mainAttributes.putValue("Plugin-Id", "valid-plugin")
            mainAttributes.putValue("Plugin-Version", "1.0.0")
            mainAttributes.putValue("Plugin-Provider", "a".repeat(256))
        }
        val ex = assertThrows<DescriptorValidationException> {
            parser.parseManifest(manifest)
        }
        assertTrue(ex.violations.any { it.contains("provider") })
    }

    @Test
    fun `properties with plugin id exceeding 128 chars throws DescriptorValidationException`() {
        val props = Properties().apply {
            setProperty("plugin.id", "a".repeat(129))
            setProperty("plugin.version", "1.0.0")
        }
        val ex = assertThrows<DescriptorValidationException> {
            parser.parseProperties(props)
        }
        assertTrue(ex.violations.any { it.contains("id") })
    }

    // ---- parseFromJar ----

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
