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
package io.plugwerk.server.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.OnDelete
import org.hibernate.annotations.OnDeleteAction
import org.hibernate.annotations.UuidGenerator
import java.time.OffsetDateTime
import java.util.UUID

/**
 * JPA entity recording an individual plugin artifact download.
 *
 * Each row represents one download event and is linked to the downloaded
 * [PluginReleaseEntity]. This table supplements the atomic `download_count`
 * counter on `plugin_release` with detailed audit data.
 *
 * Privacy: the [clientIp] is anonymised by default (see `plugwerk.tracking.anonymize-ip`)
 * and both [clientIp] and [userAgent] capture can be disabled entirely via configuration.
 */
@Entity
@Table(name = "download_event")
class DownloadEventEntity(
    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(name = "id", updatable = false)
    var id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "release_id", nullable = false, updatable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    var release: PluginReleaseEntity,

    @CreationTimestamp
    @Column(name = "downloaded_at", nullable = false, updatable = false)
    var downloadedAt: OffsetDateTime = OffsetDateTime.now(),

    @Column(name = "client_ip", length = 45)
    var clientIp: String? = null,

    @Column(name = "user_agent")
    var userAgent: String? = null,
)
