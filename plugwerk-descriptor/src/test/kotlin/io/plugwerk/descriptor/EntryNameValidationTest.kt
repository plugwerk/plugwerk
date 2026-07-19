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

import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

/**
 * Security tests for [validateEntryName], the Zip-Slip / path-traversal
 * guard applied to every JAR entry inside an uploaded ZIP bundle.
 *
 * The function is the last line of defence before entry bytes are read, so
 * its accept/reject contract is verified directly here rather than only
 * through the higher-level [DescriptorResolver] paths. It rejects two shapes:
 *  - `..` path-traversal segments (escape the extraction root)
 *  - embedded NUL bytes (can truncate paths in native / filesystem calls)
 * Everything else — including spaces — is a legitimate archive entry name.
 */
class EntryNameValidationTest {

    /** A single NUL byte, spelled out to keep it visible in source. */
    private val nul = '\u0000'

    @ParameterizedTest
    @ValueSource(
        strings = [
            "plugin.jar",
            "META-INF/MANIFEST.MF",
            "nested/dir/plugin.properties",
            "io.example.my-plugin-1.0.0.jar",
            "a.jar",
            // Spaces are legitimate in archive entry names and must be accepted;
            // only `..` traversal and NUL bytes are rejected.
            "My Plugin.jar",
        ],
    )
    fun `accepts safe entry names`(name: String) {
        assertDoesNotThrow { validateEntryName(name) }
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "../evil.jar",
            "../../etc/passwd",
            "nested/../../escape.jar",
            "foo/..",
            "..",
        ],
    )
    fun `rejects path-traversal entry names`(name: String) {
        val ex = assertThrows<IllegalArgumentException> { validateEntryName(name) }
        assertEquals("Suspicious JAR entry name: $name", ex.message)
    }

    @Test
    fun `rejects an entry name with an embedded NUL byte`() {
        // A NUL byte can truncate a path in native / filesystem calls, sneaking
        // bytes past extension checks — the guard rejects it outright.
        val name = "plugin$nul.jar"
        val ex = assertThrows<IllegalArgumentException> { validateEntryName(name) }
        assertEquals("Suspicious JAR entry name: $name", ex.message)
    }

    @Test
    fun `rejects a NUL byte at the end of an otherwise safe name`() {
        val name = "META-INF/MANIFEST.MF$nul"
        assertThrows<IllegalArgumentException> { validateEntryName(name) }
    }

    @Test
    fun `rejection message names the offending entry`() {
        val ex = assertThrows<IllegalArgumentException> { validateEntryName("../../secret") }
        assertEquals("Suspicious JAR entry name: ../../secret", ex.message)
    }
}
