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
package io.plugwerk.client.updater

import io.plugwerk.client.PlugwerkClient
import io.plugwerk.spi.PlugwerkConfig
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class PlugwerkUpdateCheckerImplTest {
    private lateinit var server: MockWebServer
    private lateinit var updateChecker: PlugwerkUpdateCheckerImpl

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
        updateChecker = PlugwerkUpdateCheckerImpl(client)
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `checkForUpdates returns available updates`() {
        server.enqueue(
            MockResponse()
                .setBody(
                    """{"updates":[
                        {
                          "pluginId":"my-plugin",
                          "currentVersion":"1.0.0",
                          "latestVersion":"1.1.0",
                          "release":{"id":"00000000-0000-0000-0000-000000000002","pluginId":"my-plugin","version":"1.1.0","status":"published","artifactSha256":"abc"}
                        }
                    ]}""",
                )
                .setResponseCode(200),
        )

        val updates = updateChecker.checkForUpdates(mapOf("my-plugin" to "1.0.0"))

        assertEquals(1, updates.size)
        assertEquals("my-plugin", updates[0].pluginId)
        assertEquals("1.0.0", updates[0].currentVersion)
        assertEquals("1.1.0", updates[0].availableVersion)
        assertEquals("1.1.0", updates[0].release.version)
    }

    @Test
    fun `checkForUpdates sends POST to updates-check endpoint`() {
        server.enqueue(MockResponse().setBody("""{"updates":[]}""").setResponseCode(200))

        updateChecker.checkForUpdates(mapOf("plugin-a" to "2.0.0"))

        val request = server.takeRequest()
        assertTrue(request.path!!.contains("/updates/check")) { "Expected /updates/check in path: ${request.path}" }
        assertEquals("POST", request.method)
        assertTrue(request.body.readUtf8().contains("plugin-a")) { "Expected plugin-a in request body" }
    }

    @Test
    fun `checkForUpdates returns empty list for empty input`() {
        val updates = updateChecker.checkForUpdates(emptyMap())

        assertTrue(updates.isEmpty())
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `getAvailableUpdates throws UnsupportedOperationException`() {
        assertThrows<UnsupportedOperationException> {
            updateChecker.getAvailableUpdates()
        }
    }
}
