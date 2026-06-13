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
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.validation.BeanPropertyBindingResult

/**
 * Branch-coverage tests for [PlugwerkPropertiesValidator] that complement the
 * existing `PlugwerkPropertiesValidatorTest` (which already exercises the auth
 * secret blocklist and most baseUrl arms). This class drives the remaining
 * uncovered decisions:
 *  - `validateTrustedProxyCidrs`: valid entry (no error), blank entry, and the
 *    `IpAddressMatcher` `runCatching { }.onFailure` invalid-syntax arm,
 *  - the baseUrl `uri.host.isNullOrBlank()` arm (a scheme-valid URI with no host).
 */
class PlugwerkPropertiesValidatorBranchCoverageTest {

    private val validator = PlugwerkPropertiesValidator()

    private fun authWithCidrs(vararg cidrs: String) = PlugwerkProperties.AuthProperties(
        jwtSecret = "a-unique-production-secret-at-least-32ch",
        encryptionKey = "prod-encrypt-16c",
        trustedProxyCidrs = cidrs.toList(),
    )

    private fun validate(props: PlugwerkProperties): BeanPropertyBindingResult {
        val errors = BeanPropertyBindingResult(props, "plugwerkProperties")
        validator.validate(props, errors)
        return errors
    }

    // -- trusted-proxy CIDR validation ---------------------------------------

    @Test
    fun `accepts a syntactically valid CIDR range and a bare IP`() {
        val errors = validate(PlugwerkProperties(auth = authWithCidrs("10.0.0.0/8", "192.168.1.1")))

        assertThat(errors.hasFieldErrors("auth.trustedProxyCidrs[0]")).isFalse()
        assertThat(errors.hasFieldErrors("auth.trustedProxyCidrs[1]")).isFalse()
    }

    @Test
    fun `rejects a blank CIDR entry`() {
        val errors = validate(PlugwerkProperties(auth = authWithCidrs("   ")))

        assertThat(errors.hasFieldErrors("auth.trustedProxyCidrs[0]")).isTrue()
    }

    @Test
    fun `rejects a syntactically invalid CIDR entry`() {
        val errors = validate(PlugwerkProperties(auth = authWithCidrs("not-a-cidr/99")))

        assertThat(errors.hasFieldErrors("auth.trustedProxyCidrs[0]")).isTrue()
    }

    @Test
    fun `flags the offending index when a later CIDR entry is invalid`() {
        val errors = validate(PlugwerkProperties(auth = authWithCidrs("10.0.0.0/8", "garbage")))

        assertThat(errors.hasFieldErrors("auth.trustedProxyCidrs[0]")).isFalse()
        assertThat(errors.hasFieldErrors("auth.trustedProxyCidrs[1]")).isTrue()
    }

    // -- baseUrl: scheme valid but host missing ------------------------------

    @Test
    fun `rejects a baseUrl whose scheme is valid but host is empty`() {
        val props = PlugwerkProperties(
            auth = authWithCidrs(),
            server = PlugwerkProperties.ServerProperties(baseUrl = "https:///no-host-path"),
        )

        val errors = validate(props)

        assertThat(errors.hasFieldErrors("server.baseUrl")).isTrue()
    }
}
