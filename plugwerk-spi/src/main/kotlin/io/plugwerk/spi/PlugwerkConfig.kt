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

import java.io.InputStream
import java.nio.file.Path
import java.util.Properties
import kotlin.io.path.inputStream

/**
 * Configuration for one Plugwerk SDK instance, bound to a single server and namespace.
 *
 * Use [Builder] for programmatic construction or [fromProperties] to load from a
 * `.properties` file.
 *
 * **Authentication priority:** If both [apiKey] and [accessToken] are set, the API key
 * takes precedence (sent as `X-Api-Key` header). The [accessToken] is sent as
 * `Authorization: Bearer` and is intended for pre-obtained OIDC/JWT tokens.
 * The SDK does **not** implement a login flow — tokens must be obtained externally.
 *
 * **Recommended:** Use [apiKey] for CI/CD pipelines and automated consumers. API keys
 * are long-lived, namespace-scoped, and do not expire unless explicitly configured.
 *
 * **Security:** Never pass sensitive values (e.g. API keys, tokens) as JVM system properties
 * (`-Dplugwerk.apiKey=…`) — they are visible in `ps aux` and `/proc/PID/cmdline`.
 * Use [Builder] or a `.properties` file with restricted filesystem permissions instead.
 *
 * The SDK constructs API URLs as:
 * `{serverUrl}/api/v1/namespaces/{namespace}/...`
 */
data class PlugwerkConfig(
    val serverUrl: String,
    val namespace: String,
    val apiKey: String? = null,
    val accessToken: String? = null,
    val connectionTimeoutMs: Long = DEFAULT_CONNECTION_TIMEOUT_MS,
    val readTimeoutMs: Long = DEFAULT_READ_TIMEOUT_MS,
    val pluginDirectory: Path? = null,
) {
    init {
        require(serverUrl.isNotBlank()) { "serverUrl must not be blank" }
        require(namespace.isNotBlank()) { "namespace must not be blank" }
        require(connectionTimeoutMs > 0) { "connectionTimeoutMs must be positive" }
        require(readTimeoutMs > 0) { "readTimeoutMs must be positive" }
    }

    override fun toString(): String = "PlugwerkConfig(serverUrl=$serverUrl, namespace=$namespace" +
        ", apiKey=${if (apiKey != null) "<set>" else "<none>"}" +
        ", accessToken=${if (accessToken != null) "<set>" else "<none>"})"

    companion object {
        const val DEFAULT_CONNECTION_TIMEOUT_MS: Long = 10_000
        const val DEFAULT_READ_TIMEOUT_MS: Long = 30_000

        private const val PROP_SERVER_URL = "plugwerk.serverUrl"
        private const val PROP_NAMESPACE = "plugwerk.namespace"
        private const val PROP_API_KEY = "plugwerk.apiKey"
        private const val PROP_ACCESS_TOKEN = "plugwerk.accessToken"
        private const val PROP_CONNECTION_TIMEOUT_MS = "plugwerk.connectionTimeoutMs"
        private const val PROP_READ_TIMEOUT_MS = "plugwerk.readTimeoutMs"
        private const val PROP_PLUGIN_DIRECTORY = "plugwerk.pluginDirectory"

        /** Loads configuration from a `.properties` file on the filesystem. */
        fun fromProperties(path: Path): PlugwerkConfig = path.inputStream().use { fromProperties(it) }

        /** Loads configuration from an [InputStream] (e.g. classpath resource). */
        fun fromProperties(stream: InputStream): PlugwerkConfig {
            val props = Properties().apply { load(stream) }
            return Builder(
                serverUrl = props.requireProperty(PROP_SERVER_URL),
                namespace = props.requireProperty(PROP_NAMESPACE),
            ).apply {
                props.getProperty(PROP_API_KEY)?.let { apiKey(it) }
                props.getProperty(PROP_ACCESS_TOKEN)?.let { accessToken(it) }
                props.getProperty(PROP_CONNECTION_TIMEOUT_MS)?.let { connectionTimeoutMs(it.toLong()) }
                props.getProperty(PROP_READ_TIMEOUT_MS)?.let { readTimeoutMs(it.toLong()) }
                props.getProperty(PROP_PLUGIN_DIRECTORY)?.let { pluginDirectory(Path.of(it)) }
            }.build()
        }

        private fun Properties.requireProperty(key: String): String = getProperty(key)?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("Required property '$key' is missing or blank")
    }

    /** Fluent builder for [PlugwerkConfig]. */
    class Builder(private val serverUrl: String, private val namespace: String) {
        private var apiKey: String? = null
        private var accessToken: String? = null
        private var connectionTimeoutMs: Long = DEFAULT_CONNECTION_TIMEOUT_MS
        private var readTimeoutMs: Long = DEFAULT_READ_TIMEOUT_MS
        private var pluginDirectory: Path? = null

        fun apiKey(key: String) = apply { this.apiKey = key }

        fun accessToken(token: String) = apply { this.accessToken = token }

        fun connectionTimeoutMs(ms: Long) = apply { this.connectionTimeoutMs = ms }

        fun readTimeoutMs(ms: Long) = apply { this.readTimeoutMs = ms }

        fun pluginDirectory(path: Path) = apply { this.pluginDirectory = path }

        fun build(): PlugwerkConfig = PlugwerkConfig(
            serverUrl = serverUrl,
            namespace = namespace,
            apiKey = apiKey,
            accessToken = accessToken,
            connectionTimeoutMs = connectionTimeoutMs,
            readTimeoutMs = readTimeoutMs,
            pluginDirectory = pluginDirectory,
        )
    }
}
