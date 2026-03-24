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

/** Base class for all Plugwerk SDK exceptions. */
open class PlugwerkException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/** The server returned an unexpected HTTP error response. */
class PlugwerkApiException(val statusCode: Int, message: String) : PlugwerkException("HTTP $statusCode: $message")

/** The request was rejected due to missing or invalid credentials (HTTP 401 / 403). */
class PlugwerkAuthException(val statusCode: Int, message: String) :
    PlugwerkException("Auth error HTTP $statusCode: $message")

/** The requested resource was not found on the server (HTTP 404). */
class PlugwerkNotFoundException(val url: String) : PlugwerkException("Resource not found: $url")
