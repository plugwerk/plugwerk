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

import io.plugwerk.server.domain.DownloadEventEntity
import io.plugwerk.server.domain.NamespaceEntity
import io.plugwerk.server.domain.PluginEntity
import io.plugwerk.server.domain.PluginReleaseEntity
import io.plugwerk.server.repository.DownloadEventRepository
import io.plugwerk.server.service.settings.ApplicationSettingsService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DownloadEventServiceTest {

    private lateinit var downloadEventRepository: DownloadEventRepository
    private lateinit var service: DownloadEventService

    private val namespace = NamespaceEntity(slug = "acme", name = "ACME Corp")
    private val plugin = PluginEntity(namespace = namespace, pluginId = "my-plugin", name = "My Plugin")
    private val release = PluginReleaseEntity(
        plugin = plugin,
        version = "1.0.0",
        artifactSha256 = "sha",
        artifactKey = "acme:my-plugin:1.0.0:jar",
    )

    private fun buildSettings(
        enabled: Boolean = true,
        captureIp: Boolean = true,
        anonymizeIp: Boolean = true,
        captureUserAgent: Boolean = true,
    ): ApplicationSettingsService {
        val settings = mock<ApplicationSettingsService>()
        whenever(settings.trackingEnabled()).thenReturn(enabled)
        whenever(settings.trackingCaptureIp()).thenReturn(captureIp)
        whenever(settings.trackingAnonymizeIp()).thenReturn(anonymizeIp)
        whenever(settings.trackingCaptureUserAgent()).thenReturn(captureUserAgent)
        return settings
    }

    @BeforeEach
    fun setUp() {
        downloadEventRepository = mock()
        service = DownloadEventService(downloadEventRepository, buildSettings())
    }

    @Test
    fun `record saves event when tracking is enabled`() {
        whenever(downloadEventRepository.save(any<DownloadEventEntity>())).thenAnswer { it.getArgument(0) }

        service.record(release, "192.168.1.42", "Mozilla/5.0")

        val captor = argumentCaptor<DownloadEventEntity>()
        verify(downloadEventRepository).save(captor.capture())
        assertThat(captor.firstValue.release).isEqualTo(release)
        assertThat(captor.firstValue.clientIp).isEqualTo("192.168.1.0")
        assertThat(captor.firstValue.userAgent).isEqualTo("Mozilla/5.0")
    }

    @Test
    fun `record does nothing when tracking is disabled`() {
        service = DownloadEventService(downloadEventRepository, buildSettings(enabled = false))

        service.record(release, "192.168.1.42", "Mozilla/5.0")

        verify(downloadEventRepository, never()).save(any<DownloadEventEntity>())
    }

    @Test
    fun `record anonymizes IPv4 to slash 24`() {
        whenever(downloadEventRepository.save(any<DownloadEventEntity>())).thenAnswer { it.getArgument(0) }

        service.record(release, "10.20.30.40", null)

        val captor = argumentCaptor<DownloadEventEntity>()
        verify(downloadEventRepository).save(captor.capture())
        assertThat(captor.firstValue.clientIp).isEqualTo("10.20.30.0")
    }

    @Test
    fun `record anonymizes IPv6 to slash 48`() {
        whenever(downloadEventRepository.save(any<DownloadEventEntity>())).thenAnswer { it.getArgument(0) }

        service.record(release, "2001:db8:85a3:1234:5678:abcd:ef01:2345", null)

        val captor = argumentCaptor<DownloadEventEntity>()
        verify(downloadEventRepository).save(captor.capture())
        assertThat(captor.firstValue.clientIp).isEqualTo("2001:db8:85a3:0:0:0:0:0")
    }

    @Test
    fun `record stores raw IP when anonymization is disabled`() {
        service = DownloadEventService(downloadEventRepository, buildSettings(anonymizeIp = false))
        whenever(downloadEventRepository.save(any<DownloadEventEntity>())).thenAnswer { it.getArgument(0) }

        service.record(release, "192.168.1.42", null)

        val captor = argumentCaptor<DownloadEventEntity>()
        verify(downloadEventRepository).save(captor.capture())
        assertThat(captor.firstValue.clientIp).isEqualTo("192.168.1.42")
    }

    @Test
    fun `record nullifies IP when captureIp is false`() {
        service = DownloadEventService(downloadEventRepository, buildSettings(captureIp = false))
        whenever(downloadEventRepository.save(any<DownloadEventEntity>())).thenAnswer { it.getArgument(0) }

        service.record(release, "192.168.1.42", "Mozilla/5.0")

        val captor = argumentCaptor<DownloadEventEntity>()
        verify(downloadEventRepository).save(captor.capture())
        assertThat(captor.firstValue.clientIp).isNull()
    }

    @Test
    fun `record nullifies user agent when captureUserAgent is false`() {
        service = DownloadEventService(downloadEventRepository, buildSettings(captureUserAgent = false))
        whenever(downloadEventRepository.save(any<DownloadEventEntity>())).thenAnswer { it.getArgument(0) }

        service.record(release, null, "Mozilla/5.0")

        val captor = argumentCaptor<DownloadEventEntity>()
        verify(downloadEventRepository).save(captor.capture())
        assertThat(captor.firstValue.userAgent).isNull()
    }

    @Test
    fun `anonymizeIpAddress handles invalid input gracefully`() {
        val result = DownloadEventService.anonymizeIpAddress("not-an-ip")
        assertThat(result).isEqualTo("not-an-ip")
    }
}
