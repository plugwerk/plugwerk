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
package io.plugwerk.server.service

import io.plugwerk.server.PlugwerkProperties
import io.plugwerk.server.domain.DownloadEventEntity
import io.plugwerk.server.domain.PluginReleaseEntity
import io.plugwerk.server.repository.DownloadEventRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.net.InetAddress

/**
 * Records download events in the audit log table.
 *
 * Runs in its own transaction ([Propagation.REQUIRES_NEW]) so that a failure to record
 * an event never rolls back the actual artifact download. The service respects the
 * `plugwerk.tracking.*` configuration for privacy-aware data capture.
 */
@Service
class DownloadEventService(
    private val downloadEventRepository: DownloadEventRepository,
    private val properties: PlugwerkProperties,
) {

    private val log = LoggerFactory.getLogger(DownloadEventService::class.java)

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun record(release: PluginReleaseEntity, clientIp: String?, userAgent: String?) {
        if (!properties.tracking.enabled) return

        val ip = when {
            !properties.tracking.captureIp || clientIp == null -> null
            properties.tracking.anonymizeIp -> anonymizeIpAddress(clientIp)
            else -> clientIp
        }

        val agent = if (properties.tracking.captureUserAgent) userAgent else null

        downloadEventRepository.save(
            DownloadEventEntity(
                release = release,
                clientIp = ip,
                userAgent = agent,
            ),
        )
    }

    companion object {
        /**
         * Anonymises an IP address by zeroing the host portion.
         * - IPv4: zeroes the last octet (→ /24 subnet), e.g. `192.168.1.42` → `192.168.1.0`
         * - IPv6: zeroes the last 80 bits (→ /48 subnet), e.g. `2001:db8:85a3::1` → `2001:db8:85a3::`
         *
         * Returns the original string if parsing fails (fail-open for analytics data).
         */
        internal fun anonymizeIpAddress(ip: String): String = try {
            val addr = InetAddress.getByName(ip)
            val bytes = addr.address
            if (bytes.size == 4) {
                // IPv4: zero last octet → /24
                bytes[3] = 0
            } else {
                // IPv6: zero last 10 bytes → /48
                for (i in 6 until 16) {
                    bytes[i] = 0
                }
            }
            InetAddress.getByAddress(bytes).hostAddress
        } catch (e: Exception) {
            LoggerFactory.getLogger(DownloadEventService::class.java)
                .warn("Failed to anonymize IP address, storing raw value", e)
            ip
        }
    }
}
