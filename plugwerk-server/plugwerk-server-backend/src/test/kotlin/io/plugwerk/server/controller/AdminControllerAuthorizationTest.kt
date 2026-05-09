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

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.security.access.prepost.PreAuthorize

/**
 * Reflection-based defense-in-depth check (#487).
 *
 * Every admin / role-gated controller method in the project is supposed to carry
 * BOTH a programmatic guard call (`namespaceAuthorizationService.requireSuperadmin(...)`
 * or `requireRole(...)`) AND a `@PreAuthorize` annotation that mirrors the same
 * authority. The two checks are redundant on purpose: if a future refactor removes
 * one (e.g. extracts the body into a helper, or changes the guard call), the other
 * still keeps the endpoint protected. See `MethodSecurityConfiguration` doc.
 *
 * This test class enumerates the historically-asymmetric methods (the `list*`
 * counterparts to mutating methods that already had `@PreAuthorize`) and asserts
 * the annotation is present with the expected SpEL expression. A regression here
 * means somebody silently dropped the annotation — which would not break any
 * behavioural test today (because `requireSuperadmin/requireRole` still fires)
 * but would re-open the same defense-in-depth gap that #487 was filed for.
 *
 * The test deliberately does NOT instantiate Spring or load the security
 * filter chain — it asks the JVM directly: "does the source carry this
 * annotation?". That keeps it fast and unambiguous about what it asserts.
 */
class AdminControllerAuthorizationTest {

    private val superadminExpression = "@namespaceAuthorizationService.isCurrentUserSuperadmin()"
    private val nsAdminExpression = "@namespaceAuthorizationService.hasRole(#ns, 'ADMIN')"

    @Test
    fun `AdminSettingsController#listApplicationSettings carries the superadmin PreAuthorize`() {
        assertPreAuthorize(AdminSettingsController::class.java, "listApplicationSettings", superadminExpression)
    }

    @Test
    fun `AdminUserController#listUsers carries the superadmin PreAuthorize`() {
        assertPreAuthorize(AdminUserController::class.java, "listUsers", superadminExpression)
    }

    @Test
    fun `OidcProviderController#listOidcProviders carries the superadmin PreAuthorize`() {
        assertPreAuthorize(OidcProviderController::class.java, "listOidcProviders", superadminExpression)
    }

    @Test
    fun `AccessKeyController#listAccessKeys carries the namespace-admin PreAuthorize`() {
        assertPreAuthorize(AccessKeyController::class.java, "listAccessKeys", nsAdminExpression)
    }

    @Test
    fun `NamespaceMemberController#listNamespaceMembers carries the namespace-admin PreAuthorize`() {
        assertPreAuthorize(NamespaceMemberController::class.java, "listNamespaceMembers", nsAdminExpression)
    }

    @Test
    fun `ReviewsController#listPendingReviews carries the namespace-admin PreAuthorize`() {
        // Hardened from MEMBER → ADMIN as part of the #487 sweep so the read side
        // matches approveRelease / rejectRelease (also ADMIN-only).
        assertPreAuthorize(ReviewsController::class.java, "listPendingReviews", nsAdminExpression)
    }

    private fun assertPreAuthorize(controller: Class<*>, methodName: String, expectedExpression: String) {
        // Lookup by name only — every controller in scope has exactly one method per
        // signature listed above, so collisions are not a concern. Matching the param
        // list explicitly would couple this test to OpenAPI-generated signatures that
        // change frequently for unrelated reasons.
        val method = controller.declaredMethods.firstOrNull { it.name == methodName }
            ?: error("$methodName not found on ${controller.simpleName}")
        val annotation = method.getAnnotation(PreAuthorize::class.java)
        assertThat(annotation)
            .withFailMessage(
                "$methodName on ${controller.simpleName} is missing @PreAuthorize. " +
                    "Defense-in-depth requires both the programmatic guard inside the body " +
                    "AND this annotation. See #487 and MethodSecurityConfiguration doc.",
            )
            .isNotNull
        assertThat(annotation.value)
            .withFailMessage(
                "$methodName on ${controller.simpleName} has @PreAuthorize but the SpEL " +
                    "expression does not match the expected guard. Expected: '$expectedExpression', " +
                    "actual: '${annotation.value}'.",
            )
            .isEqualTo(expectedExpression)
    }
}
