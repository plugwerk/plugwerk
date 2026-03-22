/*
 * SPDX-License-Identifier: AGPL-3.0
 * Copyright (C) 2026 devtank42 GmbH
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
            "/register",
            "/forgot-password",
            "/reset-password",
            "/admin/**",
            "/403",
            "/500",
            "/503",
            "/{namespace}/plugins/{pluginId}",
        ],
    )
    fun spa(request: HttpServletRequest): String = "forward:/index.html"
}
