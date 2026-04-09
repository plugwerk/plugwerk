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
package io.plugwerk.client.catalog

import io.plugwerk.client.PlugwerkClient
import io.plugwerk.spi.PlugwerkConfig
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

        catalog.searchPlugins(SearchCriteria(query = "hello"))

        val request = server.takeRequest()
        assert(request.path!!.contains("q=hello")) { "Expected q= in path: ${request.path}" }
    }

    @Test
    fun `downloadUrl returns correct URL`() {
        val url = catalog.downloadUrl("my-plugin", "1.0.0")
        assert(url.contains("/api/v1/namespaces/acme/plugins/my-plugin/releases/1.0.0/download")) {
            "Unexpected download URL: $url"
        }
    }
}
