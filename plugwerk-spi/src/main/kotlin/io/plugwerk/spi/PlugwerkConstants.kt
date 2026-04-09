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

/**
 * Shared constants used across the Plugwerk SPI, client SDK, and server.
 *
 * These values define the stable API contract between host applications and the
 * Plugwerk server. They should be referenced rather than hard-coded in client code.
 */
object PlugwerkConstants {
    /** Current REST API version string, included in every endpoint path. */
    const val API_VERSION = "v1"

    /** Base path prefix for all Plugwerk REST endpoints (e.g. `/api/v1`). */
    const val API_BASE_PATH = "/api/$API_VERSION"

    /**
     * Slug of the namespace that is used when no explicit namespace is configured.
     *
     * Most single-tenant deployments only ever need this namespace.
     */
    const val DEFAULT_NAMESPACE = "default"
}
