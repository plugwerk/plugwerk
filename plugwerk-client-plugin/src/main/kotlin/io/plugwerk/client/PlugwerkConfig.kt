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
 * The SDK constructs API URLs as:
 * `{serverUrl}/api/v1/namespaces/{namespace}/...`
 */
data class PlugwerkConfig(
    val serverUrl: String,
    val namespace: String,
    val accessToken: String? = null,
    val connectionTimeoutMs: Long = DEFAULT_CONNECTION_TIMEOUT_MS,
    val readTimeoutMs: Long = DEFAULT_READ_TIMEOUT_MS,
    val cacheDirectory: Path? = null,
) {
    init {
        require(serverUrl.isNotBlank()) { "serverUrl must not be blank" }
        require(namespace.isNotBlank()) { "namespace must not be blank" }
        require(connectionTimeoutMs > 0) { "connectionTimeoutMs must be positive" }
        require(readTimeoutMs > 0) { "readTimeoutMs must be positive" }
    }

    override fun toString(): String =
        "PlugwerkConfig(serverUrl=$serverUrl, namespace=$namespace, accessToken=${if (accessToken != null) "<set>" else "<none>"})"

    companion object {
        const val DEFAULT_CONNECTION_TIMEOUT_MS: Long = 10_000
        const val DEFAULT_READ_TIMEOUT_MS: Long = 30_000

        private const val PROP_SERVER_URL = "plugwerk.serverUrl"
        private const val PROP_NAMESPACE = "plugwerk.namespace"
        private const val PROP_ACCESS_TOKEN = "plugwerk.accessToken"
        private const val PROP_CONNECTION_TIMEOUT_MS = "plugwerk.connectionTimeoutMs"
        private const val PROP_READ_TIMEOUT_MS = "plugwerk.readTimeoutMs"
        private const val PROP_CACHE_DIRECTORY = "plugwerk.cacheDirectory"

        /** Loads configuration from a `.properties` file on the filesystem. */
        fun fromProperties(path: Path): PlugwerkConfig = path.inputStream().use { fromProperties(it) }

        /** Loads configuration from an [InputStream] (e.g. classpath resource). */
        fun fromProperties(stream: InputStream): PlugwerkConfig {
            val props = Properties().apply { load(stream) }
            return Builder(
                serverUrl = props.requireProperty(PROP_SERVER_URL),
                namespace = props.requireProperty(PROP_NAMESPACE),
            ).apply {
                props.getProperty(PROP_ACCESS_TOKEN)?.let { accessToken(it) }
                props.getProperty(PROP_CONNECTION_TIMEOUT_MS)?.let { connectionTimeoutMs(it.toLong()) }
                props.getProperty(PROP_READ_TIMEOUT_MS)?.let { readTimeoutMs(it.toLong()) }
                props.getProperty(PROP_CACHE_DIRECTORY)?.let { cacheDirectory(Path.of(it)) }
            }.build()
        }

        /**
         * Loads configuration from JVM system properties.
         *
         * Required system properties:
         * - `plugwerk.serverUrl` — base URL of the Plugwerk server
         * - `plugwerk.namespace` — namespace to connect to
         *
         * Optional system properties mirror the `.properties` file keys.
         * `plugwerk.cacheDirectory` is required when using PF4J plugin mode (no-arg constructor).
         *
         * This factory is used by [io.plugwerk.client.PlugwerkMarketplaceImpl]'s no-arg constructor,
         * which PF4J invokes when discovering the extension via reflection. Set the required system
         * properties before calling `pluginManager.getExtensions(PlugwerkMarketplace::class.java)`.
         */
        fun fromSystemProperties(): PlugwerkConfig {
            fun requireProp(key: String) = System.getProperty(key)?.takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("Required system property '$key' is not set or blank")
            return Builder(
                serverUrl = requireProp(PROP_SERVER_URL),
                namespace = requireProp(PROP_NAMESPACE),
            ).apply {
                System.getProperty(PROP_ACCESS_TOKEN)?.let { accessToken(it) }
                System.getProperty(PROP_CONNECTION_TIMEOUT_MS)?.let { connectionTimeoutMs(it.toLong()) }
                System.getProperty(PROP_READ_TIMEOUT_MS)?.let { readTimeoutMs(it.toLong()) }
                System.getProperty(PROP_CACHE_DIRECTORY)?.let { cacheDirectory(Path.of(it)) }
            }.build()
        }

        private fun Properties.requireProperty(key: String): String = getProperty(key)?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("Required property '$key' is missing or blank")
    }

    /** Fluent builder for [PlugwerkConfig]. */
    class Builder(private val serverUrl: String, private val namespace: String) {
        private var accessToken: String? = null
        private var connectionTimeoutMs: Long = DEFAULT_CONNECTION_TIMEOUT_MS
        private var readTimeoutMs: Long = DEFAULT_READ_TIMEOUT_MS
        private var cacheDirectory: Path? = null

        fun accessToken(token: String) = apply { this.accessToken = token }

        fun connectionTimeoutMs(ms: Long) = apply { this.connectionTimeoutMs = ms }

        fun readTimeoutMs(ms: Long) = apply { this.readTimeoutMs = ms }

        fun cacheDirectory(path: Path) = apply { this.cacheDirectory = path }

        fun build(): PlugwerkConfig = PlugwerkConfig(
            serverUrl = serverUrl,
            namespace = namespace,
            accessToken = accessToken,
            connectionTimeoutMs = connectionTimeoutMs,
            readTimeoutMs = readTimeoutMs,
            cacheDirectory = cacheDirectory,
        )
    }
}
