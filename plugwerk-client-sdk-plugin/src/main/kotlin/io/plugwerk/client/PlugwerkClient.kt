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

import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule
import java.io.InputStream
import java.nio.file.Path
import java.util.concurrent.TimeUnit

/**
 * Low-level HTTP client for communication with a single Plugwerk server.
 *
 * All requests are scoped to the namespace defined in [config]. The Bearer token
 * from [PlugwerkConfig.accessToken] is added automatically when present.
 *
 * Callers work with the higher-level components ([catalog][io.plugwerk.client.catalog.PlugwerkCatalogImpl],
 * [installer][io.plugwerk.client.installer.PlugwerkInstallerImpl], etc.) rather than this class directly.
 */
class PlugwerkClient(internal val config: PlugwerkConfig) {
    private val baseNamespacePath = "${config.serverUrl.trimEnd('/')}/api/v1/namespaces/${config.namespace}"

    @PublishedApi
    internal val objectMapper: JsonMapper =
        JsonMapper.builder()
            .addModule(KotlinModule.Builder().build())
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build()

    private val httpClient: OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(config.connectionTimeoutMs, TimeUnit.MILLISECONDS)
            .readTimeout(config.readTimeoutMs, TimeUnit.MILLISECONDS)
            .apply {
                if (config.accessToken != null) {
                    addInterceptor(BearerTokenInterceptor(config.accessToken))
                }
            }
            .build()

    /** Builds a full URL for the given path relative to the namespace base path. */
    fun url(path: String): String = "$baseNamespacePath/${path.trimStart('/')}"

    /** Executes a GET request and deserializes the JSON response body into [T]. */
    inline fun <reified T> get(path: String): T {
        val request = Request.Builder().url(url(path)).get().build()
        return execute(request) { body ->
            objectMapper.readValue(body.string(), T::class.java)
        }
    }

    /** Executes a GET request and returns null when the server responds with 404. */
    inline fun <reified T> getOrNull(path: String): T? {
        val request = Request.Builder().url(url(path)).get().build()
        return executeOrNull(request) { body ->
            objectMapper.readValue(body.string(), T::class.java)
        }
    }

    /** Executes a POST request with a JSON body and deserializes the JSON response into [T]. */
    inline fun <reified T> post(path: String, requestBody: Any): T {
        val json = objectMapper.writeValueAsString(requestBody)
        val body = json.toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder().url(url(path)).post(body).build()
        return execute(request) { responseBody ->
            objectMapper.readValue(responseBody.string(), T::class.java)
        }
    }

    /**
     * Executes a GET request for a binary artifact and returns the response body as an [InputStream].
     * The caller is responsible for closing the stream (and thereby the underlying HTTP response).
     */
    fun download(path: String): InputStream {
        val request = Request.Builder().url(url(path)).get().build()
        val response = httpClient.newCall(request).execute()
        handleErrors(response, url(path))
        return response.body.byteStream()
    }

    /**
     * Executes a GET request for a binary artifact and returns the suggested filename from the
     * `Content-Disposition` header paired with the response body stream.
     * The caller is responsible for closing the stream.
     */
    fun downloadWithFilename(path: String): Pair<String?, InputStream> {
        val request = Request.Builder().url(url(path)).get().build()
        val response = httpClient.newCall(request).execute()
        handleErrors(response, url(path))
        val filename = response.header("Content-Disposition")
            ?.let { Regex("""filename="([^"]+)"""").find(it)?.groupValues?.get(1) }
        return Pair(filename, response.body.byteStream())
    }

    @PublishedApi
    internal fun <T> execute(request: Request, transform: (okhttp3.ResponseBody) -> T): T {
        val response = httpClient.newCall(request).execute()
        handleErrors(response, request.url.toString())
        return response.use { transform(it.body) }
    }

    @PublishedApi
    internal fun <T> executeOrNull(request: Request, transform: (okhttp3.ResponseBody) -> T): T? {
        val response = httpClient.newCall(request).execute()
        if (response.code == 404) {
            response.close()
            return null
        }
        handleErrors(response, request.url.toString())
        return response.use { transform(it.body) }
    }

    @PublishedApi
    internal fun handleErrors(response: Response, url: String) {
        if (response.code in 200..299) return
        response.use {
            when (it.code) {
                401, 403 -> throw PlugwerkAuthException(it.code, "Unauthorized access to $url")
                404 -> throw PlugwerkNotFoundException(url)
                else -> throw PlugwerkApiException(it.code, it.body.string())
            }
        }
    }

    companion object {
        @PublishedApi
        internal val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

private class BearerTokenInterceptor(private val token: String) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request =
            chain.request().newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        return chain.proceed(request)
    }
}
