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
package io.plugwerk.server.repository

import io.plugwerk.server.domain.ApplicationSettingEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional
import java.util.UUID

/**
 * Spring Data JPA repository for [ApplicationSettingEntity].
 *
 * One row per admin-manageable setting, keyed by the stable dotted identifier in
 * [ApplicationSettingEntity.settingKey]. The key column has a unique constraint, so
 * [findBySettingKey] returns at most one result.
 */
@Repository
interface ApplicationSettingRepository : JpaRepository<ApplicationSettingEntity, UUID> {

    /**
     * Looks up a single setting row by its dotted key.
     *
     * @param settingKey e.g. `upload.max_file_size_mb` or `tracking.enabled`.
     * @return the row if present, otherwise [Optional.empty]. An empty result means the
     *   setting has never been seeded — callers must fall back to the hard-coded default
     *   defined in `SettingKey`.
     */
    fun findBySettingKey(settingKey: String): Optional<ApplicationSettingEntity>
}
