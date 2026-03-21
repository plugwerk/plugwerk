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
package io.plugwerk.descriptor

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream

class DescriptorResolver(
    private val plugwerkParser: PlugwerkDescriptorParser = PlugwerkDescriptorParser(),
    private val manifestParser: Pf4jManifestParser = Pf4jManifestParser(),
) {

    fun resolve(jarStream: InputStream): PlugwerkDescriptor {
        val bytes = jarStream.readAllBytes()

        return tryParse { plugwerkParser.parseFromJar(ByteArrayInputStream(bytes)) }
            ?: tryParse { manifestParser.parseFromJar(ByteArrayInputStream(bytes)) }
            ?: throw DescriptorNotFoundException(
                "No descriptor found in JAR (tried plugwerk.yml, MANIFEST.MF, plugin.properties)",
            )
    }

    private fun tryParse(block: () -> PlugwerkDescriptor): PlugwerkDescriptor? = try {
        block()
    } catch (_: DescriptorNotFoundException) {
        null
    }
}
