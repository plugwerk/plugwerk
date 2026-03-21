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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream

class PlugwerkDescriptorParserTest {

    private val parser = PlugwerkDescriptorParser()

    @Test
    fun `parse full descriptor from YAML`() {
        val input = loadResource("plugwerk-valid.yml")
        val descriptor = parser.parse(input)

        assertEquals("acme-pdf-export", descriptor.id)
        assertEquals("1.2.0", descriptor.version)
        assertEquals("PDF Export Plugin", descriptor.name)
        assertEquals("Exports reports as PDF with configurable templates.", descriptor.description)
        assertEquals("ACME GmbH", descriptor.author)
        assertEquals("Apache-2.0", descriptor.license)
        assertEquals(">=2.0.0 <4.0.0", descriptor.requiresSystemVersion)
        assertEquals(3, descriptor.requiresApiLevel)
        assertEquals("acme-crm", descriptor.namespace)
        assertEquals(listOf("export", "reporting"), descriptor.categories)
        assertEquals(listOf("pdf", "report", "template"), descriptor.tags)
        assertEquals("icon.png", descriptor.icon)
        assertEquals(listOf("screenshot-config.png", "screenshot-output.png"), descriptor.screenshots)
        assertEquals("https://plugins.acme.com/pdf-export", descriptor.homepage)
        assertEquals("https://github.com/acme/pdf-export-plugin", descriptor.repository)
        assertEquals(1, descriptor.pluginDependencies.size)
        assertEquals("acme-template-engine", descriptor.pluginDependencies[0].id)
        assertEquals(">=1.0.0", descriptor.pluginDependencies[0].version)
    }

    @Test
    fun `parse minimal descriptor`() {
        val input = loadResource("plugwerk-minimal.yml")
        val descriptor = parser.parse(input)

        assertEquals("simple-plugin", descriptor.id)
        assertEquals("0.1.0", descriptor.version)
        assertEquals("Simple Plugin", descriptor.name)
        assertNull(descriptor.description)
        assertNull(descriptor.author)
        assertNull(descriptor.license)
        assertNull(descriptor.requiresSystemVersion)
        assertNull(descriptor.requiresApiLevel)
        assertNull(descriptor.namespace)
        assertTrue(descriptor.categories.isEmpty())
        assertTrue(descriptor.tags.isEmpty())
        assertTrue(descriptor.pluginDependencies.isEmpty())
    }

    @Test
    fun `parse throws on missing id`() {
        val input = loadResource("plugwerk-missing-id.yml")
        val ex = assertThrows<DescriptorParseException> {
            parser.parse(input)
        }
        assertTrue(ex.message!!.contains("id"), "Error should mention 'id': ${ex.message}")
    }

    @Test
    fun `parse throws on invalid version`() {
        val input = loadResource("plugwerk-bad-version.yml")
        val ex = assertThrows<DescriptorParseException> {
            parser.parse(input)
        }
        assertTrue(ex.message!!.contains("version"), "Error should mention 'version': ${ex.message}")
    }

    @Test
    fun `parse from JAR with plugwerk yml`() {
        val jarStream = createTestJar(
            "plugwerk.yml" to loadResourceAsString("plugwerk-valid.yml"),
        )
        val descriptor = parser.parseFromJar(jarStream)

        assertEquals("acme-pdf-export", descriptor.id)
        assertEquals("1.2.0", descriptor.version)
    }

    @Test
    fun `parse from JAR without plugwerk yml throws`() {
        val jarStream = createTestJar("some-file.txt" to "content")
        assertThrows<DescriptorNotFoundException> {
            parser.parseFromJar(jarStream)
        }
    }

    @Test
    fun `unknown YAML fields are ignored`() {
        val yaml = """
            plugwerk:
              id: "test-plugin"
              version: "1.0.0"
              name: "Test"
              unknown-field: "should be ignored"
              another: 42
        """.trimIndent()
        val descriptor = parser.parse(yaml.byteInputStream())
        assertEquals("test-plugin", descriptor.id)
    }

    private fun loadResource(name: String) = javaClass.classLoader.getResourceAsStream(name)
        ?: throw IllegalStateException("Test resource not found: $name")

    private fun loadResourceAsString(name: String) = loadResource(name).bufferedReader().readText()

    private fun createTestJar(vararg entries: Pair<String, String>): InputStream {
        val baos = ByteArrayOutputStream()
        JarOutputStream(baos).use { jar ->
            for ((name, content) in entries) {
                jar.putNextEntry(JarEntry(name))
                jar.write(content.toByteArray())
                jar.closeEntry()
            }
        }
        return ByteArrayInputStream(baos.toByteArray())
    }
}
