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
package io.plugwerk.server.service.configuration

import io.plugwerk.server.PlugwerkProperties
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper

class ConfigurationTreeBuilderTest {

    private val mapper = JsonMapper.builder().build()

    @Test
    fun `redacts jwt secret as a configured-flag marker`() {
        val props = PlugwerkProperties(
            auth = PlugwerkProperties.AuthProperties(jwtSecret = "do-not-leak"),
        )
        val tree = ConfigurationTreeBuilder(mapper, props).build()

        val jwtSecret = tree.get("auth")?.get("jwt-secret")
        assertThat(jwtSecret).isNotNull
        assertThat(jwtSecret!!.get("_secret").booleanValue()).isTrue
        assertThat(jwtSecret.get("configured").booleanValue()).isTrue
        // The plaintext must not appear anywhere in the rendered tree.
        assertThat(tree.toString()).doesNotContain("do-not-leak")
    }

    @Test
    fun `marks an empty secret as not configured`() {
        val props = PlugwerkProperties(
            auth = PlugwerkProperties.AuthProperties(jwtSecret = ""),
        )
        val tree = ConfigurationTreeBuilder(mapper, props).build()

        val jwtSecret = tree.get("auth")?.get("jwt-secret")
        assertThat(jwtSecret?.get("configured")?.booleanValue()).isFalse
    }

    @Test
    fun `non-secret leafs pass through untouched`() {
        val props = PlugwerkProperties(
            storage = PlugwerkProperties.StorageProperties(type = "fs"),
        )
        val tree = ConfigurationTreeBuilder(mapper, props).build()

        assertThat(tree.get("storage")?.get("type")?.textValue()).isEqualTo("fs")
    }

    @Test
    fun `bean-validation methods are hidden from the rendered tree`() {
        val props = PlugwerkProperties()
        val tree = ConfigurationTreeBuilder(mapper, props).build()

        // @AssertTrue methods on PlugwerkProperties.StorageProperties /
        // S3Properties would otherwise surface as pseudo-properties
        // because Jackson treats `isFooBar()` as a bean getter.
        val asString = tree.toString()
        // The Kotlin Jackson module keeps `isFooBar` as a method name —
        // hide both shapes so neither leaks.
        assertThat(asString).doesNotContain("isS3ConfigPresentWhenS3Selected")
        assertThat(asString).doesNotContain("is-s3-config-present-when-s3-selected")
        assertThat(asString).doesNotContain("s3-config-present-when-s3-selected")
        assertThat(asString).doesNotContain("s3ConfigPresentWhenS3Selected")
        assertThat(asString).doesNotContain("isCredentialsConsistent")
        assertThat(asString).doesNotContain("credentials-consistent")
        assertThat(asString).doesNotContain("isKeyPrefixWellFormed")
        assertThat(asString).doesNotContain("key-prefix-well-formed")
    }
}
