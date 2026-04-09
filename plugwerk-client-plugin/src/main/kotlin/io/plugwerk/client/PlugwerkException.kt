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

/** Base class for all Plugwerk SDK exceptions. */
open class PlugwerkException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/** The server returned an unexpected HTTP error response. */
class PlugwerkApiException(val statusCode: Int, message: String) : PlugwerkException("HTTP $statusCode: $message")

/** The request was rejected due to missing or invalid credentials (HTTP 401 / 403). */
class PlugwerkAuthException(val statusCode: Int, message: String) :
    PlugwerkException("Auth error HTTP $statusCode: $message")

/** The requested resource was not found on the server (HTTP 404). */
class PlugwerkNotFoundException(val url: String) : PlugwerkException("Resource not found: $url")
