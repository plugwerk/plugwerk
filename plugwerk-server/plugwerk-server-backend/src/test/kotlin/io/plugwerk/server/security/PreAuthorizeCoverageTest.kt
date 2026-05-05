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

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider
import org.springframework.core.type.filter.AnnotationTypeFilter
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RestController
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/**
 * Regression guard for audit finding SBS-AUTHZ-001..009 (issue #257).
 *
 * Scans every `@RestController` in `io.plugwerk.server.controller` for mutating HTTP
 * handlers (POST / PUT / PATCH / DELETE). Each handler must carry `@PreAuthorize` either
 * on the method itself or on the declaring class, or be explicitly allow-listed here.
 *
 * Handler HTTP-method metadata lives on the OpenAPI-generated `*Api` interface
 * (`@RequestMapping(method = [RequestMethod.POST])`), not on the implementation — the
 * test walks the interface hierarchy when needed.
 *
 * This test runs as a plain unit test (no Spring context needed) so it is cheap to
 * execute on every build.
 */
class PreAuthorizeCoverageTest {

    /**
     * Handlers that intentionally do NOT carry `@PreAuthorize`. Every entry must be
     * accompanied by a written justification — these are reviewed on every audit.
     */
    private val allowList: Set<String> = setOf(
        // AuthController.login — unauthenticated endpoint by design (credential exchange).
        "AuthController.login",
        // AuthController.refresh — unauthenticated endpoint; token-bound via refresh cookie.
        "AuthController.refresh",
        // AuthController.logout — authentication enforcement lives in the security filter chain.
        "AuthController.logout",
        // AuthController.changePassword — authentication is enforced by the URL-level
        // requestMatchers(...).authenticated() rule in SecurityConfiguration; the operation
        // is scoped to the caller's own subject inside the controller body.
        "AuthController.changePassword",
        // AuthRegistrationController.register — public self-registration (#420).
        // Gated at the controller-body level by an ApplicationSettings check
        // that throws 404 when self-registration is disabled, so the endpoint
        // is invisible on locked-down deployments. Rate-limited per IP and
        // per email (RegisterRateLimitFilter + RegisterRateLimitService).
        "AuthRegistrationController.register",
        // AuthPasswordResetController.forgotPassword — public forgot-password (#421).
        // Gated at the controller-body level by ApplicationSettings + 404 disguise;
        // anti-enumeration response is a uniform 204 across all branches.
        // IP-keyed rate-limit in PasswordResetRateLimitFilter.
        "AuthPasswordResetController.forgotPassword",
        // AuthPasswordResetController.resetPassword — public reset-password (#421).
        // Gated by the same 404 disguise. Authorization is "presented a valid
        // single-use token", which is exactly the security model.
        // Token-keyed rate-limit fires inside the controller, IP-keyed in the filter.
        "AuthPasswordResetController.resetPassword",
        // UpdateCheckController.checkForUpdates — intentionally public: PF4J client plugins
        // submit their installed-plugin list (no secrets) and the server answers with
        // available updates. Anonymous callers are allowed by design (see
        // UpdateCheckEndpointAuthzTest). POST is used only because the batch payload needs
        // a request body; there is no authorization gate.
        "UpdateCheckController.checkForUpdates",
    )

    private val mutatingMethods = setOf(
        RequestMethod.POST,
        RequestMethod.PUT,
        RequestMethod.PATCH,
        RequestMethod.DELETE,
    )

    @Test
    fun `every mutating endpoint has @PreAuthorize or is on the allow-list`() {
        val scanner = ClassPathScanningCandidateComponentProvider(false).apply {
            addIncludeFilter(AnnotationTypeFilter(RestController::class.java))
        }
        val controllers: List<Class<*>> = scanner
            .findCandidateComponents("io.plugwerk.server.controller")
            .map { Class.forName(it.beanClassName) }

        val violations = mutableListOf<String>()

        for (controller in controllers) {
            for (method in controller.declaredMethods) {
                if (!Modifier.isPublic(method.modifiers)) continue
                if (method.isSynthetic || method.isBridge) continue

                if (!isMutatingHandler(method)) continue

                val hasPreAuthorize = method.isAnnotationPresent(PreAuthorize::class.java) ||
                    controller.isAnnotationPresent(PreAuthorize::class.java)

                val key = "${controller.simpleName}.${method.name}"
                if (!hasPreAuthorize && key !in allowList) {
                    violations += key
                }
            }
        }

        assertTrue(violations.isEmpty()) {
            "Mutating endpoints missing @PreAuthorize:\n" + violations.joinToString("\n") { "  - $it" }
        }
    }

    /**
     * True iff [method] handles a mutating HTTP verb. Inspects:
     *  - the method itself
     *  - the same-signature method on any interface the declaring class implements
     *  - the direct superclass chain
     */
    private fun isMutatingHandler(method: Method): Boolean {
        methodsToCheck(method).forEach { candidate ->
            if (candidate.isAnnotationPresent(PostMapping::class.java) ||
                candidate.isAnnotationPresent(PutMapping::class.java) ||
                candidate.isAnnotationPresent(PatchMapping::class.java) ||
                candidate.isAnnotationPresent(DeleteMapping::class.java)
            ) {
                return true
            }
            val requestMapping = candidate.getAnnotation(RequestMapping::class.java)
            if (requestMapping != null && requestMapping.method.toSet().intersect(mutatingMethods).isNotEmpty()) {
                return true
            }
        }
        return false
    }

    private fun methodsToCheck(method: Method): Sequence<Method> = sequence {
        yield(method)
        for (iface in method.declaringClass.interfaces) {
            runCatching { iface.getMethod(method.name, *method.parameterTypes) }
                .getOrNull()
                ?.let { yield(it) }
        }
        var superClass: Class<*>? = method.declaringClass.superclass
        while (superClass != null && superClass != Any::class.java) {
            runCatching { superClass!!.getDeclaredMethod(method.name, *method.parameterTypes) }
                .getOrNull()
                ?.let { yield(it) }
            superClass = superClass.superclass
        }
    }
}
