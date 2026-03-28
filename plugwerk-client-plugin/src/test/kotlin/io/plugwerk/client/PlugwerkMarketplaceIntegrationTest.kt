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
package io.plugwerk.client

import io.plugwerk.spi.model.InstallResult
import io.plugwerk.spi.model.PluginStatus
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

/**
 * End-to-end integration test exercising the full SDK stack via a MockWebServer.
 * Verifies that [PlugwerkMarketplaceImpl.create] correctly wires catalog, installer,
 * and update checker, and that all three can complete real HTTP round-trips.
 */
class PlugwerkMarketplaceIntegrationTest {
    private lateinit var server: MockWebServer
    private lateinit var marketplace: PlugwerkMarketplaceImpl

    @TempDir
    lateinit var pluginDir: Path

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        marketplace =
            PlugwerkMarketplaceImpl.create(
                config =
                PlugwerkConfig(
                    serverUrl = server.url("/").toString().trimEnd('/'),
                    namespace = "test-ns",
                    accessToken = "integration-token",
                ),
                pluginDirectory = pluginDir,
            )
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `create wires all three components`() {
        assertNotNull(marketplace.catalog())
        assertNotNull(marketplace.installer())
        assertNotNull(marketplace.updateChecker())
    }

    @Test
    fun `catalog lists plugins end-to-end`() {
        server.enqueue(
            MockResponse()
                .setBody(
                    """{"content":[
                        {"id":"00000000-0000-0000-0000-000000000001","pluginId":"plugin-a","name":"Plugin A","status":"active","latestRelease":{"id":"00000000-0000-0000-0000-000000000002","pluginId":"plugin-a","version":"3.0.0","status":"published"}},
                        {"id":"00000000-0000-0000-0000-000000000003","pluginId":"plugin-b","name":"Plugin B","status":"archived"}
                    ],"totalElements":2,"page":0,"size":20,"totalPages":1}""",
                )
                .setResponseCode(200),
        )

        val plugins = marketplace.catalog().listPlugins()

        assertEquals(2, plugins.size)
        assertEquals("plugin-a", plugins[0].pluginId)
        assertEquals(PluginStatus.ACTIVE, plugins[0].status)
        assertEquals("3.0.0", plugins[0].latestVersion)
        assertEquals(PluginStatus.ARCHIVED, plugins[1].status)
    }

    @Test
    fun `installer downloads and installs a plugin end-to-end`() {
        val content = "fake-plugin-bytes".toByteArray()
        val sha256 = sha256Hex(content)

        server.enqueue(
            MockResponse()
                .setBody(
                    """{"id":"00000000-0000-0000-0000-000000000002","pluginId":"my-plugin","version":"1.0.0","status":"published","artifactSha256":"$sha256"}""",
                )
                .setResponseCode(200),
        )
        server.enqueue(MockResponse().setBody(String(content)).setResponseCode(200))

        val result = marketplace.installer().install("my-plugin", "1.0.0")

        assertInstanceOf(InstallResult.Success::class.java, result)
        assertTrue(Files.exists(pluginDir.resolve("my-plugin-1.0.0.jar")))
    }

    @Test
    fun `update checker returns available updates end-to-end`() {
        server.enqueue(
            MockResponse()
                .setBody(
                    """{"updates":[{
                        "pluginId":"plugin-a",
                        "currentVersion":"1.0.0",
                        "latestVersion":"2.0.0",
                        "release":{"id":"00000000-0000-0000-0000-000000000002","pluginId":"plugin-a","version":"2.0.0","status":"published","artifactSha256":"sha"}
                    }]}""",
                )
                .setResponseCode(200),
        )

        val updates = marketplace.updateChecker().checkForUpdates(mapOf("plugin-a" to "1.0.0"))

        assertEquals(1, updates.size)
        assertEquals("plugin-a", updates[0].pluginId)
        assertEquals("2.0.0", updates[0].availableVersion)
    }

    @Test
    fun `all components share the same namespace in requests`() {
        server.enqueue(
            MockResponse()
                .setBody("""{"content":[],"totalElements":0,"page":0,"size":20,"totalPages":0}""")
                .setResponseCode(200),
        )

        marketplace.catalog().listPlugins()

        val request = server.takeRequest()
        assertTrue(request.path!!.contains("/namespaces/test-ns/")) {
            "Expected test-ns namespace in request path: ${request.path}"
        }
    }

    @Test
    fun `all components include Bearer token in requests`() {
        server.enqueue(
            MockResponse()
                .setBody("""{"content":[],"totalElements":0,"page":0,"size":20,"totalPages":0}""")
                .setResponseCode(200),
        )

        marketplace.catalog().listPlugins()

        val request = server.takeRequest()
        assertEquals("Bearer integration-token", request.getHeader("Authorization"))
    }

    private fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(bytes).joinToString("") { "%02x".format(it) }
    }
}
