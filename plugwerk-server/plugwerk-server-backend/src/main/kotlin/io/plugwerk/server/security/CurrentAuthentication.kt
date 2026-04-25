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
 *
 * @see currentAuthenticationOrNull for code paths that have a legitimate anonymous
 *   fallback (e.g. catalog visibility downgrade).
 * @see currentAuthenticationOrElse for single-expression bodies that branch on auth
 *   presence with a fixed default.
 */
fun currentAuthentication(): Authentication = SecurityContextHolder.getContext().authentication
    ?: throw UnauthorizedException("Authentication required")

/**
 * Returns the current [Authentication] from the [SecurityContextHolder], or `null` when
 * no authentication is present. Use this when an absent authentication is **a legitimate
 * state** the caller wants to react to — typically by falling back to a typed default
 * via the elvis operator:
 *
 * ```kotlin
 * fun resolveVisibility(ns: String): CatalogVisibility {
 *   val auth = currentAuthenticationOrNull() ?: return CatalogVisibility.PUBLIC
 *   …
 * }
 * ```
 *
 * Picks up the same call shape as Kotlin's `firstOrNull` / `singleOrNull` family.
 */
fun currentAuthenticationOrNull(): Authentication? = SecurityContextHolder.getContext().authentication

/**
 * Returns [block]`(authentication)` when an [Authentication] is present in the
 * [SecurityContextHolder], otherwise returns [default]. Designed for single-expression
 * function bodies that derive a typed value from the (possibly absent) auth:
 *
 * ```kotlin
 * fun hasRole(slug: String, role: NamespaceRole): Boolean =
 *   currentAuthenticationOrElse(default = false) { auth ->
 *     try { requireRole(slug, auth, role); true }
 *     catch (_: ForbiddenException) { false }
 *     catch (_: NamespaceNotFoundException) { false }
 *   }
 * ```
 *
 * Generic on `T` via `inline` — the compiler specialises per call site so no `Any` cast
 * is needed and the return type matches whatever default the caller passes. Prefer
 * [currentAuthenticationOrNull] when the function body has multiple early returns sharing
 * the same default — using `OrElse` there would force `return@currentAuthenticationOrElse`
 * labels and read worse than the elvis pattern.
 */
inline fun <T> currentAuthenticationOrElse(default: T, block: (Authentication) -> T): T =
    SecurityContextHolder.getContext().authentication?.let(block) ?: default
