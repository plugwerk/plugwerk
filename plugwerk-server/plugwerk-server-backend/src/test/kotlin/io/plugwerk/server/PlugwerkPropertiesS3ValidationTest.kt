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
package io.plugwerk.server

import jakarta.validation.Validation
import jakarta.validation.Validator
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Validates [PlugwerkProperties.StorageProperties.S3Properties] and the
 * cross-field [PlugwerkProperties.StorageProperties.isS3ConfigPresentWhenS3Selected]
 * guard (#191). Each test pins one validation path so a future relaxation of
 * the contract fails one assertion at a time instead of silently widening the
 * accepted config surface.
 */
class PlugwerkPropertiesS3ValidationTest {

    private val validator: Validator =
        Validation.buildDefaultValidatorFactory().use { it.validator }

    @Test
    fun `valid S3 config with static creds passes`() {
        val props = PlugwerkProperties.StorageProperties.S3Properties(
            bucket = "plugwerk-artifacts",
            region = "eu-central-1",
            accessKey = "AKIA...",
            secretKey = "secret",
        )
        assertThat(validator.validate(props)).isEmpty()
    }

    @Test
    fun `valid S3 config with default credentials chain (both blank) passes`() {
        val props = PlugwerkProperties.StorageProperties.S3Properties(
            bucket = "plugwerk-artifacts",
            region = "eu-central-1",
        )
        assertThat(validator.validate(props)).isEmpty()
    }

    @Test
    fun `half-configured credential pair is rejected (accessKey set, secretKey blank)`() {
        val props = PlugwerkProperties.StorageProperties.S3Properties(
            bucket = "plugwerk-artifacts",
            region = "eu-central-1",
            accessKey = "AKIA...",
            secretKey = null,
        )
        val violations = validator.validate(props)
        assertThat(violations.map { it.message })
            .anyMatch { it.contains("both be set or both be blank") }
    }

    @Test
    fun `half-configured credential pair is rejected (secretKey set, accessKey blank)`() {
        val props = PlugwerkProperties.StorageProperties.S3Properties(
            bucket = "plugwerk-artifacts",
            region = "eu-central-1",
            accessKey = "",
            secretKey = "secret",
        )
        val violations = validator.validate(props)
        assertThat(violations.map { it.message })
            .anyMatch { it.contains("both be set or both be blank") }
    }

    @Test
    fun `blank bucket is rejected`() {
        val props = PlugwerkProperties.StorageProperties.S3Properties(
            bucket = "",
            region = "eu-central-1",
        )
        assertThat(validator.validate(props)).isNotEmpty
    }

    @Test
    fun `blank region is rejected`() {
        val props = PlugwerkProperties.StorageProperties.S3Properties(
            bucket = "plugwerk-artifacts",
            region = "",
        )
        assertThat(validator.validate(props)).isNotEmpty
    }

    @Test
    fun `key-prefix with leading slash is rejected`() {
        val props = PlugwerkProperties.StorageProperties.S3Properties(
            bucket = "plugwerk-artifacts",
            region = "eu-central-1",
            keyPrefix = "/env/plugwerk/",
        )
        val violations = validator.validate(props)
        assertThat(violations.map { it.message })
            .anyMatch { it.contains("must not start with") }
    }

    @Test
    fun `key-prefix without leading slash is accepted`() {
        val props = PlugwerkProperties.StorageProperties.S3Properties(
            bucket = "plugwerk-artifacts",
            region = "eu-central-1",
            keyPrefix = "env/plugwerk/",
        )
        assertThat(validator.validate(props)).isEmpty()
    }

    @Test
    fun `cross-field guard rejects type=s3 with null s3 sub-section`() {
        val storage = PlugwerkProperties.StorageProperties(type = "s3", s3 = null)
        val violations = validator.validate(storage)
        assertThat(violations.map { it.message })
            .anyMatch { it.contains("plugwerk.storage.s3.bucket") }
    }

    @Test
    fun `cross-field guard rejects type=s3 with blank bucket`() {
        val storage = PlugwerkProperties.StorageProperties(
            type = "s3",
            s3 = PlugwerkProperties.StorageProperties.S3Properties(
                bucket = "",
                region = "eu-central-1",
            ),
        )
        val violations = validator.validate(storage)
        assertThat(violations.map { it.message })
            .anyMatch { it.contains("plugwerk.storage.s3.bucket") }
    }

    @Test
    fun `cross-field guard accepts type=fs with no s3 sub-section`() {
        val storage = PlugwerkProperties.StorageProperties(type = "fs")
        assertThat(validator.validate(storage)).isEmpty()
    }

    @Test
    fun `toString redacts accessKey and secretKey`() {
        val props = PlugwerkProperties.StorageProperties.S3Properties(
            bucket = "plugwerk-artifacts",
            region = "eu-central-1",
            accessKey = "AKIA-public-id-no-secret",
            secretKey = "do-not-log-this-secret",
        )
        val str = props.toString()
        assertThat(str)
            .doesNotContain("AKIA-public-id-no-secret")
            .doesNotContain("do-not-log-this-secret")
        assertThat(str).contains("accessKey=<set>", "secretKey=<set>")
    }
}
