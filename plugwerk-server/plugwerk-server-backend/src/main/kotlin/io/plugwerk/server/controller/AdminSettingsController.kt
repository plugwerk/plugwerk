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
package io.plugwerk.server.controller

import io.plugwerk.api.AdminSettingsApi
import io.plugwerk.api.model.ApplicationSettingDto
import io.plugwerk.api.model.ApplicationSettingsResponse
import io.plugwerk.api.model.ApplicationSettingsUpdateRequest
import io.plugwerk.server.security.NamespaceAuthorizationService
import io.plugwerk.server.service.UnauthorizedException
import io.plugwerk.server.service.settings.ApplicationSettingKey
import io.plugwerk.server.service.settings.ApplicationSettingsService
import io.plugwerk.server.service.settings.SettingSnapshot
import io.plugwerk.server.service.settings.SettingSource
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * REST controller for admin-managed application settings (ADR-0016).
 *
 * All endpoints require superadmin privileges. Regular namespace admins do not have access
 * because these settings are process-wide, not namespace-scoped.
 *
 * The controller delegates the entire read/write/validate pipeline to
 * [ApplicationSettingsService]; its job is translation between the OpenAPI-generated DTOs and
 * the service's internal [SettingSnapshot] type.
 */
@RestController
@RequestMapping("/api/v1")
class AdminSettingsController(
    private val settingsService: ApplicationSettingsService,
    private val namespaceAuthorizationService: NamespaceAuthorizationService,
) : AdminSettingsApi {

    override fun listApplicationSettings(): ResponseEntity<ApplicationSettingsResponse> {
        requireSuperadmin()
        return ResponseEntity.ok(buildResponse())
    }

    @PreAuthorize("@namespaceAuthorizationService.isCurrentUserSuperadmin()")
    override fun updateApplicationSettings(
        applicationSettingsUpdateRequest: ApplicationSettingsUpdateRequest,
    ): ResponseEntity<ApplicationSettingsResponse> {
        val principal = requireSuperadmin()
        val updates = applicationSettingsUpdateRequest.settings
        for ((rawKey, rawValue) in updates) {
            val key = ApplicationSettingKey.byKey(rawKey)
                ?: throw IllegalArgumentException("Unknown setting key: '$rawKey'")
            settingsService.update(key, rawValue, updatedBy = principal)
        }
        return ResponseEntity.ok(buildResponse())
    }

    private fun requireSuperadmin(): String {
        val auth = SecurityContextHolder.getContext().authentication
            ?: throw UnauthorizedException("Not authenticated")
        namespaceAuthorizationService.requireSuperadmin(auth)
        return auth.name
    }

    private fun buildResponse(): ApplicationSettingsResponse =
        ApplicationSettingsResponse(settings = settingsService.listAll().map { it.toDto() })

    private fun SettingSnapshot.toDto(): ApplicationSettingDto = ApplicationSettingDto(
        key = key.key,
        value = value,
        valueType = ApplicationSettingDto.ValueType.valueOf(key.valueType.name),
        source = when (source) {
            SettingSource.DATABASE -> ApplicationSettingDto.Source.DATABASE
            SettingSource.DEFAULT -> ApplicationSettingDto.Source.DEFAULT
        },
        requiresRestart = key.requiresRestart,
        restartPending = restartPending,
        description = description,
        allowedValues = key.allowedValues?.toList(),
        minInt = key.minInt,
        maxInt = key.maxInt,
    )
}
