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

import io.plugwerk.api.AdminConfigurationApi
import io.plugwerk.server.security.NamespaceAuthorizationService
import io.plugwerk.server.security.currentAuthentication
import io.plugwerk.server.service.configuration.ConfigurationTreeBuilder
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import tools.jackson.databind.ObjectMapper

/**
 * Read-only admin endpoint that surfaces the effective `plugwerk.*`
 * configuration tree for the dashboard (#522). Superadmin-only with
 * the usual `@PreAuthorize` + inline `requireSuperadmin`
 * defense-in-depth pair.
 *
 * The endpoint is purely informational — operators change values in
 * `application.yml` / env-vars and restart the server.
 */
@RestController
@RequestMapping("/api/v1")
class AdminConfigurationController(
    private val treeBuilder: ConfigurationTreeBuilder,
    private val objectMapper: ObjectMapper,
    private val namespaceAuthorizationService: NamespaceAuthorizationService,
) : AdminConfigurationApi {

    @PreAuthorize("@namespaceAuthorizationService.isCurrentUserSuperadmin()")
    override fun getEffectiveConfiguration(): ResponseEntity<Map<String, Any>> {
        namespaceAuthorizationService.requireSuperadmin(currentAuthentication())
        val tree = treeBuilder.build()

        // Convert the redacted ObjectNode into the contract type
        // (`Map<String, Any>`) so the generated DTO and the response
        // match without an extra type-cast on the wire.
        @Suppress("UNCHECKED_CAST")
        val asMap = objectMapper.treeToValue(tree, Map::class.java) as Map<String, Any>
        return ResponseEntity.ok(asMap)
    }
}
