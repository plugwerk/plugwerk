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
package io.plugwerk.client.catalog

import io.plugwerk.client.PlugwerkClient
import io.plugwerk.client.PlugwerkConfig
import io.plugwerk.spi.model.PluginStatus
import io.plugwerk.spi.model.ReleaseStatus
import io.plugwerk.spi.model.SearchCriteria
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class PlugwerkCatalogImplTest {
    private lateinit var server: MockWebServer
    private lateinit var catalog: PlugwerkCatalogImpl

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        val client =
            PlugwerkClient(
                PlugwerkConfig(
                    serverUrl = server.url("/").toString().trimEnd('/'),
                    namespace = "acme",
                ),
            )
        catalog = PlugwerkCatalogImpl(client)
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `listPlugins returns mapped plugin list`() {
        server.enqueue(
            MockResponse()
                .setBody(
                    """{"content":[
                        {"id":"00000000-0000-0000-0000-000000000001","pluginId":"my-plugin","name":"My Plugin","status":"active","latestRelease":{"id":"00000000-0000-0000-0000-000000000002","pluginId":"my-plugin","version":"1.0.0","status":"published"}}
                    ],"totalElements":1,"page":0,"size":20,"totalPages":1}""",
                )
                .setResponseCode(200),
        )

        val plugins = catalog.listPlugins()

        assertEquals(1, plugins.size)
        assertEquals("my-plugin", plugins[0].pluginId)
        assertEquals("My Plugin", plugins[0].name)
        assertEquals(PluginStatus.ACTIVE, plugins[0].status)
        assertEquals("1.0.0", plugins[0].latestVersion)
    }

    @Test
    fun `getPlugin returns mapped plugin`() {
        server.enqueue(
            MockResponse()
                .setBody(
                    """{"id":"00000000-0000-0000-0000-000000000001","pluginId":"my-plugin","name":"My Plugin","status":"active"}""",
                )
                .setResponseCode(200),
        )

        val plugin = catalog.getPlugin("my-plugin")

        assertEquals("my-plugin", plugin?.pluginId)
        assertEquals(PluginStatus.ACTIVE, plugin?.status)
    }

    @Test
    fun `getPlugin returns null on 404`() {
        server.enqueue(MockResponse().setResponseCode(404))

        val plugin = catalog.getPlugin("unknown")

        assertNull(plugin)
    }

    @Test
    fun `getPluginRelease maps release status correctly`() {
        server.enqueue(
            MockResponse()
                .setBody(
                    """{"id":"00000000-0000-0000-0000-000000000002","pluginId":"my-plugin","version":"1.2.0","status":"published","artifactSha256":"abc123"}""",
                )
                .setResponseCode(200),
        )

        val release = catalog.getPluginRelease("my-plugin", "1.2.0")

        assertEquals("1.2.0", release?.version)
        assertEquals(ReleaseStatus.PUBLISHED, release?.status)
        assertEquals("abc123", release?.artifactSha256)
    }

    @Test
    fun `searchPlugins appends query parameters`() {
        server.enqueue(
            MockResponse()
                .setBody("""{"content":[],"totalElements":0,"page":0,"size":20,"totalPages":0}""")
                .setResponseCode(200),
        )

        catalog.searchPlugins(SearchCriteria(query = "hello", category = "tools"))

        val request = server.takeRequest()
        assert(request.path!!.contains("q=hello")) { "Expected q= in path: ${request.path}" }
        assert(request.path!!.contains("category=tools")) { "Expected category= in path: ${request.path}" }
    }

    @Test
    fun `downloadUrl returns correct URL`() {
        val url = catalog.downloadUrl("my-plugin", "1.0.0")
        assert(url.contains("/api/v1/namespaces/acme/plugins/my-plugin/releases/1.0.0/download")) {
            "Unexpected download URL: $url"
        }
    }
}
