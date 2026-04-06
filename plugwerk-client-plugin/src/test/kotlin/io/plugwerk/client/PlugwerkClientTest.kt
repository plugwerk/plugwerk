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
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class PlugwerkClientTest {
    private lateinit var server: MockWebServer
    private lateinit var client: PlugwerkClient

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        client =
            PlugwerkClient(
                PlugwerkConfig(
                    serverUrl = server.url("/").toString().trimEnd('/'),
                    namespace = "test-ns",
                    accessToken = "test-token",
                ),
            )
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `get sends Bearer token header`() {
        server.enqueue(MockResponse().setBody("""{"value":"ok"}""").setResponseCode(200))

        client.get<Map<String, String>>("plugins")

        val request = server.takeRequest()
        assertEquals("Bearer test-token", request.headers["Authorization"])
    }

    @Test
    fun `get builds correct namespace URL`() {
        server.enqueue(MockResponse().setBody("""{"value":"ok"}""").setResponseCode(200))

        client.get<Map<String, String>>("plugins")

        val request = server.takeRequest()
        assert(request.path!!.contains("/api/v1/namespaces/test-ns/plugins")) {
            "Expected namespace in path, got: ${request.path}"
        }
    }

    @Test
    fun `get deserializes JSON response`() {
        server.enqueue(MockResponse().setBody("""{"name":"my-plugin"}""").setResponseCode(200))

        val result = client.get<Map<String, String>>("plugins/foo")

        assertEquals("my-plugin", result["name"])
    }

    @Test
    fun `getOrNull returns null on 404`() {
        server.enqueue(MockResponse().setResponseCode(404))

        val result = client.getOrNull<Map<String, String>>("plugins/unknown")

        assertNull(result)
    }

    @Test
    fun `get throws PlugwerkNotFoundException on 404`() {
        server.enqueue(MockResponse().setResponseCode(404))

        assertThrows<PlugwerkNotFoundException> {
            client.get<Map<String, String>>("plugins/unknown")
        }
    }

    @Test
    fun `get throws PlugwerkAuthException on 401`() {
        server.enqueue(MockResponse().setResponseCode(401))

        assertThrows<PlugwerkAuthException> {
            client.get<Map<String, String>>("plugins")
        }
    }

    @Test
    fun `get throws PlugwerkAuthException on 403`() {
        server.enqueue(MockResponse().setResponseCode(403))

        assertThrows<PlugwerkAuthException> {
            client.get<Map<String, String>>("plugins")
        }
    }

    @Test
    fun `get throws PlugwerkApiException on 500`() {
        server.enqueue(MockResponse().setResponseCode(500).setBody("Internal Server Error"))

        assertThrows<PlugwerkApiException> {
            client.get<Map<String, String>>("plugins")
        }
    }

    @Test
    fun `get sends X-Api-Key header when apiKey is configured`() {
        val apiKeyClient =
            PlugwerkClient(
                PlugwerkConfig(
                    serverUrl = server.url("/").toString().trimEnd('/'),
                    namespace = "test-ns",
                    apiKey = "pwk_my-api-key",
                ),
            )
        server.enqueue(MockResponse().setBody("""{}""").setResponseCode(200))

        apiKeyClient.get<Map<String, String>>("plugins")

        val request = server.takeRequest()
        assertEquals("pwk_my-api-key", request.headers["X-Api-Key"])
        assertNull(request.headers["Authorization"])
    }

    @Test
    fun `apiKey takes precedence over accessToken`() {
        val dualAuthClient =
            PlugwerkClient(
                PlugwerkConfig(
                    serverUrl = server.url("/").toString().trimEnd('/'),
                    namespace = "test-ns",
                    apiKey = "pwk_key",
                    accessToken = "tok-jwt",
                ),
            )
        server.enqueue(MockResponse().setBody("""{}""").setResponseCode(200))

        dualAuthClient.get<Map<String, String>>("plugins")

        val request = server.takeRequest()
        assertEquals("pwk_key", request.headers["X-Api-Key"])
        assertNull(request.headers["Authorization"])
    }

    @Test
    fun `no auth header when neither apiKey nor accessToken is set`() {
        val anonymousClient =
            PlugwerkClient(
                PlugwerkConfig(
                    serverUrl = server.url("/").toString().trimEnd('/'),
                    namespace = "public-ns",
                ),
            )
        server.enqueue(MockResponse().setBody("""{}""").setResponseCode(200))

        anonymousClient.get<Map<String, String>>("plugins")

        val request = server.takeRequest()
        assertNull(request.headers["Authorization"])
        assertNull(request.headers["X-Api-Key"])
    }

    @Test
    fun `post sends JSON body and deserializes response`() {
        server.enqueue(MockResponse().setBody("""{"result":"created"}""").setResponseCode(200))

        val response = client.post<Map<String, String>>("plugins", mapOf("name" to "test"))

        val request = server.takeRequest()
        assertEquals("application/json; charset=utf-8", request.headers["Content-Type"])
        assertEquals("created", response["result"])
    }
}
