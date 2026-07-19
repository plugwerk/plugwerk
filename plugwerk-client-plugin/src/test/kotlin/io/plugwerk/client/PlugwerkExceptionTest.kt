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

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

/**
 * Locks the message formatting and field exposure of the SDK exception
 * hierarchy. These messages and fields are part of the contract SDK consumers
 * see when handling failures, so their shape must not drift silently.
 */
class PlugwerkExceptionTest {

    @Test
    fun `base exception carries message and optional cause`() {
        val cause = IllegalStateException("boom")
        val ex = PlugwerkException("something failed", cause)

        assertEquals("something failed", ex.message)
        assertSame(cause, ex.cause)
    }

    @Test
    fun `base exception cause defaults to null`() {
        assertNull(PlugwerkException("no cause").cause)
    }

    @Test
    fun `api exception formats status code into message and exposes it`() {
        val ex = PlugwerkApiException(500, "Internal Server Error")

        assertEquals(500, ex.statusCode)
        assertEquals("HTTP 500: Internal Server Error", ex.message)
    }

    @Test
    fun `auth exception formats status code into message and exposes it`() {
        val ex = PlugwerkAuthException(401, "invalid api key")

        assertEquals(401, ex.statusCode)
        assertEquals("Auth error HTTP 401: invalid api key", ex.message)
    }

    @Test
    fun `not-found exception formats the url into the message and exposes it`() {
        val ex = PlugwerkNotFoundException("plugins/io.example.plugin")

        assertEquals("plugins/io.example.plugin", ex.url)
        assertEquals("Resource not found: plugins/io.example.plugin", ex.message)
    }
}
