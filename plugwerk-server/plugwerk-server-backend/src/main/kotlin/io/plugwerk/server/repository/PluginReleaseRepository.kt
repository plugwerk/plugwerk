/*
 * Plugwerk — Plugin Marketplace for the PF4J Ecosystem
 * Copyright (C) 2026 devtank42 GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package io.plugwerk.server.repository

import io.plugwerk.common.model.ReleaseStatus
import io.plugwerk.server.domain.PluginEntity
import io.plugwerk.server.domain.PluginReleaseEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional
import java.util.UUID

interface PluginReleaseRepository : JpaRepository<PluginReleaseEntity, UUID> {

    fun findByPluginAndVersion(plugin: PluginEntity, version: String): Optional<PluginReleaseEntity>

    fun findAllByPluginOrderByCreatedAtDesc(plugin: PluginEntity): List<PluginReleaseEntity>

    fun findAllByPluginAndStatus(plugin: PluginEntity, status: ReleaseStatus): List<PluginReleaseEntity>

    fun existsByPluginAndVersion(plugin: PluginEntity, version: String): Boolean
}
