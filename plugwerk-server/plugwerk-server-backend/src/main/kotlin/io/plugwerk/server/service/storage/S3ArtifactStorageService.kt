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
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.HeadObjectRequest
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request
import software.amazon.awssdk.services.s3.model.NoSuchKeyException
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.S3Exception
import java.io.InputStream

/**
 * S3-compatible [ArtifactStorageService] backed by the AWS SDK v2 (#191).
 *
 * Selected when `plugwerk.storage.type=s3`. Hands every SDK call through
 * [mapExceptions] so callers see the project's own
 * [ArtifactNotFoundException] / [ArtifactStorageException] regardless of
 * which underlying `S3Exception` subtype the SDK throws.
 *
 * Key handling: every public method routes through [prefixed] so the
 * operator-configured `key-prefix` is opaque to callers. Returned keys
 * from [listKeys] are stripped of the prefix again so consumers see the
 * same keys they passed in.
 */
@Service
@ConditionalOnProperty(prefix = "plugwerk.storage", name = ["type"], havingValue = "s3")
class S3ArtifactStorageService(private val s3Client: S3Client, properties: PlugwerkProperties) :
    ArtifactStorageService {

    private val s3 = checkNotNull(properties.storage.s3) {
        "plugwerk.storage.s3 must be configured when plugwerk.storage.type=s3"
    }
    private val bucket: String = s3.bucket
    private val keyPrefix: String = s3.keyPrefix

    override fun store(key: String, content: InputStream, contentLength: Long): String {
        mapExceptions(key) {
            s3Client.putObject(
                PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(prefixed(key))
                    .contentLength(contentLength)
                    .build(),
                RequestBody.fromInputStream(content, contentLength),
            )
        }
        return key
    }

    override fun retrieve(key: String): InputStream = mapExceptions(key) {
        s3Client.getObject(
            GetObjectRequest.builder()
                .bucket(bucket)
                .key(prefixed(key))
                .build(),
        )
    }

    override fun delete(key: String) {
        // S3 DeleteObject is silently idempotent — matches Files.deleteIfExists.
        mapExceptions(key) {
            s3Client.deleteObject(
                DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(prefixed(key))
                    .build(),
            )
        }
    }

    override fun exists(key: String): Boolean = try {
        s3Client.headObject(
            HeadObjectRequest.builder()
                .bucket(bucket)
                .key(prefixed(key))
                .build(),
        )
        true
    } catch (_: NoSuchKeyException) {
        false
    } catch (ex: S3Exception) {
        // 404 from HeadObject is reported as a generic S3Exception with status
        // 404, not NoSuchKeyException — that variant is only thrown for
        // GetObject. Treat both as "not found".
        if (ex.statusCode() == 404) {
            false
        } else {
            throw ArtifactStorageException(
                "S3 headObject failed for key '$key': ${ex.message}",
                ex,
            )
        }
    }

    override fun listKeys(prefix: String): Sequence<String> {
        val combinedPrefix = prefixed(prefix)
        // Lazy: every iteration step calls the paginator, which transparently
        // pulls the next page when the current one is exhausted. Callers MUST
        // either consume the sequence fully or wrap iteration in a try-finally
        // because the paginator keeps SDK state alive between pages.
        return sequence {
            val request = ListObjectsV2Request.builder()
                .bucket(bucket)
                .prefix(combinedPrefix)
                .build()
            try {
                s3Client.listObjectsV2Paginator(request).forEach { page ->
                    page.contents().forEach { obj ->
                        // Strip the configured prefix so the caller sees the same
                        // keys they passed to store/retrieve/delete.
                        val raw = obj.key()
                        yield(if (keyPrefix.isNotEmpty()) raw.removePrefix(keyPrefix) else raw)
                    }
                }
            } catch (ex: S3Exception) {
                throw ArtifactStorageException(
                    "S3 listObjects failed for prefix '$prefix': ${ex.message}",
                    ex,
                )
            }
        }
    }

    private fun prefixed(key: String): String = if (keyPrefix.isEmpty()) key else "$keyPrefix$key"

    private inline fun <T> mapExceptions(key: String, block: () -> T): T = try {
        block()
    } catch (ex: NoSuchKeyException) {
        throw ArtifactNotFoundException(key)
    } catch (ex: S3Exception) {
        throw ArtifactStorageException(
            "S3 operation failed for key '$key': ${ex.message}",
            ex,
        )
    } catch (ex: Exception) {
        throw ArtifactStorageException(
            "Unexpected error during S3 operation for key '$key': ${ex.message}",
            ex,
        )
    }
}
