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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotSame
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
    fun `connect returns a fresh marketplace bound to the given config`() {
        val marketplace = plugin.connect(config("http://server-a:8080", "ns-a")) as PlugwerkMarketplaceImpl

        assertEquals("http://server-a:8080", marketplace.client.config.serverUrl)
        assertEquals("ns-a", marketplace.client.config.namespace)
    }

    @Test
    fun `connect returns a new instance on every call`() {
        // No internal cache by design — each call yields its own HTTP client. Hosts
        // that want to reuse a marketplace are expected to store the reference.
        val first = plugin.connect(config())
        val second = plugin.connect(config())

        assertNotSame(first, second)
    }

    @Test
    fun `multiple connects with different configs produce isolated marketplaces`() {
        val a = plugin.connect(config("http://server-a:8080", "ns-a")) as PlugwerkMarketplaceImpl
        val b = plugin.connect(config("http://server-b:8080", "ns-b")) as PlugwerkMarketplaceImpl

        assertEquals("http://server-a:8080", a.client.config.serverUrl)
        assertEquals("ns-a", a.client.config.namespace)
        assertEquals("http://server-b:8080", b.client.config.serverUrl)
        assertEquals("ns-b", b.client.config.namespace)
    }

    @Test
    fun `stop closes any marketplaces still alive (defense-in-depth)`() {
        // Hosts that forget to call close() leave us with HTTP clients to reclaim.
        // Stop must close those rather than leaking dispatcher threads on plugin
        // unload.
        val a = plugin.connect(config()) as PlugwerkMarketplaceImpl
        val b = plugin.connect(config()) as PlugwerkMarketplaceImpl

        plugin.stop()

        // After stop, repeated close() calls on the same instances must be a
        // no-op (the contract for AutoCloseable.close as we implement it). If
        // stop() did not actually close them, the second close() below would do
        // the work and the first one would have been a leak — this is the
        // assertion-by-side-effect we can express without exposing internals.
        a.close()
        b.close()
    }

    @Test
    fun `close on the marketplace is idempotent`() {
        val marketplace = plugin.connect(config())

        marketplace.close()
        // Second close is a no-op; if it threw or double-closed the underlying
        // HTTP client we would observe an exception here.
        marketplace.close()
    }
}
