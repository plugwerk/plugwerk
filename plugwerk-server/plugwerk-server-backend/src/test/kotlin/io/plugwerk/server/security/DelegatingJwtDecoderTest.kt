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

import io.plugwerk.server.service.TokenRevocationService
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtException
import java.time.Instant
import java.util.UUID

/**
 * Unit tests for [DelegatingJwtDecoder]'s revocation-check semantics (#483).
 *
 * The interesting contract: locally-issued tokens MUST carry `jti`, `sub`, and
 * `iat`. Pre-#483 the decoder silently bypassed revocation when any was
 * missing — a security control silently disabled by a missing claim. The fix
 * is to fail loudly so a future [io.plugwerk.server.service.JwtTokenService]
 * regression that drops a claim cannot quietly turn revocation off for a new
 * token type.
 *
 * `checkRevocation` runs ONLY after the local decoder accepts the token —
 * OIDC tokens bypass it entirely via the fallback loop. The OIDC-fallback
 * test below pins that behaviour so the loud-fail does not leak into the
 * external-token path.
 */
@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DelegatingJwtDecoderTest {

    private lateinit var localDecoder: JwtDecoder
    private lateinit var oidcProviderRegistry: OidcProviderRegistry
    private lateinit var tokenRevocationService: TokenRevocationService
    private lateinit var decoder: DelegatingJwtDecoder

    @BeforeEach
    fun setUp() {
        localDecoder = mock()
        oidcProviderRegistry = mock()
        tokenRevocationService = mock()
        whenever(oidcProviderRegistry.decoders()).thenReturn(emptyList())
        decoder = DelegatingJwtDecoder(localDecoder, oidcProviderRegistry, tokenRevocationService)
    }

    @Test
    fun `valid local token with all required claims passes through revocation check`() {
        val jti = UUID.randomUUID().toString()
        val subject = UUID.randomUUID().toString()
        val issuedAt = Instant.parse("2026-05-10T12:00:00Z")
        val jwt = jwt(jti = jti, subject = subject, issuedAt = issuedAt)
        whenever(localDecoder.decode("token")).thenReturn(jwt)
        whenever(tokenRevocationService.isRevoked(jti, subject, issuedAt)).thenReturn(false)

        val result = decoder.decode("token")

        assertThat(result).isSameAs(jwt)
        verify(tokenRevocationService).isRevoked(jti, subject, issuedAt)
    }

    @Test
    fun `revoked local token throws JwtException with revoked message`() {
        val jti = UUID.randomUUID().toString()
        val subject = UUID.randomUUID().toString()
        val issuedAt = Instant.parse("2026-05-10T12:00:00Z")
        val jwt = jwt(jti = jti, subject = subject, issuedAt = issuedAt)
        whenever(localDecoder.decode("token")).thenReturn(jwt)
        whenever(tokenRevocationService.isRevoked(jti, subject, issuedAt)).thenReturn(true)

        assertThatThrownBy { decoder.decode("token") }
            .isInstanceOf(JwtException::class.java)
            .hasMessageContaining("revoked")
    }

    @Test
    fun `local token missing jti throws JwtException with claim-name in message (#483)`() {
        val jwt = jwt(jti = null, subject = UUID.randomUUID().toString(), issuedAt = Instant.now())
        whenever(localDecoder.decode("token")).thenReturn(jwt)

        assertThatThrownBy { decoder.decode("token") }
            .isInstanceOf(JwtException::class.java)
            .hasMessageContaining("jti")

        verify(tokenRevocationService, never()).isRevoked(any(), any(), any())
    }

    @Test
    fun `local token missing sub throws JwtException with claim-name in message (#483)`() {
        val jwt = jwt(jti = UUID.randomUUID().toString(), subject = null, issuedAt = Instant.now())
        whenever(localDecoder.decode("token")).thenReturn(jwt)

        assertThatThrownBy { decoder.decode("token") }
            .isInstanceOf(JwtException::class.java)
            .hasMessageContaining("sub")

        verify(tokenRevocationService, never()).isRevoked(any(), any(), any())
    }

    @Test
    fun `local token missing iat throws JwtException with claim-name in message (#483)`() {
        val jwt = jwt(jti = UUID.randomUUID().toString(), subject = UUID.randomUUID().toString(), issuedAt = null)
        whenever(localDecoder.decode("token")).thenReturn(jwt)

        assertThatThrownBy { decoder.decode("token") }
            .isInstanceOf(JwtException::class.java)
            .hasMessageContaining("iat")

        verify(tokenRevocationService, never()).isRevoked(any(), any(), any())
    }

    @Test
    fun `OIDC fallback token bypasses revocation check entirely (#483 contract preservation)`() {
        // Local decoder rejects (HMAC mismatch / unknown issuer), an OIDC
        // decoder accepts. checkRevocation must NOT run, and the missing-claim
        // hardening from #483 must NOT leak into this path: the returned jwt
        // intentionally has no jti — pre-#483 this was already silently
        // accepted, post-#483 it must still be silently accepted because the
        // local-revocation contract simply does not apply to external tokens.
        val oidcJwt = jwt(jti = null, subject = "external-subject", issuedAt = Instant.now())
        val oidcDecoder = mock<JwtDecoder>()
        whenever(oidcDecoder.decode("token")).thenReturn(oidcJwt)
        whenever(localDecoder.decode("token")).thenThrow(JwtException("not a local token"))
        whenever(oidcProviderRegistry.decoders()).thenReturn(listOf(oidcDecoder))

        val result = decoder.decode("token")

        assertThat(result).isSameAs(oidcJwt)
        verify(tokenRevocationService, never()).isRevoked(any(), any(), any())
    }

    private fun jwt(jti: String?, subject: String?, issuedAt: Instant?): Jwt {
        // Build the mock directly with the targeted getters stubbed. We do
        // not stub the rest of Jwt's API because the production
        // checkRevocation only reads .id / .subject / .issuedAt.
        val mockJwt = mock<Jwt>()
        whenever(mockJwt.id).thenReturn(jti)
        whenever(mockJwt.subject).thenReturn(subject)
        whenever(mockJwt.issuedAt).thenReturn(issuedAt)
        return mockJwt
    }
}
