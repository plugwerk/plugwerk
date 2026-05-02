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
import io.plugwerk.server.security.DbClientRegistrationRepository
import io.plugwerk.server.security.DelegatingJwtDecoder
import io.plugwerk.server.security.LoginRateLimitFilter
import io.plugwerk.server.security.NamespaceAccessKeyAuthFilter
import io.plugwerk.server.security.NamespaceAuthorizationService
import io.plugwerk.server.security.OidcLoginSuccessHandler
import io.plugwerk.server.security.OidcProviderRegistry
import io.plugwerk.server.security.PasswordChangeRequiredFilter
import io.plugwerk.server.security.PromptAwareOAuth2AuthorizationRequestResolver
import io.plugwerk.server.security.PublicNamespaceFilter
import io.plugwerk.server.security.RefreshRateLimitFilter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.security.authorization.AuthorizationManager
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.encrypt.Encryptors
import org.springframework.security.crypto.encrypt.TextEncryptor
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter
import org.springframework.security.provisioning.InMemoryUserDetailsManager
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.access.intercept.RequestAuthorizationContext
import org.springframework.security.web.authentication.HttpStatusEntryPoint
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.security.web.csrf.CookieCsrfTokenRepository
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource
import java.security.MessageDigest

@Configuration
@EnableWebSecurity
class SecurityConfiguration(
    private val loginRateLimitFilter: LoginRateLimitFilter,
    private val refreshRateLimitFilter: RefreshRateLimitFilter,
    private val changePasswordRateLimitFilter: ChangePasswordRateLimitFilter,
    private val apiKeyAuthFilter: NamespaceAccessKeyAuthFilter,
    private val publicNamespaceFilter: PublicNamespaceFilter,
    private val passwordChangeRequiredFilter: PasswordChangeRequiredFilter,
    private val props: PlugwerkProperties,
    private val oidcProviderRegistry: OidcProviderRegistry,
    private val localJwtDecoder: DelegatingJwtDecoder,
    private val namespaceAuthorizationService: NamespaceAuthorizationService,
) {
    // NOTE: dbClientRegistrationRepository + oidcLoginSuccessHandler must NOT be
    // constructor-injected here — they transitively depend on `textEncryptor`,
    // which this very class exposes as a @Bean. Constructor injection produces
    // a circular reference that fails the bean container at startup. Both are
    // injected as @Bean method parameters on `securityFilterChain` instead, so
    // they are resolved lazily after textEncryptor has been created.

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    /**
     * Builds the in-memory `UserDetailsService` for the optional Prometheus scrape
     * account (SBS-004 / #292 — see [ADR-0025](../../../../../../../docs/adrs/0025-actuator-endpoint-hardening.md)).
     *
     * Returns `null` when the scrape account is not configured. The single user
     * carries only the `ACTUATOR_SCRAPE` authority and is only referenced by the
     * actuator filter chain — the API chain has no username/password login surface,
     * so the scrape credentials cannot authenticate against any other endpoint.
     * The plaintext password is BCrypt-encoded at construction and never retained.
     */
    private fun buildActuatorScrapeUserDetailsService(passwordEncoder: PasswordEncoder): UserDetailsService? {
        val actuator = props.auth.actuator
        if (!actuator.isScrapeAccountEnabled()) return null
        val user = User.withUsername(actuator.scrapeUsername!!.trim())
            .password(passwordEncoder.encode(actuator.scrapePassword!!))
            .authorities(SimpleGrantedAuthority(ACTUATOR_SCRAPE_AUTHORITY))
            .build()
        return InMemoryUserDetailsManager(user)
    }

    /**
     * CORS configuration source backed by [PlugwerkProperties.ServerProperties.CorsProperties].
     *
     * Default `plugwerk.server.cors.allowed-origins: []` installs a configuration with an
     * empty origin list, which keeps today's same-origin-only behaviour (Plugwerk's frontend
     * is bundled into the server JAR and served from the same origin as the API). Adding
     * entries via `PLUGWERK_SERVER_CORS_ALLOWED_ORIGINS` opts specific origins into CORS.
     *
     * See ADR-0021 for the full threat model and revisit conditions.
     */
    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val cors = props.server.cors
        val configuration = CorsConfiguration().apply {
            allowedOrigins = cors.allowedOrigins
            allowedMethods = cors.allowedMethods
            allowedHeaders = cors.allowedHeaders
            allowCredentials = cors.allowCredentials
            maxAge = cors.maxAge
        }
        return UrlBasedCorsConfigurationSource().apply {
            registerCorsConfiguration("/api/**", configuration)
        }
    }

    /**
     * AES-256-CBC text encryptor for OIDC provider client secrets stored in the database.
     *
     * [PlugwerkProperties.AuthProperties.encryptionKey] is a **password** fed to
     * Spring Security's `Encryptors.text()`, which uses `PBKDF2WithHmacSHA1` to derive a
     * 256-bit AES key. The password length controls PBKDF2 input entropy — it does **not**
     * change the AES key size. See ADR-0022 for the full record of this contract and the
     * migration procedure when the password is rotated.
     *
     * The salt is derived deterministically from the encryption key (SHA-256 of the key,
     * first 8 bytes hex-encoded) so that it is unique per deployment but stable across
     * restarts — existing encrypted values remain decryptable as long as the password
     * does not change.
     *
     * Environment variable: `PLUGWERK_AUTH_ENCRYPTION_KEY`
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

        /** GrantedAuthority for the Prometheus scrape account. */
        const val ACTUATOR_SCRAPE_AUTHORITY = "ACTUATOR_SCRAPE"
    }

    /**
     * Actuator filter chain (SBS-004 / #292 — [ADR-0025](../../../../../../../docs/adrs/0025-actuator-endpoint-hardening.md)).
     *
     * Separated from the API chain because the authorization rules are different in kind:
     * `/actuator/info` and `/actuator/prometheus` expose host-level state (build info,
     * metrics cardinality, pool sizes) that must not be readable by ordinary namespace
     * members, independently of namespace role. `/actuator/health` stays public so
     * container orchestrators, load balancers, and the in-repo docker-compose health
     * check keep working without credentials.
     *
     * Access paths to `/info` and `/prometheus`:
     *   1. **Scrape account (opt-in)** — HTTP Basic, backed by
     *      [actuatorScrapeUserDetailsService]. Canonical path for unattended scraping.
     *   2. **Superadmin JWT (fallback)** — same Bearer token used for the API chain,
     *      but only passes the `isCurrentUserSuperadmin()` gate. Lets human admins
     *      curl the endpoints without configuring a scrape account.
     *
     * `@Order(1)` ensures this chain is consulted before the catch-all API chain.
     * Namespace filters (access-key, public-namespace) are intentionally *not* wired
     * into this chain — actuator endpoints are host-level and do not carry namespace
     * context.
     */
    @Bean
    @Order(1)
    fun actuatorSecurityFilterChain(http: HttpSecurity, passwordEncoder: PasswordEncoder): SecurityFilterChain {
        val scrapeUserDetailsService = buildActuatorScrapeUserDetailsService(passwordEncoder)
        val superadminOrScrape = AuthorizationManager<RequestAuthorizationContext> { authentication, _ ->
            val auth = authentication.get()
            val hasScrapeAuthority = auth.authorities.any { it.authority == ACTUATOR_SCRAPE_AUTHORITY }
            val isSuperadmin = auth.isAuthenticated && namespaceAuthorizationService.isSuperadmin(auth)
            org.springframework.security.authorization.AuthorizationDecision(hasScrapeAuthority || isSuperadmin)
        }

        http
            .securityMatcher("/actuator/**")
            .cors { it.disable() }
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
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .formLogin { it.disable() }
            .exceptionHandling { it.authenticationEntryPoint(HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)) }
            .oauth2ResourceServer { oauth2 ->
                oauth2.jwt { jwt -> jwt.decoder(localJwtDecoder) }
            }
            .authorizeHttpRequests { auth ->
                auth
                    .requestMatchers("/actuator/health").permitAll()
                    .requestMatchers("/actuator/info", "/actuator/prometheus").access(superadminOrScrape)
                    // Closed by default: future /actuator/* additions do not silently leak.
                    .anyRequest().denyAll()
            }

        if (scrapeUserDetailsService != null) {
            http.httpBasic { it.realmName("plugwerk-actuator") }
            http.userDetailsService(scrapeUserDetailsService)
        } else {
            http.httpBasic { it.disable() }
        }

        return http.build()
    }

    @Bean
    @Order(2)
    fun securityFilterChain(
        http: HttpSecurity,
        dbClientRegistrationRepository: DbClientRegistrationRepository,
        oidcLoginSuccessHandler: OidcLoginSuccessHandler,
        promptAwareAuthorizationRequestResolver: PromptAwareOAuth2AuthorizationRequestResolver,
    ): SecurityFilterChain {
        http
            // CORS is configured via the corsConfigurationSource() bean above.
            //
            // Default = empty allow-list => today's same-origin-only behaviour, explicit.
            // Operators add origins via PLUGWERK_SERVER_CORS_ALLOWED_ORIGINS when the
            // frontend is deployed on a separate origin.
            //
            // See ADR-0021 for the threat model and revisit conditions. Do NOT add
            // @CrossOrigin annotations on controllers — this central config is the
            // only review point for cross-origin policy.
            .cors { it.configurationSource(corsConfigurationSource()) }
            // CSRF scope (ADR-0027, supersedes ADR-0020):
            //
            // The refresh-cookie endpoint (/api/v1/auth/refresh) reads an ambient httpOnly
            // cookie and is therefore the *only* CSRF surface in the app. Every other
            // endpoint authenticates via Bearer JWT or X-Api-Key — both non-ambient —
            // and remains CSRF-exempt.
            //
            // Implementation: CookieCsrfTokenRepository.withHttpOnlyFalse() issues an
            // XSRF-TOKEN cookie that JavaScript *can* read; the frontend echoes the value
            // in the X-XSRF-TOKEN header on the refresh call. Spring's double-submit
            // check then matches header to cookie. SameSite=Strict on the refresh cookie
            // itself is defence in depth for browsers that honour it.
            //
            // `CsrfTokenRequestAttributeHandler` (non-XOR) replaces Spring's 6.x default
            // `XorCsrfTokenRequestAttributeHandler`. The XOR handler expects a token that
            // was randomly XOR-masked per response and rejects the raw cookie value when
            // the SPA echoes it straight back in the header. That behaviour is intended
            // for server-rendered forms that carry a CsrfToken attribute, but it breaks
            // the cookie-only double-submit pattern this deployment relies on: on reload
            // the SPA has only the cookie value, never a server-rendered XOR'd variant.
            // The non-XOR handler compares header to cookie by equality — exactly the
            // double-submit contract ADR-0027 describes.
            .csrf { csrf ->
                csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                csrf.csrfTokenRequestHandler(CsrfTokenRequestAttributeHandler())
                csrf.requireCsrfProtectionMatcher { request ->
                    request.method == HttpMethod.POST.name() &&
                        request.requestURI == "/api/v1/auth/refresh"
                }
            }
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
            // Session policy is IF_REQUIRED (Spring's default), NOT STATELESS:
            // OAuth2 login (#79) needs an HTTP session for the brief window
            // between /oauth2/authorization/{id} (where the AuthorizationRequest
            // with state + PKCE code verifier is stored) and the callback at
            // /login/oauth2/code/{id} (where Spring needs to look it up).
            // STATELESS would silently no-op the storage and produce a Spring
            // Whitelabel error page on the callback because the request would
            // not be found in any repository.
            //
            // Our regular API auth model is unaffected: Bearer tokens are
            // verified statelessly per-request by the resource server, the
            // refresh cookie is the long-lived session token, and the
            // SecurityContext is never persisted into the session because no
            // formLogin / basic-auth path puts it there. The JSESSIONID
            // cookie issued during an OAuth2 login carries only the in-flight
            // OAuth2 state; once the callback completes Spring abandons it.
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED) }
            .exceptionHandling {
                // Return 401 JSON instead of redirect to login page
                it.authenticationEntryPoint(HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
            }
            .oauth2ResourceServer { oauth2 ->
                oauth2.jwt { jwt -> jwt.decoder(localJwtDecoder) }
            }
            // Browser-initiated OAuth2 login flow (issue #79). Spring's OAuth2 client filter
            // handles `/oauth2/authorization/{registrationId}` (start), the redirect to the
            // upstream provider, the `/login/oauth2/code/{registrationId}` callback, and the
            // PKCE-state validation. We provide:
            //   - the ClientRegistrationRepository (DB-backed, refreshable at runtime)
            //   - the success handler that mints a Plugwerk JWT + refresh cookie and redirects
            //     the user to "/" so the SPA's hydrate() picks up the session.
            // No formLogin page is rendered because we ship our own React login at /login.
            .oauth2Login { oauth2 ->
                oauth2
                    .clientRegistrationRepository(dbClientRegistrationRepository)
                    .successHandler(oidcLoginSuccessHandler)
                    // Issue #410: lets the login page's "Use a different
                    // account" link inject `prompt=select_account` (OIDC) /
                    // `prompt=login` (generic OAuth2) into the upstream
                    // authorize request. The resolver enforces a strict
                    // allow-list — see PromptAwareOAuth2AuthorizationRequestResolver.
                    .authorizationEndpoint { it.authorizationRequestResolver(promptAwareAuthorizationRequestResolver) }
            }
            .authorizeHttpRequests { auth ->
                auth
                    // Logout requires a valid Bearer token — must be listed before the auth wildcard
                    .requestMatchers(HttpMethod.POST, "/api/v1/auth/logout").authenticated()
                    // Auth endpoints are always public (login + change-password requires auth but handled in controller)
                    .requestMatchers("/api/v1/auth/**").permitAll()
                    // OAuth2 browser-flow endpoints managed by Spring Security itself (#79).
                    // /oauth2/authorization/** initiates the flow; /login/oauth2/code/** is the
                    // upstream-provider callback. Both must be reachable without prior auth.
                    .requestMatchers("/oauth2/authorization/**", "/login/oauth2/code/**").permitAll()
                    // Update check is public (used by client plugin without auth)
                    .requestMatchers(HttpMethod.POST, "/api/v1/namespaces/*/updates/check").permitAll()
                    // Public server config (upload limits etc.) — used by frontend without auth
                    .requestMatchers(HttpMethod.GET, "/api/v1/config").permitAll()
                    // /actuator/** is handled by actuatorSecurityFilterChain (@Order 1). See ADR-0025.
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
            // LoginRateLimitFilter runs first — blocks brute-force login attempts before any auth processing
            .addFilterBefore(loginRateLimitFilter, UsernamePasswordAuthenticationFilter::class.java)
            // RefreshRateLimitFilter protects /api/v1/auth/refresh from DoS/spam (ADR-0027).
            .addFilterAfter(refreshRateLimitFilter, LoginRateLimitFilter::class.java)
            // PublicNamespaceFilter runs next — sets AnonymousAuth for public namespace GETs
            .addFilterAfter(publicNamespaceFilter, RefreshRateLimitFilter::class.java)
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
