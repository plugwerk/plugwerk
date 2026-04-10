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

import io.plugwerk.server.PlugwerkProperties
import io.plugwerk.server.service.VersionProvider
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1")
class ConfigController(private val properties: PlugwerkProperties, private val versionProvider: VersionProvider) {

    @GetMapping("/config")
    fun getServerConfig(): ResponseEntity<ServerConfigResponse> = ResponseEntity.ok(
        ServerConfigResponse(
            version = versionProvider.getVersion(),
            upload = ServerConfigResponse.UploadConfig(
                maxFileSizeMb = properties.upload.maxFileSizeMb,
            ),
        ),
    )

    data class ServerConfigResponse(val version: String, val upload: UploadConfig) {
        data class UploadConfig(val maxFileSizeMb: Int)
    }
}
