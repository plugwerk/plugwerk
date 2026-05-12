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
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.http.apache.ApacheHttpClient
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.S3Configuration
import java.net.URI

/**
 * Wires the AWS SDK v2 [S3Client] bean for the S3 storage backend (#191).
 *
 * Only loaded when `plugwerk.storage.type=s3` so a filesystem-only deployment
 * does not pay the SDK init cost or carry the S3 client into its bean graph.
 *
 * The hybrid credential strategy mirrors what the [PlugwerkProperties.StorageProperties.S3Properties]
 * KDoc documents: if `accessKey`/`secretKey` are both set, [StaticCredentialsProvider]
 * is used; if both are blank, the SDK's [DefaultCredentialsProvider] chain runs
 * (env, instance profile, IRSA, ECS task role). The half-configured case is
 * rejected at startup by the @AssertTrue validator on `S3Properties`.
 */
@Configuration
@ConditionalOnProperty(prefix = "plugwerk.storage", name = ["type"], havingValue = "s3")
class S3ClientConfig {

    private val log = LoggerFactory.getLogger(S3ClientConfig::class.java)

    @Bean(destroyMethod = "close")
    fun s3Client(properties: PlugwerkProperties): S3Client {
        val s3 = checkNotNull(properties.storage.s3) {
            "plugwerk.storage.s3 must be configured when plugwerk.storage.type=s3 " +
                "— this should have been caught by the @AssertTrue validator on " +
                "StorageProperties.isS3ConfigPresentWhenS3Selected; please file a bug if you see this"
        }

        val credentialsProvider = resolveCredentialsProvider(s3)

        val builder = S3Client.builder()
            .region(Region.of(s3.region))
            .credentialsProvider(credentialsProvider)
            .httpClient(ApacheHttpClient.builder().build())
            .serviceConfiguration(
                S3Configuration.builder()
                    .pathStyleAccessEnabled(s3.pathStyleAccess)
                    .build(),
            )

        if (!s3.endpoint.isNullOrBlank()) {
            builder.endpointOverride(URI.create(s3.endpoint))
        }

        log.info(
            "S3 client initialised: bucket={}, region={}, endpoint={}, pathStyleAccess={}, " +
                "credentialsProvider={}",
            s3.bucket,
            s3.region,
            s3.endpoint ?: "<aws-default>",
            s3.pathStyleAccess,
            credentialsProvider.javaClass.simpleName,
        )
        return builder.build()
    }

    private fun resolveCredentialsProvider(
        s3: PlugwerkProperties.StorageProperties.S3Properties,
    ): AwsCredentialsProvider {
        val accessKey = s3.accessKey
        val secretKey = s3.secretKey
        return if (!accessKey.isNullOrBlank() && !secretKey.isNullOrBlank()) {
            StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey))
        } else {
            DefaultCredentialsProvider.create()
        }
    }
}
