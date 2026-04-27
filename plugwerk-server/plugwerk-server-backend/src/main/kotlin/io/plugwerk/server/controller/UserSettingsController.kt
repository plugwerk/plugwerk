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

import io.plugwerk.api.UserSettingsApi
import io.plugwerk.api.model.UserSettingsResponse
import io.plugwerk.api.model.UserSettingsUpdateRequest
import io.plugwerk.server.security.CurrentUserResolver
import io.plugwerk.server.service.settings.UserSettingsService
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1")
class UserSettingsController(
    private val userSettingsService: UserSettingsService,
    private val currentUserResolver: CurrentUserResolver,
) : UserSettingsApi {

    override fun getUserSettings(): ResponseEntity<UserSettingsResponse> {
        // CurrentUserResolver throws UnauthorizedException (→ 401) when the
        // request lacks an auth or carries a non-UUID subject. API-key callers
        // present `key:<id>` as their auth name; that fails the UUID parse
        // and gets the same 401 the previous explicit guard returned.
        val userId = currentUserResolver.currentUserId()
        val settings = userSettingsService.getAll(userId)
        return ResponseEntity.ok(UserSettingsResponse(settings = settings))
    }

    @PreAuthorize("isAuthenticated() and !authentication.name.startsWith('key:')")
    override fun updateUserSettings(
        userSettingsUpdateRequest: UserSettingsUpdateRequest,
    ): ResponseEntity<UserSettingsResponse> {
        val userId = currentUserResolver.currentUserId()
        val updated = userSettingsService.update(userId, userSettingsUpdateRequest.settings)
        return ResponseEntity.ok(UserSettingsResponse(settings = updated))
    }
}
