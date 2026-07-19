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
package io.plugwerk.spi

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Locks the stable REST API contract exposed by [PlugwerkConstants].
 *
 * These values are part of the wire contract between host applications and the
 * Plugwerk server; an accidental change here would silently break every client.
 * The test exists to make such a change fail loudly and deliberately.
 */
class PlugwerkConstantsTest {

    @Test
    fun `API_VERSION is v1`() {
        assertEquals("v1", PlugwerkConstants.API_VERSION)
    }

    @Test
    fun `API_BASE_PATH is derived from the API version`() {
        assertEquals("/api/v1", PlugwerkConstants.API_BASE_PATH)
        assertEquals("/api/${PlugwerkConstants.API_VERSION}", PlugwerkConstants.API_BASE_PATH)
    }

    @Test
    fun `DEFAULT_NAMESPACE is default`() {
        assertEquals("default", PlugwerkConstants.DEFAULT_NAMESPACE)
    }
}
