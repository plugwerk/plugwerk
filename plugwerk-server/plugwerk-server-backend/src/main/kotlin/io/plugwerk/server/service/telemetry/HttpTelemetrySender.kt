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
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.time.Duration

/**
 * Sends the telemetry payload over HTTPS to the configured, Plugwerk-owned
 * endpoint (DEV-23 / ADR-0039).
 *
 * Two guards keep this fail-open and safe:
 *  - **Short timeouts** ([CONNECT_TIMEOUT] / [READ_TIMEOUT]) cap the worst-case
 *    blocking time so a hung endpoint cannot stall the scheduler thread (the
 *    first-start beacon is additionally dispatched off the startup thread by
 *    [TelemetryBeacon]).
 *  - **HTTPS-only** — a blank or non-`https://` endpoint is treated as "not
 *    configured" and the send is skipped with a debug log. This is enforced here
 *    rather than as startup bean validation precisely so a misconfigured
 *    endpoint can never crash the server (fail-open beats fail-fast for
 *    telemetry).
 *
 * A reachable-but-erroring endpoint (4xx/5xx) makes `retrieve()` throw; that
 * exception propagates to [TelemetryBeacon], which swallows it. This class never
 * has to catch anything itself.
 */
@Component
class HttpTelemetrySender(private val properties: PlugwerkProperties) : TelemetrySender {

    private val log = LoggerFactory.getLogger(HttpTelemetrySender::class.java)

    // Spring Boot does not autoconfigure a RestClient.Builder bean in our
    // dependency set (see GitHubEmailFetcher for the same note), so the client
    // is built directly here with an explicitly-bounded request factory.
    private val restClient: RestClient = RestClient.builder()
        .requestFactory(
            SimpleClientHttpRequestFactory().apply {
                setConnectTimeout(CONNECT_TIMEOUT)
                setReadTimeout(READ_TIMEOUT)
            },
        )
        .build()

    override fun send(payload: TelemetryPayload) {
        val endpoint = properties.telemetry.endpoint.trim()
        if (endpoint.isEmpty()) {
            log.debug("telemetry endpoint not configured; skipping {} beacon", payload.event)
            return
        }
        if (!endpoint.startsWith("https://", ignoreCase = true)) {
            log.debug("telemetry endpoint is not HTTPS; refusing to send {} beacon", payload.event)
            return
        }
        restClient.post()
            .uri(endpoint)
            .contentType(MediaType.APPLICATION_JSON)
            .body(payload)
            .retrieve()
            .toBodilessEntity()
        log.debug("telemetry {} beacon sent", payload.event)
    }

    companion object {
        private val CONNECT_TIMEOUT: Duration = Duration.ofSeconds(2)
        private val READ_TIMEOUT: Duration = Duration.ofSeconds(2)
    }
}
