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
package io.plugwerk.client

import io.plugwerk.spi.PlugwerkConfig
import io.plugwerk.spi.PlugwerkPlugin
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class PlugwerkPluginImplTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var plugin: PlugwerkPluginImpl

    private fun config(serverUrl: String = "http://localhost:8080", namespace: String = "acme") =
        PlugwerkConfig.Builder(serverUrl, namespace)
            .pluginDirectory(tempDir)
            .build()

    @BeforeEach
    fun setUp() {
        plugin = PlugwerkPluginImpl()
    }

    @Test
    fun `configure and marketplace work for default server`() {
        plugin.configure(config())

        val marketplace = plugin.marketplace()

        assertNotSame(null, marketplace)
    }

    @Test
    fun `marketplace returns same instance on repeated calls`() {
        plugin.configure(config())

        val first = plugin.marketplace()
        val second = plugin.marketplace()

        assertSame(first, second)
    }

    @Test
    fun `configure multiple servers and retrieve each`() {
        plugin.configure("server-a", config("http://server-a:8080"))
        plugin.configure("server-b", config("http://server-b:8080"))

        val a = plugin.marketplace("server-a")
        val b = plugin.marketplace("server-b")

        assertNotSame(a, b)
    }

    @Test
    fun `marketplace throws when server ID not configured`() {
        val ex = assertThrows(IllegalStateException::class.java) {
            plugin.marketplace("unknown")
        }
        assertTrue(ex.message!!.contains("unknown"))
    }

    @Test
    fun `marketplace throws when no default server configured`() {
        assertThrows(IllegalStateException::class.java) {
            plugin.marketplace()
        }
    }

    @Test
    fun `remove clears server entry`() {
        plugin.configure("temp", config())
        assertTrue(plugin.remove("temp"))

        assertThrows(IllegalStateException::class.java) {
            plugin.marketplace("temp")
        }
    }

    @Test
    fun `remove returns false for unknown server ID`() {
        assertFalse(plugin.remove("nonexistent"))
    }

    @Test
    fun `removeAll clears all entries`() {
        plugin.configure("a", config())
        plugin.configure("b", config())

        plugin.removeAll()

        assertTrue(plugin.serverIds().isEmpty())
    }

    @Test
    fun `reconfigure same server ID replaces marketplace`() {
        plugin.configure("s", config("http://old:8080"))
        val old = plugin.marketplace("s")

        plugin.configure("s", config("http://new:8080"))
        val new = plugin.marketplace("s")

        assertNotSame(old, new)
    }

    @Test
    fun `serverIds returns all registered IDs`() {
        plugin.configure("alpha", config())
        plugin.configure("beta", config())
        plugin.configure("gamma", config())

        assertEquals(setOf("alpha", "beta", "gamma"), plugin.serverIds())
    }

    @Test
    fun `default server convenience delegates to DEFAULT_SERVER_ID`() {
        plugin.configure(config())

        assertEquals(setOf(PlugwerkPlugin.DEFAULT_SERVER_ID), plugin.serverIds())
        assertSame(plugin.marketplace(), plugin.marketplace(PlugwerkPlugin.DEFAULT_SERVER_ID))
    }

    @Test
    fun `marketplace instances are isolated`() {
        plugin.configure("a", config("http://server-a:8080", "ns-a"))
        plugin.configure("b", config("http://server-b:8080", "ns-b"))

        val clientA = (plugin.marketplace("a") as PlugwerkMarketplaceImpl).client
        val clientB = (plugin.marketplace("b") as PlugwerkMarketplaceImpl).client

        assertEquals("http://server-a:8080", clientA.config.serverUrl)
        assertEquals("ns-a", clientA.config.namespace)
        assertEquals("http://server-b:8080", clientB.config.serverUrl)
        assertEquals("ns-b", clientB.config.namespace)
    }

    @Test
    fun `stop cleans up all servers`() {
        plugin.configure("a", config())
        plugin.configure("b", config())

        plugin.stop()

        assertTrue(plugin.serverIds().isEmpty())
    }
}
