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
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.UpdateTimestamp
import org.hibernate.annotations.UuidGenerator
import java.time.OffsetDateTime
import java.util.UUID

/**
 * Scalar value type stored in [ApplicationSettingEntity.valueType].
 *
 * Determines how [ApplicationSettingEntity.settingValue] (stored as `text`) is parsed back
 * into a typed runtime value. See `ApplicationSettingKey` in the service layer for the per-key mapping.
 */
enum class SettingValueType {
    STRING,
    INTEGER,
    BOOLEAN,
    ENUM,
}

/**
 * JPA entity that persists a single global application setting (ADR-0016).
 *
 * Every admin-manageable setting is stored as one row in the `application_setting` table.
 * The table is the single source of truth for admin-manageable values — there is no
 * `application.yml` fallback for settings managed through this entity.
 *
 * Defaults are seeded by Liquibase migration `0005_application_settings.yaml` on first
 * installation. Subsequent installations keep the existing row values.
 *
 * @property id Primary key, UUIDv7 (chronologically ordered — see ADR-0003).
 * @property settingKey Stable dotted identifier, e.g. `upload.max_file_size_mb`. Unique.
 * @property settingValue Stringified value. `null` means "unset — fall back to the key's
 *   hard-coded default in `ApplicationSettingKey`". Parsing is driven by [valueType].
 * @property settingDesc Human-readable description of the setting. Seeded by Liquibase with
 *   a sensible default for every known key and displayed in the Admin UI as inline help.
 *   Nullable because a row may exist for an unknown/legacy key without a description.
 * @property valueType Discriminator used by the service layer to parse [settingValue].
 * @property updatedAt Last-write timestamp (set automatically on insert and every update).
 * @property updatedBy Principal name of the user who last wrote this row. `null` for rows
 *   that were seeded by Liquibase or have never been modified through the admin API.
 */
@Entity
@Table(name = "application_setting")
class ApplicationSettingEntity(
    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(name = "id", updatable = false)
    var id: UUID? = null,

    @Column(name = "setting_key", nullable = false, unique = true, length = 128, updatable = false)
    var settingKey: String,

    @Column(name = "setting_value", nullable = true, columnDefinition = "text")
    var settingValue: String? = null,

    @Column(name = "setting_desc", nullable = true, columnDefinition = "text")
    var settingDesc: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "value_type", nullable = false, length = 16)
    var valueType: SettingValueType,

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    var updatedAt: OffsetDateTime = OffsetDateTime.now(),

    @Column(name = "updated_by", nullable = true, length = 255)
    var updatedBy: String? = null,
)
