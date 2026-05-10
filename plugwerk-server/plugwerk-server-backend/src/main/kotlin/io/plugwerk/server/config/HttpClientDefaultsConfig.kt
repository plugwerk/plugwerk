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

import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Configuration

/**
 * Sets short, bounded JVM-wide defaults for the legacy `URLConnection` HTTP
 * stack (#479). Spring Security 7 dropped the `.rest(RestOperations)` hook on
 * `ClientRegistrations.fromIssuerLocation` / `JwtDecoders.fromIssuerLocation`
 * — those classes use a `private static final RestTemplate` backed by
 * `SimpleClientHttpRequestFactory`, which honours these system properties.
 *
 * 5s / 5s caps the SSRF "did this port answer" oracle to a 10s worst case
 * per probe, short enough that error-based scanning is impractical without
 * being so tight that legitimate WAN-distant providers fail. Other Plugwerk
 * HTTP clients (OkHttp, JDK HttpClient if added later, Reactor Netty)
 * ignore these properties — collateral damage is limited to anything that
 * actually still uses `java.net.URLConnection`.
 *
 * Setting these as system properties (not just a `RestTemplate` bean) is
 * required because `ClientRegistrations.rest` and `JwtDecoders.rest` are
 * package-private static fields, not Spring-managed beans.
 */
@Configuration
class HttpClientDefaultsConfig {

    private val log = LoggerFactory.getLogger(HttpClientDefaultsConfig::class.java)

    @PostConstruct
    fun applyJvmDefaults() {
        val connectTimeoutMs = "5000"
        val readTimeoutMs = "5000"
        System.setProperty("sun.net.client.defaultConnectTimeout", connectTimeoutMs)
        System.setProperty("sun.net.client.defaultReadTimeout", readTimeoutMs)
        log.info(
            "URLConnection defaults set: connectTimeout={}ms, readTimeout={}ms",
            connectTimeoutMs,
            readTimeoutMs,
        )
    }
}
