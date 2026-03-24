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

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class DescriptorValidatorTest {

    private fun minimalDescriptor(
        id: String = "my-plugin",
        name: String = "My Plugin",
        version: String = "1.0.0",
        homepage: String? = null,
        repository: String? = null,
        icon: String? = null,
        categories: List<String> = emptyList(),
        tags: List<String> = emptyList(),
        requiresSystemVersion: String? = null,
    ) = PlugwerkDescriptor(
        id = id,
        version = version,
        name = name,
        homepage = homepage,
        repository = repository,
        icon = icon,
        categories = categories,
        tags = tags,
        requiresSystemVersion = requiresSystemVersion,
    )

    @Test
    fun `valid descriptor passes without exception`() {
        assertDoesNotThrow {
            DescriptorValidator.validate(minimalDescriptor())
        }
    }

    // ---- id ----

    @ParameterizedTest
    @ValueSource(strings = ["my-plugin", "acme.pdf_export", "A1B2", "a-b-c.d_e"])
    fun `valid plugin ids pass`(id: String) {
        assertDoesNotThrow { DescriptorValidator.validate(minimalDescriptor(id = id)) }
    }

    @Test
    fun `plugin id at max length of 128 passes`() {
        assertDoesNotThrow { DescriptorValidator.validate(minimalDescriptor(id = "a".repeat(128))) }
    }

    @ParameterizedTest
    @ValueSource(strings = ["has space", "../traversal", "plugin/slash"])
    fun `invalid plugin ids throw DescriptorValidationException`(id: String) {
        val ex = assertThrows<DescriptorValidationException> {
            DescriptorValidator.validate(minimalDescriptor(id = id))
        }
        assertTrue(ex.violations.any { it.contains("id") })
    }

    @Test
    fun `plugin id exceeding 128 chars throws`() {
        val ex = assertThrows<DescriptorValidationException> {
            DescriptorValidator.validate(minimalDescriptor(id = "a".repeat(129)))
        }
        assertTrue(ex.violations.any { it.contains("id") })
    }

    // ---- name ----

    @Test
    fun `name at max length of 255 passes`() {
        assertDoesNotThrow {
            DescriptorValidator.validate(minimalDescriptor(name = "a".repeat(255)))
        }
    }

    @Test
    fun `name exceeding max length throws`() {
        val ex = assertThrows<DescriptorValidationException> {
            DescriptorValidator.validate(minimalDescriptor(name = "a".repeat(256)))
        }
        assertTrue(ex.violations.any { it.contains("name") })
    }

    // ---- URL fields ----

    @ParameterizedTest
    @ValueSource(strings = ["https://example.com", "http://example.com/path?q=1"])
    fun `valid URLs pass`(url: String) {
        assertDoesNotThrow {
            DescriptorValidator.validate(minimalDescriptor(homepage = url, repository = url))
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["ftp://example.com", "not-a-url", "javascript:alert(1)"])
    fun `invalid homepage URL throws`(url: String) {
        val ex = assertThrows<DescriptorValidationException> {
            DescriptorValidator.validate(minimalDescriptor(homepage = url))
        }
        assertTrue(ex.violations.any { it.contains("homepage") })
    }

    @ParameterizedTest
    @ValueSource(strings = ["ftp://example.com", "not-a-url"])
    fun `invalid repository URL throws`(url: String) {
        val ex = assertThrows<DescriptorValidationException> {
            DescriptorValidator.validate(minimalDescriptor(repository = url))
        }
        assertTrue(ex.violations.any { it.contains("repository") })
    }

    @Test
    fun `icon as relative path passes without URL validation`() {
        assertDoesNotThrow {
            DescriptorValidator.validate(minimalDescriptor(icon = "icons/plugin.png"))
        }
    }

    @Test
    fun `icon as valid https URL passes`() {
        assertDoesNotThrow {
            DescriptorValidator.validate(minimalDescriptor(icon = "https://example.com/icon.png"))
        }
    }

    @Test
    fun `icon as invalid https URL throws`() {
        val ex = assertThrows<DescriptorValidationException> {
            DescriptorValidator.validate(minimalDescriptor(icon = "https://"))
        }
        assertTrue(ex.violations.any { it.contains("icon") })
    }

    // ---- categories / tags ----

    @Test
    fun `tag at max length of 64 passes`() {
        assertDoesNotThrow {
            DescriptorValidator.validate(minimalDescriptor(tags = listOf("a".repeat(64))))
        }
    }

    @Test
    fun `tag exceeding max length throws`() {
        val ex = assertThrows<DescriptorValidationException> {
            DescriptorValidator.validate(minimalDescriptor(tags = listOf("a".repeat(65))))
        }
        assertTrue(ex.violations.any { it.contains("tags") })
    }

    @Test
    fun `category exceeding max length throws`() {
        val ex = assertThrows<DescriptorValidationException> {
            DescriptorValidator.validate(minimalDescriptor(categories = listOf("a".repeat(65))))
        }
        assertTrue(ex.violations.any { it.contains("categories") })
    }

    // ---- requiresSystemVersion ----

    @ParameterizedTest
    @ValueSource(strings = [">=1.0.0", ">=2.0.0 <4.0.0", ">=2.0.0 & <4.0.0", "1.0.0 - 2.0.0"])
    fun `valid SemVer ranges pass`(range: String) {
        assertDoesNotThrow {
            DescriptorValidator.validate(minimalDescriptor(requiresSystemVersion = range))
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["not-a-range", ">> 1.0.0", "latest"])
    fun `invalid SemVer ranges throw`(range: String) {
        val ex = assertThrows<DescriptorValidationException> {
            DescriptorValidator.validate(minimalDescriptor(requiresSystemVersion = range))
        }
        assertTrue(ex.violations.any { it.contains("requiresSystemVersion") })
    }

    // ---- multiple violations reported at once ----

    @Test
    fun `multiple violations are reported together`() {
        val ex = assertThrows<DescriptorValidationException> {
            DescriptorValidator.validate(
                minimalDescriptor(
                    id = "../bad",
                    name = "a".repeat(256),
                    homepage = "ftp://bad",
                ),
            )
        }
        assertTrue(ex.violations.size >= 3)
    }
}
