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

import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait

/**
 * Shared MinIO Testcontainer for S3-backend integration tests (#191).
 *
 * Singleton: spun up once per Gradle test JVM, reused across every test class
 * that touches S3. Mirrors [SharedPostgresContainer]'s start-once pattern.
 *
 * Default credentials are MinIO's `minioadmin` / `minioadmin` — fine for
 * test isolation. Each test class is responsible for creating its own bucket
 * (or per-test prefixes) to avoid cross-test interference.
 *
 * Image is pinned: refreshing requires a deliberate version bump (and a
 * Testcontainers image cache eviction on CI).
 */
object SharedMinioContainer {
    const val ACCESS_KEY: String = "minioadmin"
    const val SECRET_KEY: String = "minioadmin"

    @Suppress("HttpUrlsUsage")
    val instance: GenericContainer<*> =
        GenericContainer("minio/minio:RELEASE.2024-12-18T13-15-44Z")
            .withCommand("server", "/data", "--console-address", ":9001")
            .withEnv("MINIO_ROOT_USER", ACCESS_KEY)
            .withEnv("MINIO_ROOT_PASSWORD", SECRET_KEY)
            .withExposedPorts(9000, 9001)
            .waitingFor(Wait.forHttp("/minio/health/live").forPort(9000))
            .apply { start() }

    val endpoint: String
        get() = "http://${instance.host}:${instance.getMappedPort(9000)}"
}
