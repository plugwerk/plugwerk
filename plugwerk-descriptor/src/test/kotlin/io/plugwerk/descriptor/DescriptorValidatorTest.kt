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
        description: String? = null,
        provider: String? = null,
        license: String? = null,
        homepage: String? = null,
        repository: String? = null,
        icon: String? = null,
        tags: List<String> = emptyList(),
        screenshots: List<String> = emptyList(),
        requiresSystemVersion: String? = null,
        pluginDependencies: List<PluginDependency> = emptyList(),
    ) = PlugwerkDescriptor(
        id = id,
        version = version,
        name = name,
        description = description,
        provider = provider,
        license = license,
        homepage = homepage,
        repository = repository,
        icon = icon,
        tags = tags,
        screenshots = screenshots,
        requiresSystemVersion = requiresSystemVersion,
        pluginDependencies = pluginDependencies,
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

    // ---- version ----

    @ParameterizedTest
    @ValueSource(strings = ["1.0.0", "0.1.0", "10.20.30", "1.0.0-beta.1", "1.0.0+build.42"])
    fun `valid SemVer versions pass`(version: String) {
        assertDoesNotThrow { DescriptorValidator.validate(minimalDescriptor(version = version)) }
    }

    @ParameterizedTest
    @ValueSource(strings = ["not-a-version", "1.0", "1", "latest", "v1.0.0.0"])
    fun `invalid SemVer versions throw`(version: String) {
        val ex = assertThrows<DescriptorValidationException> {
            DescriptorValidator.validate(minimalDescriptor(version = version))
        }
        assertTrue(ex.violations.any { it.contains("version") })
    }

    @Test
    fun `version exceeding max length throws`() {
        val longVersion = "1.0.0-${"a".repeat(DescriptorValidator.VERSION_MAX_LENGTH)}"
        val ex = assertThrows<DescriptorValidationException> {
            DescriptorValidator.validate(minimalDescriptor(version = longVersion))
        }
        assertTrue(ex.violations.any { it.contains("version") })
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

    // ---- description ----

    @Test
    fun `description at max length passes`() {
        assertDoesNotThrow {
            DescriptorValidator.validate(
                minimalDescriptor(description = "a".repeat(DescriptorValidator.DESCRIPTION_MAX_LENGTH)),
            )
        }
    }

    @Test
    fun `description exceeding max length throws`() {
        val ex = assertThrows<DescriptorValidationException> {
            DescriptorValidator.validate(
                minimalDescriptor(description = "a".repeat(DescriptorValidator.DESCRIPTION_MAX_LENGTH + 1)),
            )
        }
        assertTrue(ex.violations.any { it.contains("description") })
    }

    // ---- provider ----

    @Test
    fun `provider at max length passes`() {
        assertDoesNotThrow {
            DescriptorValidator.validate(
                minimalDescriptor(provider = "a".repeat(DescriptorValidator.PROVIDER_MAX_LENGTH)),
            )
        }
    }

    @Test
    fun `provider exceeding max length throws`() {
        val ex = assertThrows<DescriptorValidationException> {
            DescriptorValidator.validate(
                minimalDescriptor(provider = "a".repeat(DescriptorValidator.PROVIDER_MAX_LENGTH + 1)),
            )
        }
        assertTrue(ex.violations.any { it.contains("provider") })
    }

    // ---- license ----

    @Test
    fun `license at max length passes`() {
        assertDoesNotThrow {
            DescriptorValidator.validate(
                minimalDescriptor(license = "a".repeat(DescriptorValidator.LICENSE_MAX_LENGTH)),
            )
        }
    }

    @Test
    fun `license exceeding max length throws`() {
        val ex = assertThrows<DescriptorValidationException> {
            DescriptorValidator.validate(
                minimalDescriptor(license = "a".repeat(DescriptorValidator.LICENSE_MAX_LENGTH + 1)),
            )
        }
        assertTrue(ex.violations.any { it.contains("license") })
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
    fun `homepage exceeding max URL length throws`() {
        val longUrl = "https://example.com/${"a".repeat(DescriptorValidator.URL_MAX_LENGTH)}"
        val ex = assertThrows<DescriptorValidationException> {
            DescriptorValidator.validate(minimalDescriptor(homepage = longUrl))
        }
        assertTrue(ex.violations.any { it.contains("homepage") })
    }

    @Test
    fun `repository exceeding max URL length throws`() {
        val longUrl = "https://example.com/${"a".repeat(DescriptorValidator.URL_MAX_LENGTH)}"
        val ex = assertThrows<DescriptorValidationException> {
            DescriptorValidator.validate(minimalDescriptor(repository = longUrl))
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

    @Test
    fun `icon exceeding max URL length throws`() {
        val longIcon = "icons/${"a".repeat(DescriptorValidator.URL_MAX_LENGTH)}.png"
        val ex = assertThrows<DescriptorValidationException> {
            DescriptorValidator.validate(minimalDescriptor(icon = longIcon))
        }
        assertTrue(ex.violations.any { it.contains("icon") })
    }

    // ---- tags ----

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
    fun `tags exceeding max count throws`() {
        val tooMany = (1..DescriptorValidator.MAX_TAGS + 1).map { "tag-$it" }
        val ex = assertThrows<DescriptorValidationException> {
            DescriptorValidator.validate(minimalDescriptor(tags = tooMany))
        }
        assertTrue(ex.violations.any { it.contains("tags") && it.contains("exceed") })
    }

    // ---- screenshots ----

    @Test
    fun `valid screenshots pass`() {
        assertDoesNotThrow {
            DescriptorValidator.validate(
                minimalDescriptor(screenshots = listOf("screenshot.png", "https://example.com/screen.png")),
            )
        }
    }

    @Test
    fun `screenshot exceeding max URL length throws`() {
        val longUrl = "https://example.com/${"a".repeat(DescriptorValidator.URL_MAX_LENGTH)}.png"
        val ex = assertThrows<DescriptorValidationException> {
            DescriptorValidator.validate(minimalDescriptor(screenshots = listOf(longUrl)))
        }
        assertTrue(ex.violations.any { it.contains("screenshots") })
    }

    @Test
    fun `screenshot with invalid URL scheme throws`() {
        val ex = assertThrows<DescriptorValidationException> {
            DescriptorValidator.validate(minimalDescriptor(screenshots = listOf("https://")))
        }
        assertTrue(ex.violations.any { it.contains("screenshots") })
    }

    @Test
    fun `screenshots exceeding max count throws`() {
        val tooMany = (1..DescriptorValidator.MAX_SCREENSHOTS + 1).map { "screen-$it.png" }
        val ex = assertThrows<DescriptorValidationException> {
            DescriptorValidator.validate(minimalDescriptor(screenshots = tooMany))
        }
        assertTrue(ex.violations.any { it.contains("screenshots") && it.contains("exceed") })
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

    @Test
    fun `requiresSystemVersion exceeding max length throws`() {
        val longRange = ">=1.0.0 ${"& >=1.0.0 ".repeat(50)}"
        val ex = assertThrows<DescriptorValidationException> {
            DescriptorValidator.validate(minimalDescriptor(requiresSystemVersion = longRange))
        }
        assertTrue(ex.violations.any { it.contains("requiresSystemVersion") })
    }

    // ---- pluginDependencies ----

    @Test
    fun `valid plugin dependencies pass`() {
        assertDoesNotThrow {
            DescriptorValidator.validate(
                minimalDescriptor(
                    pluginDependencies = listOf(PluginDependency("acme-dep", ">=1.0.0")),
                ),
            )
        }
    }

    @Test
    fun `dependency with invalid id format throws`() {
        val ex = assertThrows<DescriptorValidationException> {
            DescriptorValidator.validate(
                minimalDescriptor(
                    pluginDependencies = listOf(PluginDependency("../traversal", ">=1.0.0")),
                ),
            )
        }
        assertTrue(ex.violations.any { it.contains("pluginDependencies[0].id") })
    }

    @Test
    fun `dependency with oversized version throws`() {
        val ex = assertThrows<DescriptorValidationException> {
            DescriptorValidator.validate(
                minimalDescriptor(
                    pluginDependencies = listOf(
                        PluginDependency(
                            "valid-dep",
                            "a".repeat(DescriptorValidator.DEPENDENCY_VERSION_MAX_LENGTH + 1),
                        ),
                    ),
                ),
            )
        }
        assertTrue(ex.violations.any { it.contains("pluginDependencies[0].version") })
    }

    @Test
    fun `dependencies exceeding max count throws`() {
        val tooMany = (1..DescriptorValidator.MAX_DEPENDENCIES + 1).map { PluginDependency("dep-$it", "*") }
        val ex = assertThrows<DescriptorValidationException> {
            DescriptorValidator.validate(minimalDescriptor(pluginDependencies = tooMany))
        }
        assertTrue(ex.violations.any { it.contains("pluginDependencies") && it.contains("exceed") })
    }

    // ---- HTML / script injection ----

    @ParameterizedTest
    @ValueSource(strings = ["<script>alert(1)</script>", "<iframe src=x>", "<object data=x>", "javascript:alert(1)"])
    fun `name with HTML or script injection throws`(name: String) {
        val ex = assertThrows<DescriptorValidationException> {
            DescriptorValidator.validate(minimalDescriptor(name = name))
        }
        assertTrue(ex.violations.any { it.contains("HTML") || it.contains("script") })
    }

    @Test
    fun `description with script tag throws`() {
        val ex = assertThrows<DescriptorValidationException> {
            DescriptorValidator.validate(minimalDescriptor(description = "Hello <script>alert(1)</script>"))
        }
        assertTrue(ex.violations.any { it.contains("description") && it.contains("HTML") })
    }

    @Test
    fun `provider with iframe tag throws`() {
        val ex = assertThrows<DescriptorValidationException> {
            DescriptorValidator.validate(minimalDescriptor(provider = "<iframe src=evil>"))
        }
        assertTrue(ex.violations.any { it.contains("provider") && it.contains("HTML") })
    }

    @Test
    fun `legitimate less-than in description passes`() {
        assertDoesNotThrow {
            DescriptorValidator.validate(minimalDescriptor(description = "values < 10 are ignored"))
        }
    }

    @Test
    fun `description with harmless angle brackets passes`() {
        assertDoesNotThrow {
            DescriptorValidator.validate(minimalDescriptor(description = "Use <T> for generics"))
        }
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
