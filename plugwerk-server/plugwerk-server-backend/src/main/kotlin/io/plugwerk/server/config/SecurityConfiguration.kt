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

import io.plugwerk.server.PlugwerkProperties
import io.plugwerk.server.security.ChangePasswordRateLimitFilter
import io.plugwerk.server.security.DelegatingJwtDecoder
import io.plugwerk.server.security.LoginRateLimitFilter
import io.plugwerk.server.security.NamespaceAccessKeyAuthFilter
import io.plugwerk.server.security.OidcProviderRegistry
import io.plugwerk.server.security.PasswordChangeRequiredFilter
import io.plugwerk.server.security.PublicNamespaceFilter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.encrypt.Encryptors
import org.springframework.security.crypto.encrypt.TextEncryptor
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.HttpStatusEntryPoint
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter
import java.security.MessageDigest

@Configuration
@EnableWebSecurity
class SecurityConfiguration(
    private val loginRateLimitFilter: LoginRateLimitFilter,
    private val changePasswordRateLimitFilter: ChangePasswordRateLimitFilter,
    private val apiKeyAuthFilter: NamespaceAccessKeyAuthFilter,
    private val publicNamespaceFilter: PublicNamespaceFilter,
    private val passwordChangeRequiredFilter: PasswordChangeRequiredFilter,
    private val props: PlugwerkProperties,
    private val oidcProviderRegistry: OidcProviderRegistry,
    private val localJwtDecoder: DelegatingJwtDecoder,
) {

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    /**
     * AES text encryptor for OIDC provider client secrets stored in the database.
     *
     * Uses the 16-character [PlugwerkProperties.AuthProperties.encryptionKey].
     * The salt is derived deterministically from the encryption key (SHA-256 of the key,
     * first 8 bytes hex-encoded) so that it is unique per deployment but stable across
     * restarts — existing encrypted values remain decryptable.
     *
     * Environment variable: `PLUGWERK_ENCRYPTION_KEY`
     */
    @Bean
    fun textEncryptor(): TextEncryptor {
        val salt = MessageDigest.getInstance("SHA-256")
            .digest(props.auth.encryptionKey.toByteArray())
            .take(8)
            .joinToString("") { "%02x".format(it) }
        return Encryptors.text(props.auth.encryptionKey, salt)
    }

    companion object {
        /**
         * Content-Security-Policy directives.
         *
         * - `style-src 'unsafe-inline'` is required because MUI 7 / Emotion injects `<style>`
         *   tags at runtime. A nonce-based approach would require SSR integration.
         * - `img-src data:` allows MUI SVG icons encoded as data URIs.
         *
         * TODO(Phase 3): make `frame-ancestors` configurable for embeddable UI component.
         */
        const val CSP_POLICY = "default-src 'self'; " +
            "script-src 'self'; " +
            "style-src 'self' 'unsafe-inline'; " +
            "img-src 'self' data:; " +
            "font-src 'self'; " +
            "connect-src 'self'; " +
            "frame-ancestors 'none'; " +
            "form-action 'self'; " +
            "base-uri 'self'; " +
            "object-src 'none'"
    }

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            // CSRF is safe to disable here because:
            //   1. The server is a pure REST API with `SessionCreationPolicy.STATELESS`
            //      (see below) — no HTTP session is created or consulted.
            //   2. Authentication is by Bearer JWT or `X-Api-Key` header, both of which
            //      an attacker cannot automatically attach to a cross-origin request.
            //   3. No endpoint reads ambient cookies for authentication.
            //   4. The OIDC callback uses the `state` / `nonce` parameters, not a session
            //      cookie, so it is not a CSRF surface.
            //
            // **Must be re-enabled if any of these become false.** Concretely: if a future
            // change moves the access token (or refresh token) into a cookie — the path
            // tracked by #294 (H-08, JWT-in-localStorage hardening) — cookies become a
            // CSRF surface and this call must be removed. See ADR-0020 for the full
            // decision record and revisit conditions.
            .csrf { it.disable() }
            .headers { headers ->
                headers.contentTypeOptions { }
                headers.frameOptions { it.deny() }
                headers.httpStrictTransportSecurity { it.maxAgeInSeconds(31536000).includeSubDomains(true) }
                headers.referrerPolicy {
                    it.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN)
                }
                headers.permissionsPolicy { it.policy("camera=(), microphone=(), geolocation=(), payment=()") }
                headers.contentSecurityPolicy { it.policyDirectives(CSP_POLICY) }
            }
            .httpBasic { it.disable() }
            .formLogin { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .exceptionHandling {
                // Return 401 JSON instead of redirect to login page
                it.authenticationEntryPoint(HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
            }
            .oauth2ResourceServer { oauth2 ->
                oauth2.jwt { jwt -> jwt.decoder(localJwtDecoder) }
            }
            .authorizeHttpRequests { auth ->
                auth
                    // Logout requires a valid Bearer token — must be listed before the auth wildcard
                    .requestMatchers(HttpMethod.POST, "/api/v1/auth/logout").authenticated()
                    // Auth endpoints are always public (login + change-password requires auth but handled in controller)
                    .requestMatchers("/api/v1/auth/**").permitAll()
                    // Update check is public (used by client plugin without auth)
                    .requestMatchers(HttpMethod.POST, "/api/v1/namespaces/*/updates/check").permitAll()
                    // Public server config (upload limits etc.) — used by frontend without auth
                    .requestMatchers(HttpMethod.GET, "/api/v1/config").permitAll()
                    // Actuator health is public; info and prometheus require authentication
                    .requestMatchers("/actuator/health").permitAll()
                    // SPA static assets and routes are always public — they only serve index.html
                    // or static bundles. Real authorization happens in the frontend via the API.
                    .requestMatchers(HttpMethod.GET, "/", "/index.html", "/assets/**", "/*.svg", "/*.ico").permitAll()
                    .requestMatchers(
                        HttpMethod.GET,
                        "/login",
                        "/register",
                        "/forgot-password",
                        "/reset-password",
                    ).permitAll()
                    .requestMatchers(HttpMethod.GET, "/403", "/404", "/500", "/503").permitAll()
                    .requestMatchers(
                        HttpMethod.GET,
                        "/admin/**",
                        "/profile",
                        "/change-password",
                        "/onboarding",
                    ).permitAll()
                    .requestMatchers(HttpMethod.GET, "/namespaces/*/plugins", "/namespaces/*/plugins/**").permitAll()
                    // OpenAPI spec is public (used by API docs page without login)
                    .requestMatchers(HttpMethod.GET, "/api-docs", "/api-docs/**").permitAll()
                    // Everything else requires authentication
                    // (public namespace GET requests are handled by PublicNamespaceFilter)
                    .anyRequest().authenticated()
            }
            // LoginRateLimitFilter runs first �� blocks brute-force login attempts before any auth processing
            .addFilterBefore(loginRateLimitFilter, UsernamePasswordAuthenticationFilter::class.java)
            // PublicNamespaceFilter runs second — sets AnonymousAuth for public namespace GETs
            .addFilterAfter(publicNamespaceFilter, LoginRateLimitFilter::class.java)
            // NamespaceAccessKeyAuthFilter runs after — handles machine-to-machine auth via X-Api-Key
            .addFilterAfter(apiKeyAuthFilter, PublicNamespaceFilter::class.java)
            // PasswordChangeRequiredFilter runs last — blocks all API access when passwordChangeRequired = true
            .addFilterAfter(passwordChangeRequiredFilter, NamespaceAccessKeyAuthFilter::class.java)
            // ChangePasswordRateLimitFilter runs after BearerTokenAuthenticationFilter so the
            // SecurityContext is populated with the JWT principal — the filter keys its bucket
            // on the authenticated subject. Unauthenticated requests pass through without
            // consuming a bucket token.
            .addFilterAfter(changePasswordRateLimitFilter, BearerTokenAuthenticationFilter::class.java)

        return http.build()
    }
}
