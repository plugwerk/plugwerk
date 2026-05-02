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

    // -----------------------------------------------------------------------
    // Pagination — #428 regression coverage. Pre-fix the SDK only consumed
    // page 0 and silently dropped the rest.
    // -----------------------------------------------------------------------

    @Test
    fun `listPlugins walks every server page (#428)`() {
        enqueuePluginPage(page = 0, totalPages = 3, ids = listOf("a", "b"))
        enqueuePluginPage(page = 1, totalPages = 3, ids = listOf("c", "d"))
        enqueuePluginPage(page = 2, totalPages = 3, ids = listOf("e"))

        val plugins = catalog.listPlugins()

        assertEquals(listOf("a", "b", "c", "d", "e"), plugins.map { it.pluginId })
        assertEquals(3, server.requestCount)
        assertPagedRequests(
            expectedPages = listOf(0, 1, 2),
            pathPrefix = "/api/v1/namespaces/acme/plugins",
        )
    }

    @Test
    fun `listPlugins returns empty list when totalPages is zero (#428)`() {
        // totalPages=0 paths still issue exactly one GET so the loop exits
        // immediately and callers are not surprised by an extra round-trip.
        server.enqueue(
            MockResponse()
                .setBody("""{"content":[],"totalElements":0,"page":0,"size":20,"totalPages":0}""")
                .setResponseCode(200),
        )

        val plugins = catalog.listPlugins()

        assertEquals(0, plugins.size)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `searchPlugins walks every server page and preserves query params (#428)`() {
        enqueuePluginPage(page = 0, totalPages = 2, ids = listOf("a"))
        enqueuePluginPage(page = 1, totalPages = 2, ids = listOf("b"))

        val plugins = catalog.searchPlugins(SearchCriteria(query = "hello", tag = "ai"))

        assertEquals(listOf("a", "b"), plugins.map { it.pluginId })
        assertEquals(2, server.requestCount)
        // Query params survive every page request — `?q=hello&tag=ai&page=N&size=100` shape.
        repeat(2) { i ->
            val request = server.takeRequest()
            val path = request.path!!
            assert(path.contains("q=hello")) { "Page $i lost q= parameter: $path" }
            assert(path.contains("tag=ai")) { "Page $i lost tag= parameter: $path" }
            assert(path.contains("page=$i")) { "Page $i missing page=$i: $path" }
            assert(path.contains("size=100")) { "Page $i missing size=100: $path" }
        }
    }

    @Test
    fun `getPluginReleases walks every server page (#428)`() {
        enqueueReleasePage(page = 0, totalPages = 2, versions = listOf("1.0.0"))
        enqueueReleasePage(page = 1, totalPages = 2, versions = listOf("2.0.0"))

        val releases = catalog.getPluginReleases("my-plugin")

        assertEquals(listOf("1.0.0", "2.0.0"), releases.map { it.version })
        assertEquals(2, server.requestCount)
        assertPagedRequests(
            expectedPages = listOf(0, 1),
            pathPrefix = "/api/v1/namespaces/acme/plugins/my-plugin/releases",
        )
    }

    @Test
    fun `paginate caps at the safety limit when server reports inconsistent totalPages (#428)`() {
        // Server lies about totalPages — every page claims totalPages=10000.
        // The cap should kick in at MAX_PAGES (= 100 today) so we do not
        // hammer the server in an unbounded loop.
        repeat(120) { p ->
            enqueuePluginPage(page = p, totalPages = 10_000, ids = listOf("p$p"))
        }

        val plugins = catalog.listPlugins()

        assertEquals(100, server.requestCount) {
            "Expected the safety cap to stop after MAX_PAGES requests, got ${server.requestCount}"
        }
        assertEquals(100, plugins.size) {
            "Items collected before the cap should still be returned"
        }
    }

    private fun enqueuePluginPage(page: Int, totalPages: Int, ids: List<String>) {
        val items = ids.joinToString(",") { id ->
            """{"id":"00000000-0000-0000-0000-000000000001",""" +
                """"pluginId":"$id","name":"P-$id","status":"active"}"""
        }
        val body = """{"content":[$items],"totalElements":${ids.size},""" +
            """"page":$page,"size":20,"totalPages":$totalPages}"""
        server.enqueue(MockResponse().setBody(body).setResponseCode(200))
    }

    private fun enqueueReleasePage(page: Int, totalPages: Int, versions: List<String>) {
        val items = versions.joinToString(",") { v ->
            """{"id":"00000000-0000-0000-0000-000000000002",""" +
                """"pluginId":"my-plugin","version":"$v","status":"published"}"""
        }
        val body = """{"content":[$items],"totalElements":${versions.size},""" +
            """"page":$page,"size":20,"totalPages":$totalPages}"""
        server.enqueue(MockResponse().setBody(body).setResponseCode(200))
    }

    /**
     * Verifies the SDK issued one request per expected page in order, with
     * `?page=N&size=100` appended to [pathPrefix]. Drains MockWebServer's
     * request queue in the process.
     */
    private fun assertPagedRequests(expectedPages: List<Int>, pathPrefix: String) {
        expectedPages.forEach { p ->
            val request = server.takeRequest()
            val path = request.path!!
            assert(path.startsWith(pathPrefix)) { "Page $p hit unexpected path: $path" }
            assert(path.contains("page=$p")) { "Page $p missing page=$p: $path" }
            assert(path.contains("size=100")) { "Page $p missing size=100: $path" }
        }
    }
}
