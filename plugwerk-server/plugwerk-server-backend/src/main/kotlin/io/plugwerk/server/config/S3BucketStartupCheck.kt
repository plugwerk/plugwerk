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
package io.plugwerk.server.config

import io.plugwerk.server.PlugwerkProperties
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.HeadBucketRequest

/**
 * Verifies that the configured S3 bucket is reachable at startup (#191).
 *
 * Only registered when `plugwerk.storage.type=s3`. Issues one `HeadBucket`
 * request against the configured bucket. Outcome depends on
 * `plugwerk.storage.s3.fail-fast-on-bucket-missing`:
 *
 *   - `false` (default): probe failure logs ERROR and the server continues
 *     starting. Operators get a clear log line and can fix the bucket
 *     without a restart loop.
 *   - `true`: probe failure rethrows, the Spring context fails to start, and
 *     the container orchestrator's restart loop kicks in. Suitable for
 *     production where a silently-broken artifact store is worse than a
 *     fail-closed pod restart (mirrors the #501 fail-fast pattern).
 *
 * Region-mismatch errors (`PermanentRedirect`) and `NoSuchBucket` both land
 * here; the log line names bucket, region, endpoint, and the underlying SDK
 * message so operators can tell the two apart without reading the trace.
 */
@Component
@ConditionalOnProperty(prefix = "plugwerk.storage", name = ["type"], havingValue = "s3")
class S3BucketStartupCheck(private val s3Client: S3Client, private val properties: PlugwerkProperties) :
    ApplicationRunner {

    private val log = LoggerFactory.getLogger(S3BucketStartupCheck::class.java)

    override fun run(args: ApplicationArguments) {
        val s3 = checkNotNull(properties.storage.s3) {
            "plugwerk.storage.s3 must be configured when plugwerk.storage.type=s3"
        }
        val bucket = s3.bucket

        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(bucket).build())
            log.info(
                "S3 bucket '{}' is reachable (region={}, endpoint={})",
                bucket,
                s3.region,
                s3.endpoint ?: "<aws-default>",
            )
        } catch (ex: Exception) {
            val msg = "S3 bucket '$bucket' is not reachable — region=${s3.region}, " +
                "endpoint=${s3.endpoint ?: "<aws-default>"}, cause: ${ex.message}. " +
                "Verify the bucket exists, the region matches, and the configured " +
                "credentials have HeadBucket permission."
            if (s3.failFastOnBucketMissing) {
                log.error("$msg [fail-fast-on-bucket-missing=true → refusing to start]", ex)
                throw IllegalStateException(msg, ex)
            }
            log.error(msg, ex)
        }
    }
}
