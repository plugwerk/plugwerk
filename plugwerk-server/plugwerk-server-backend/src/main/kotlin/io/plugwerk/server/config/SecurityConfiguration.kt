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
package io.plugwerk.server.config

import io.plugwerk.server.security.ApiKeyAuthFilter
import io.plugwerk.server.security.PublicNamespaceFilter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.HttpStatusEntryPoint
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

@Configuration
@EnableWebSecurity
class SecurityConfiguration(
    private val apiKeyAuthFilter: ApiKeyAuthFilter,
    private val publicNamespaceFilter: PublicNamespaceFilter,
    private val jwtDecoder: JwtDecoder,
) {

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .httpBasic { it.disable() }
            .formLogin { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .exceptionHandling {
                // Return 401 JSON instead of redirect to login page
                it.authenticationEntryPoint(HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
            }
            .oauth2ResourceServer { oauth2 ->
                oauth2.jwt { jwt -> jwt.decoder(jwtDecoder) }
            }
            .authorizeHttpRequests { auth ->
                auth
                    // Auth endpoints are always public
                    .requestMatchers("/api/auth/**").permitAll()
                    // Update check is public (used by client SDK without auth)
                    .requestMatchers(HttpMethod.POST, "/api/v1/namespaces/*/updates/check").permitAll()
                    // Actuator health and info are public
                    .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                    // SPA static assets are always public
                    .requestMatchers(HttpMethod.GET, "/", "/index.html", "/assets/**", "/*.svg", "/*.ico").permitAll()
                    // OpenAPI spec is public (used by API docs page without login)
                    .requestMatchers(HttpMethod.GET, "/api-docs/**").permitAll()
                    // Everything else requires authentication
                    // (public namespace GET requests are handled by PublicNamespaceFilter)
                    .anyRequest().authenticated()
            }
            // PublicNamespaceFilter runs first — sets AnonymousAuth for public namespace GETs
            .addFilterBefore(publicNamespaceFilter, UsernamePasswordAuthenticationFilter::class.java)
            // ApiKeyAuthFilter runs after — handles machine-to-machine auth via X-Api-Key
            .addFilterAfter(apiKeyAuthFilter, PublicNamespaceFilter::class.java)

        return http.build()
    }
}
