package io.plugwerk.client

import java.nio.file.Path

/**
 * Configuration for the Plugwerk Client SDK.
 * Full builder pattern and properties file support will be added in Milestone 7 (T-7.1).
 */
data class PlugwerkConfig(
    val serverUrl: String,
    val namespace: String,
    val apiKey: String? = null,
    val connectionTimeoutMs: Long = 10_000,
    val readTimeoutMs: Long = 30_000,
    val cacheDirectory: Path? = null,
) {
    override fun toString(): String = "PlugwerkConfig(serverUrl=$serverUrl, namespace=$namespace, apiKey=<masked>)"
}
