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
package io.plugwerk.server.security.url

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatNoException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class UriGuardsTest {

    @Test
    fun `accepts well-formed public https URI`() {
        assertThatNoException().isThrownBy {
            UriGuards.requirePublicHttpUri("https://accounts.google.com", "issuerUri", required = true)
        }
    }

    @Test
    fun `accepts well-formed public http URI`() {
        assertThatNoException().isThrownBy {
            UriGuards.requirePublicHttpUri("http://login.example.com", "issuerUri", required = true)
        }
    }

    @Test
    fun `null + not required is silent`() {
        assertThatNoException().isThrownBy {
            UriGuards.requirePublicHttpUri(null, "jwkSetUri", required = false)
        }
    }

    @Test
    fun `blank + not required is silent`() {
        assertThatNoException().isThrownBy {
            UriGuards.requirePublicHttpUri("   ", "jwkSetUri", required = false)
        }
    }

    @Test
    fun `null + required throws with field-prefixed message`() {
        val ex = assertThrows<IllegalArgumentException> {
            UriGuards.requirePublicHttpUri(null, "issuerUri", required = true)
        }
        assertThat(ex.message).startsWith("issuerUri").contains("required")
    }

    @Test
    fun `blank + required throws with field-prefixed message`() {
        val ex = assertThrows<IllegalArgumentException> {
            UriGuards.requirePublicHttpUri("", "issuerUri", required = true)
        }
        assertThat(ex.message).startsWith("issuerUri").contains("required")
    }

    @Test
    fun `invalid URI syntax throws with field-prefixed message`() {
        val ex = assertThrows<IllegalArgumentException> {
            UriGuards.requirePublicHttpUri("ht!tp://broken url", "issuerUri", required = true)
        }
        assertThat(ex.message).startsWith("issuerUri").contains("valid URI")
    }

    @Test
    fun `non-http scheme throws with field-prefixed message`() {
        val ex = assertThrows<IllegalArgumentException> {
            UriGuards.requirePublicHttpUri("file:///etc/passwd", "issuerUri", required = true)
        }
        assertThat(ex.message).startsWith("issuerUri").contains("http or https")
    }

    @Test
    fun `URI without host throws with field-prefixed message`() {
        val ex = assertThrows<IllegalArgumentException> {
            UriGuards.requirePublicHttpUri("https:///path-only", "issuerUri", required = true)
        }
        assertThat(ex.message).startsWith("issuerUri").contains("host")
    }

    @Test
    fun `private host throws via HostClassifier`() {
        val ex = assertThrows<IllegalArgumentException> {
            UriGuards.requirePublicHttpUri(
                "http://169.254.169.254/.well-known/openid-configuration",
                "issuerUri",
                required = true,
            )
        }
        assertThat(ex.message).startsWith("issuerUri").contains("private")
    }
}
