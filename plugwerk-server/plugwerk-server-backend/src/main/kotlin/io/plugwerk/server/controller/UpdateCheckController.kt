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

import io.plugwerk.api.UpdatesApi
import io.plugwerk.api.model.UpdateCheckRequest
import io.plugwerk.api.model.UpdateCheckResponse
import io.plugwerk.server.service.UpdateCheckService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1")
class UpdateCheckController(private val updateCheckService: UpdateCheckService) : UpdatesApi {

    override fun checkForUpdates(
        ns: String,
        updateCheckRequest: UpdateCheckRequest,
    ): ResponseEntity<UpdateCheckResponse> {
        val response = updateCheckService.checkUpdates(ns, updateCheckRequest.plugins)
        return ResponseEntity.ok(response)
    }
}
