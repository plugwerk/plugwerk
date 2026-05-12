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
package io.plugwerk.server.service.storage

import io.plugwerk.server.PlugwerkProperties
import io.plugwerk.server.SharedMinioContainer
import io.plugwerk.server.service.ArtifactNotFoundException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.http.apache.ApacheHttpClient
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.S3Configuration
import software.amazon.awssdk.services.s3.model.CreateBucketRequest
import java.io.ByteArrayInputStream
import java.net.URI
import java.util.UUID

/**
 * End-to-end coverage for [S3ArtifactStorageService] against a real MinIO
 * Testcontainer (#191). Unit tests in `S3ArtifactStorageServiceTest` cover
 * the exception-mapping and key-prefix contracts in isolation; this class
 * verifies the wire-level behaviour against a genuine S3-compatible server.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("integration")
class S3ArtifactStorageServiceIntegrationTest {

    private val bucket = "plugwerk-it-${UUID.randomUUID()}"
    private lateinit var s3Client: S3Client
    private lateinit var storage: S3ArtifactStorageService

    @BeforeAll
    fun setUp() {
        s3Client = S3Client.builder()
            .region(Region.of("us-east-1"))
            .endpointOverride(URI.create(SharedMinioContainer.endpoint))
            .credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(
                        SharedMinioContainer.ACCESS_KEY,
                        SharedMinioContainer.SECRET_KEY,
                    ),
                ),
            )
            .httpClient(ApacheHttpClient.builder().build())
            .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
            .build()
        s3Client.createBucket(CreateBucketRequest.builder().bucket(bucket).build())
        storage = S3ArtifactStorageService(s3Client, props(bucket = bucket))
    }

    @Test
    fun `store retrieve roundtrip preserves bytes`() {
        val bytes = "hello-plugwerk-s3".toByteArray()

        storage.store("acme:hello:1.0.0:jar", ByteArrayInputStream(bytes), bytes.size.toLong())

        val read = storage.retrieve("acme:hello:1.0.0:jar").readAllBytes()
        assertThat(read).isEqualTo(bytes)
    }

    @Test
    fun `exists returns true after store and false after delete`() {
        storage.store("acme:exists-test:1.0.0:jar", ByteArrayInputStream(byteArrayOf(1, 2, 3)), 3)
        assertThat(storage.exists("acme:exists-test:1.0.0:jar")).isTrue()

        storage.delete("acme:exists-test:1.0.0:jar")
        assertThat(storage.exists("acme:exists-test:1.0.0:jar")).isFalse()
    }

    @Test
    fun `retrieve of missing key throws ArtifactNotFoundException`() {
        assertThatThrownBy { storage.retrieve("never:existed:0.0.0:jar") }
            .isInstanceOf(ArtifactNotFoundException::class.java)
    }

    @Test
    fun `delete of missing key is silently idempotent`() {
        storage.delete("never:existed:0.0.0:jar")
        // no exception
    }

    @Test
    fun `listKeys returns every key under a prefix`() {
        storage.store("ns-list:plugin-a:1.0.0:jar", ByteArrayInputStream(byteArrayOf(0)), 1)
        storage.store("ns-list:plugin-b:1.0.0:jar", ByteArrayInputStream(byteArrayOf(0)), 1)
        storage.store("other-list:plugin-c:1.0.0:jar", ByteArrayInputStream(byteArrayOf(0)), 1)

        val results = storage.listKeys("ns-list:").toList()

        assertThat(results).containsExactlyInAnyOrder(
            "ns-list:plugin-a:1.0.0:jar",
            "ns-list:plugin-b:1.0.0:jar",
        )
    }

    @Test
    fun `listKeys crosses the 1000-key pagination boundary`() {
        // S3 ListObjectsV2 returns up to 1000 keys per page; we seed 1100 to
        // force pagination and verify the paginator surfaces every key.
        val prefix = "pagination:run-${UUID.randomUUID()}:"
        repeat(1100) { i ->
            storage.store("$prefix$i:jar", ByteArrayInputStream(byteArrayOf(0)), 1)
        }

        val results = storage.listKeys(prefix).toList()

        assertThat(results).hasSize(1100)
    }

    @Test
    fun `keyPrefix is opaque to callers — store with prefix, listKeys returns unprefixed`() {
        val prefixedStorage = S3ArtifactStorageService(
            s3Client,
            props(bucket = bucket, keyPrefix = "tenant-a/"),
        )

        prefixedStorage.store("acme:prefix-test:1.0.0:jar", ByteArrayInputStream(byteArrayOf(0)), 1)

        // Caller sees the key without the configured prefix.
        val listed = prefixedStorage.listKeys("acme:").toList()
        assertThat(listed).containsExactly("acme:prefix-test:1.0.0:jar")
        assertThat(prefixedStorage.exists("acme:prefix-test:1.0.0:jar")).isTrue()
    }

    private fun props(bucket: String, keyPrefix: String = ""): PlugwerkProperties = PlugwerkProperties(
        storage = PlugwerkProperties.StorageProperties(
            type = "s3",
            s3 = PlugwerkProperties.StorageProperties.S3Properties(
                bucket = bucket,
                region = "us-east-1",
                endpoint = SharedMinioContainer.endpoint,
                accessKey = SharedMinioContainer.ACCESS_KEY,
                secretKey = SharedMinioContainer.SECRET_KEY,
                keyPrefix = keyPrefix,
                pathStyleAccess = true,
            ),
        ),
        auth = PlugwerkProperties.AuthProperties(
            jwtSecret = "test-secret-at-least-32-chars-long!!",
        ),
    )
}
