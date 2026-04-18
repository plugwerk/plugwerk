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
package io.plugwerk.server.config

import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity

/**
 * Enables AOP-based method-level authorization (`@PreAuthorize`).
 *
 * `prePostEnabled = true` is the default in Spring Security 7, so `@PreAuthorize` and
 * `@PostAuthorize` evaluation is active without any further configuration.
 *
 * Every mutating REST endpoint carries a `@PreAuthorize` that mirrors the programmatic
 * `requireRole` / `requireSuperadmin` call made inside the method body. The programmatic
 * calls stay in place as defence-in-depth; this configuration adds the AOP safety net.
 *
 * Kept as a standalone configuration (separate from [SecurityConfiguration]) so the
 * filter-chain bean stays focused and test slices can opt into method-security
 * independently.
 */
@Configuration
@EnableMethodSecurity
class MethodSecurityConfiguration
