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
package io.plugwerk.server.service.telemetry

import io.plugwerk.server.PlugwerkProperties
import okhttp3.mockwebserver.MockWebServer
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Pins the fail-open guards of [HttpTelemetrySender] — the branches that keep a
 * misconfigured telemetry endpoint from ever affecting the server (ADR-0039).
 *
 * The endpoint is pointed at a live MockWebServer so we can assert that a skip
 * decision produces **zero** network traffic, not merely that `send()` returns.
 * The HTTPS happy-path POST is covered by the Spring integration test
 * (`TelemetryBeaconIT`); it needs a TLS endpoint and is out of scope here.
 */
class HttpTelemetrySenderTest {

    private lateinit var server: MockWebServer

    private val payload = TelemetryPayload(
        installId = "00000000-0000-0000-0000-000000000000",
        version = "1.0.0",
        installType = "docker",
        event = TelemetryEvent.HEARTBEAT.wireValue,
    )

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    private fun sender(endpoint: String): HttpTelemetrySender = HttpTelemetrySender(
        PlugwerkProperties(
            telemetry = PlugwerkProperties.TelemetryProperties(endpoint = endpoint),
        ),
    )

    @Test
    fun `skips sending when the endpoint is empty`() {
        assertThatCode { sender("").send(payload) }.doesNotThrowAnyException()
        assertThat(server.requestCount).isZero()
    }

    @Test
    fun `skips sending when the endpoint is blank whitespace`() {
        assertThatCode { sender("   ").send(payload) }.doesNotThrowAnyException()
        assertThat(server.requestCount).isZero()
    }

    @Test
    fun `refuses to send over plain HTTP - no request reaches the server`() {
        val httpEndpoint = server.url("/telemetry").toString()

        assertThatCode { sender(httpEndpoint).send(payload) }.doesNotThrowAnyException()

        assertThat(httpEndpoint).startsWith("http://")
        assertThat(server.requestCount).isZero()
    }

    @Test
    fun `HTTPS scheme check is case-insensitive - an uppercase HTTP URL is still refused`() {
        val upperHttp = server.url("/telemetry").toString().replaceFirst("http://", "HTTP://")

        assertThatCode { sender(upperHttp).send(payload) }.doesNotThrowAnyException()

        assertThat(server.requestCount).isZero()
    }
}
