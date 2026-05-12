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
import io.plugwerk.server.service.ArtifactNotFoundException
import io.plugwerk.server.service.ArtifactStorageException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.HeadObjectRequest
import software.amazon.awssdk.services.s3.model.NoSuchKeyException
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.S3Exception

/**
 * Unit tests for [S3ArtifactStorageService] (#191).
 *
 * Covers the exception-mapping contract (SDK exception → project exception),
 * key-prefix handling, and basic request shape. Integration coverage against
 * a real MinIO container lives in `S3ArtifactStorageServiceIntegrationTest`.
 */
class S3ArtifactStorageServiceTest {

    private lateinit var s3Client: S3Client
    private lateinit var storage: S3ArtifactStorageService

    @BeforeEach
    fun setUp() {
        s3Client = mock()
        storage = S3ArtifactStorageService(s3Client, props(keyPrefix = ""))
    }

    // -- exception mapping -------------------------------------------------

    @Test
    fun `retrieve maps NoSuchKeyException to ArtifactNotFoundException`() {
        whenever(s3Client.getObject(any<GetObjectRequest>()))
            .thenThrow(NoSuchKeyException.builder().message("not found").build())

        assertThatThrownBy { storage.retrieve("missing:plugin:1.0.0:jar") }
            .isInstanceOf(ArtifactNotFoundException::class.java)
            .hasMessageContaining("missing:plugin:1.0.0:jar")
    }

    @Test
    fun `retrieve maps generic S3Exception to ArtifactStorageException with cause`() {
        val cause = S3Exception.builder().message("AccessDenied").build()
        whenever(s3Client.getObject(any<GetObjectRequest>())).thenThrow(cause)

        assertThatThrownBy { storage.retrieve("acme:plugin:1.0.0:jar") }
            .isInstanceOf(ArtifactStorageException::class.java)
            .hasMessageContaining("acme:plugin:1.0.0:jar")
            .hasCause(cause)
    }

    @Test
    fun `exists returns true for HeadObject success`() {
        whenever(s3Client.headObject(any<HeadObjectRequest>())).thenReturn(null)

        assertThat(storage.exists("acme:plugin:1.0.0:jar")).isTrue()
    }

    @Test
    fun `exists returns false for NoSuchKeyException`() {
        whenever(s3Client.headObject(any<HeadObjectRequest>()))
            .thenThrow(NoSuchKeyException.builder().message("not found").build())

        assertThat(storage.exists("acme:plugin:1.0.0:jar")).isFalse()
    }

    @Test
    fun `exists returns false for 404 S3Exception (HeadObject path)`() {
        // HeadObject reports 404 as a plain S3Exception with statusCode=404,
        // not a NoSuchKeyException — that subtype is only thrown by GetObject.
        // Build an S3Exception with awsErrorDetails so statusCode() returns 404.
        val notFound = S3Exception.builder()
            .statusCode(404)
            .message("Not Found")
            .build()
        whenever(s3Client.headObject(any<HeadObjectRequest>())).thenThrow(notFound)

        assertThat(storage.exists("acme:plugin:1.0.0:jar")).isFalse()
    }

    @Test
    fun `exists rethrows for non-404 S3Exception`() {
        val accessDenied = S3Exception.builder()
            .statusCode(403)
            .message("AccessDenied")
            .build()
        whenever(s3Client.headObject(any<HeadObjectRequest>())).thenThrow(accessDenied)

        assertThatThrownBy { storage.exists("acme:plugin:1.0.0:jar") }
            .isInstanceOf(ArtifactStorageException::class.java)
    }

    @Test
    fun `delete is silently idempotent (SDK DeleteObject returns 204 for missing keys)`() {
        storage.delete("never:existed:0.0.0:jar")

        verify(s3Client).deleteObject(any<DeleteObjectRequest>())
    }

    // -- key prefix --------------------------------------------------------

    @Test
    fun `keyPrefix is prepended to every S3 key`() {
        val sut = S3ArtifactStorageService(s3Client, props(keyPrefix = "prod/plugwerk/"))

        sut.delete("acme:plugin:1.0.0:jar")

        val captor = argumentCaptor<DeleteObjectRequest>()
        verify(s3Client).deleteObject(captor.capture())
        assertThat(captor.firstValue.key()).isEqualTo("prod/plugwerk/acme:plugin:1.0.0:jar")
    }

    @Test
    fun `empty keyPrefix passes keys through verbatim`() {
        storage.delete("acme:plugin:1.0.0:jar")

        val captor = argumentCaptor<DeleteObjectRequest>()
        verify(s3Client).deleteObject(captor.capture())
        assertThat(captor.firstValue.key()).isEqualTo("acme:plugin:1.0.0:jar")
    }

    @Test
    fun `store passes contentLength through to PutObjectRequest`() {
        storage.store("acme:plugin:1.0.0:jar", "ignored".byteInputStream(), 7L)

        val captor = argumentCaptor<PutObjectRequest>()
        verify(s3Client).putObject(captor.capture(), any<software.amazon.awssdk.core.sync.RequestBody>())
        assertThat(captor.firstValue.bucket()).isEqualTo("plugwerk-artifacts")
        assertThat(captor.firstValue.key()).isEqualTo("acme:plugin:1.0.0:jar")
        assertThat(captor.firstValue.contentLength()).isEqualTo(7L)
    }

    private fun props(keyPrefix: String): PlugwerkProperties = PlugwerkProperties(
        storage = PlugwerkProperties.StorageProperties(
            type = "s3",
            s3 = PlugwerkProperties.StorageProperties.S3Properties(
                bucket = "plugwerk-artifacts",
                region = "eu-central-1",
                keyPrefix = keyPrefix,
            ),
        ),
        auth = PlugwerkProperties.AuthProperties(
            jwtSecret = "test-secret-at-least-32-chars-long!!",
        ),
    )
}
