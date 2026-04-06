/*
 * Copyright (c) 2025-present devtank42 GmbH
 *
 * This file is part of Plugwerk.
 *
 * Plugwerk is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Plugwerk is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Plugwerk. If not, see <https://www.gnu.org/licenses/>.
 */
package io.plugwerk.spi

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.writeText

class PlugwerkConfigTest {
    @Test
    fun `builder creates config with all fields`() {
        val config =
            PlugwerkConfig.Builder("https://plugins.example.com", "acme")
                .apiKey("pwk_secret-key")
                .accessToken("tok-secret")
                .connectionTimeoutMs(5_000)
                .readTimeoutMs(15_000)
                .build()

        assertEquals("https://plugins.example.com", config.serverUrl)
        assertEquals("acme", config.namespace)
        assertEquals("pwk_secret-key", config.apiKey)
        assertEquals("tok-secret", config.accessToken)
        assertEquals(5_000, config.connectionTimeoutMs)
        assertEquals(15_000, config.readTimeoutMs)
    }

    @Test
    fun `builder uses defaults for optional fields`() {
        val config = PlugwerkConfig.Builder("https://plugins.example.com", "acme").build()

        assertNull(config.apiKey)
        assertNull(config.accessToken)
        assertNull(config.pluginDirectory)
        assertEquals(PlugwerkConfig.DEFAULT_CONNECTION_TIMEOUT_MS, config.connectionTimeoutMs)
        assertEquals(PlugwerkConfig.DEFAULT_READ_TIMEOUT_MS, config.readTimeoutMs)
    }

    @Test
    fun `toString masks api key and access token`() {
        val config = PlugwerkConfig.Builder("https://plugins.example.com", "acme")
            .apiKey("pwk_secret").accessToken("tok-secret").build()
        val str = config.toString()
        assert("pwk_secret" !in str) { "API key must not appear in toString(): $str" }
        assert("tok-secret" !in str) { "Access token must not appear in toString(): $str" }
        assert(str.contains("apiKey=<set>")) { "toString() must indicate apiKey is set: $str" }
        assert(str.contains("accessToken=<set>")) { "toString() must indicate accessToken is set: $str" }
    }

    @Test
    fun `toString shows none when no credentials`() {
        val config = PlugwerkConfig.Builder("https://plugins.example.com", "acme").build()
        val str = config.toString()
        assert(str.contains("apiKey=<none>")) { "toString() must indicate apiKey is none: $str" }
        assert(str.contains("accessToken=<none>")) { "toString() must indicate accessToken is none: $str" }
    }

    @Test
    fun `fromProperties loads all fields`(@TempDir tempDir: Path) {
        val propsFile = tempDir.resolve("plugwerk-client.properties")
        propsFile.writeText(
            """
            plugwerk.serverUrl=https://example.com
            plugwerk.namespace=myns
            plugwerk.apiKey=pwk_test-key
            plugwerk.accessToken=tok-123
            plugwerk.connectionTimeoutMs=3000
            plugwerk.readTimeoutMs=20000
            """.trimIndent(),
        )

        val config = PlugwerkConfig.fromProperties(propsFile)

        assertEquals("https://example.com", config.serverUrl)
        assertEquals("myns", config.namespace)
        assertEquals("pwk_test-key", config.apiKey)
        assertEquals("tok-123", config.accessToken)
        assertEquals(3_000, config.connectionTimeoutMs)
        assertEquals(20_000, config.readTimeoutMs)
    }

    @Test
    fun `fromProperties works without optional fields`(@TempDir tempDir: Path) {
        val propsFile = tempDir.resolve("plugwerk-client.properties")
        propsFile.writeText(
            """
            plugwerk.serverUrl=https://example.com
            plugwerk.namespace=myns
            """.trimIndent(),
        )

        val config = PlugwerkConfig.fromProperties(propsFile)

        assertNull(config.accessToken)
        assertEquals(PlugwerkConfig.DEFAULT_CONNECTION_TIMEOUT_MS, config.connectionTimeoutMs)
    }

    @Test
    fun `fromProperties throws for missing serverUrl`(@TempDir tempDir: Path) {
        val propsFile = tempDir.resolve("plugwerk-client.properties")
        propsFile.writeText("plugwerk.namespace=myns")

        assertThrows(IllegalArgumentException::class.java) {
            PlugwerkConfig.fromProperties(propsFile)
        }
    }

    @Test
    fun `constructor rejects blank serverUrl`() {
        assertThrows(IllegalArgumentException::class.java) {
            PlugwerkConfig(serverUrl = "", namespace = "acme")
        }
    }

    @Test
    fun `constructor rejects blank namespace`() {
        assertThrows(IllegalArgumentException::class.java) {
            PlugwerkConfig(serverUrl = "https://example.com", namespace = "")
        }
    }

    @Test
    fun `fromProperties loads from input stream`() {
        val props =
            """
            plugwerk.serverUrl=https://stream.example.com
            plugwerk.namespace=streamns
            """.trimIndent().byteInputStream()

        val config = PlugwerkConfig.fromProperties(props)

        assertNotNull(config)
        assertEquals("https://stream.example.com", config.serverUrl)
        assertEquals("streamns", config.namespace)
    }
}
