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

import com.nimbusds.jose.jwk.source.ImmutableSecret
import io.plugwerk.server.security.JwtKeyDerivation
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder
import javax.crypto.SecretKey

/**
 * Provides [JwtDecoder] and [JwtEncoder] beans for HMAC-SHA256 signed JWTs.
 *
 * The signing key is HKDF-derived from `PLUGWERK_AUTH_JWT_SECRET` via
 * [JwtKeyDerivation] using the [JwtKeyDerivation.Purpose.JWT_SIGNING] info
 * string (SBS-012 / #267). The minimum-length (`@Size(min = 32)`) and
 * blocklist-default checks on the raw secret are still enforced upstream by
 * [io.plugwerk.server.PlugwerkProperties] and
 * [io.plugwerk.server.config.PlugwerkPropertiesValidator] — HKDF concentrates
 * entropy but cannot manufacture it from nothing.
 *
 * Phase 2+: External OIDC providers are configured via the admin UI and loaded dynamically
 * by [io.plugwerk.server.security.OidcProviderRegistry]. The [io.plugwerk.server.security.DelegatingJwtDecoder]
 * tries the local decoder first and falls back to enabled OIDC decoders automatically.
 */
@Configuration
class JwtConfiguration(keyDerivation: JwtKeyDerivation) {

    private val secretKey: SecretKey = keyDerivation.deriveKey(JwtKeyDerivation.Purpose.JWT_SIGNING)

    /**
     * Local HMAC-SHA256 decoder for tokens self-issued by this server via `/api/v1/auth/login`.
     *
     * Named `localDecoder` to distinguish it from the composite [io.plugwerk.server.security.DelegatingJwtDecoder]
     * which combines this decoder with any enabled OIDC provider decoders.
     */
    @Bean
    fun localDecoder(): JwtDecoder = NimbusJwtDecoder.withSecretKey(secretKey).macAlgorithm(MacAlgorithm.HS256).build()

    @Bean
    fun jwtEncoder(): JwtEncoder = NimbusJwtEncoder(ImmutableSecret(secretKey))
}
