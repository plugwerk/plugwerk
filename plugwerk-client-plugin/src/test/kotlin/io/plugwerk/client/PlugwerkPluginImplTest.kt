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
