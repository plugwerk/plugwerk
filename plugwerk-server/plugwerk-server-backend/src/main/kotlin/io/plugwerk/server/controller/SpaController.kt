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

import jakarta.servlet.http.HttpServletRequest
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping

/**
 * Catch-all controller that forwards all non-API, non-static requests to the React SPA.
 * This is required for client-side routing (React Router) to work when the user navigates
 * directly to a deep link or refreshes the browser.
 */
@Controller
class SpaController {

    @GetMapping(
        value = [
            "/",
            "/login",
            "/change-password",
            "/register",
            "/forgot-password",
            "/reset-password",
            "/admin/**",
            "/403",
            "/500",
            "/503",
            "/namespaces/{namespace}/plugins",
            "/namespaces/{namespace}/plugins/{pluginId}",
            "/api-docs",
        ],
    )
    fun spa(request: HttpServletRequest): String = "forward:/index.html"
}
