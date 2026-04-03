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
package io.plugwerk.server.domain

/**
 * Packaging format of a plugin artifact.
 *
 * Stored per [PluginReleaseEntity] and determined at upload time from the original filename
 * extension. Used to set the correct `Content-Disposition` filename on download and exposed
 * to clients via the API so they know the artifact type before downloading.
 */
enum class FileFormat {
    JAR,
    ZIP,
    ;

    companion object {
        /**
         * Resolves a [FileFormat] from a file extension string.
         *
         * @param extension lowercase file extension without dot (e.g. `"jar"`, `"zip"`)
         * @return the matching [FileFormat], defaulting to [JAR] for unrecognized extensions
         */
        fun fromExtension(extension: String): FileFormat = when (extension.lowercase()) {
            "zip" -> ZIP
            else -> JAR
        }
    }
}
