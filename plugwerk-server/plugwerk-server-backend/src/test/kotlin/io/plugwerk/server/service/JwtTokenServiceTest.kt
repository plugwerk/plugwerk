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
package io.plugwerk.server.service

import com.nimbusds.jose.jwk.source.ImmutableSecret
import io.plugwerk.server.PlugwerkProperties
import io.plugwerk.server.config.JwtConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import java.time.Instant
import javax.crypto.spec.SecretKeySpec

class JwtTokenServiceTest {

    private val secret = "test-secret-at-least-32-chars-long!!"
    private val props = PlugwerkProperties(
        auth = PlugwerkProperties.AuthProperties(
            jwtSecret = secret,
            tokenValidityHours = 8,
        ),
    )

    private val secretKey = SecretKeySpec(secret.toByteArray(), "HmacSHA256")
    private val config = JwtConfiguration(props)
    private val service = JwtTokenService(config.jwtEncoder(), props)

    private val decoder = NimbusJwtDecoder.withSecretKey(secretKey)
        .macAlgorithm(MacAlgorithm.HS256)
        .build()

    @Test
    fun `generates a non-blank token`() {
        val token = service.generateToken("alice")
        assertThat(token).isNotBlank()
    }

    @Test
    fun `token contains correct subject`() {
        val token = service.generateToken("alice")
        val jwt = decoder.decode(token)
        assertThat(jwt.subject).isEqualTo("alice")
    }

    @Test
    fun `token contains server base-url as issuer`() {
        val token = service.generateToken("alice")
        val jwt = decoder.decode(token)
        assertThat(jwt.issuer?.toString()).isEqualTo("http://localhost:8080")
    }

    @Test
    fun `token expires after configured validity hours`() {
        val before = Instant.now()
        val token = service.generateToken("alice")
        val jwt = decoder.decode(token)
        val expectedExpiry = before.plusSeconds(8 * 3600L)
        // Allow 5 seconds tolerance
        assertThat(jwt.expiresAt).isBetween(expectedExpiry.minusSeconds(5), expectedExpiry.plusSeconds(5))
    }

    @Test
    fun `tokenValiditySeconds returns correct value`() {
        assertThat(service.tokenValiditySeconds()).isEqualTo(8 * 3600L)
    }

    @Test
    fun `different usernames produce different tokens`() {
        val token1 = service.generateToken("alice")
        val token2 = service.generateToken("bob")
        assertThat(token1).isNotEqualTo(token2)
    }

    @Test
    fun `token is a valid three-part JWT`() {
        val token = service.generateToken("alice")
        val parts = token.split(".")
        assertThat(parts).hasSize(3)
    }
}
