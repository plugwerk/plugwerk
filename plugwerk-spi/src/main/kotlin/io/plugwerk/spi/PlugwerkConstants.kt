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
