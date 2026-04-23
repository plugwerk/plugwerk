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
package io.plugwerk.server.security

import io.plugwerk.server.service.UnauthorizedException
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder

/**
 * Returns the current [Authentication] from the [SecurityContextHolder] or throws
 * [UnauthorizedException] (→ HTTP 401) when none is present (KT-005..007 / #285).
 *
 * Use this in controller methods that are protected by the Spring Security filter chain.
 * Under normal operation the filter chain guarantees an `Authentication` for these paths
 * — but if a future refactor removes that guarantee, this function fails loudly with a
 * clean 401 instead of a raw `NullPointerException` and stack trace from a `!!` assertion.
 *
 * Previous call sites used `SecurityContextHolder.getContext().authentication!!` in 16
 * places across `ManagementController`, `ReviewsController`, `AccessKeyController`, and
 * `NamespaceMemberController`. Each was a latent NPE with no context.
 */
fun currentAuthentication(): Authentication = SecurityContextHolder.getContext().authentication
    ?: throw UnauthorizedException("Authentication required")
