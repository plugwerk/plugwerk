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
