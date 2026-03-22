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
package io.plugwerk.server.controller

import io.plugwerk.server.service.NamespaceService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class NamespaceSummary(val slug: String, val ownerOrg: String)

@RestController
@RequestMapping("/api/v1/namespaces")
class NamespaceController(private val namespaceService: NamespaceService) {

    @GetMapping
    fun listNamespaces(): ResponseEntity<List<NamespaceSummary>> {
        val namespaces = namespaceService.findAll()
            .map { NamespaceSummary(slug = it.slug, ownerOrg = it.ownerOrg) }
        return ResponseEntity.ok(namespaces)
    }
}
