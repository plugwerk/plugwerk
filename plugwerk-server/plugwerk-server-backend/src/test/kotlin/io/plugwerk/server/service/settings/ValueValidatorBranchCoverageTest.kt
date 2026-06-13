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
package io.plugwerk.server.service.settings

import io.plugwerk.server.domain.SettingValueType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Branch-coverage tests for the shared [ValueValidator] (RC-011 / #281).
 *
 * Exercises every `when(type)` arm and every nested decision: STRING/PASSWORD
 * blank-vs-non-blank under both `allowBlankString` settings, the three INTEGER
 * failure modes plus the in-range success, the BOOLEAN in/out-of-set branches,
 * and the three ENUM branches (no allowedValues declared, value not admissible,
 * value admissible).
 */
class ValueValidatorBranchCoverageTest {

    // ---- STRING ------------------------------------------------------------

    @Test
    fun `STRING blank is rejected when blanks are not allowed`() {
        val error = ValueValidator.validate(
            rawValue = "   ",
            type = SettingValueType.STRING,
            keyDebugName = "some.string",
            allowBlankString = false,
        )
        assertThat(error).isEqualTo("value must not be blank")
    }

    @Test
    fun `STRING blank is accepted when blanks are allowed`() {
        val error = ValueValidator.validate(
            rawValue = "",
            type = SettingValueType.STRING,
            keyDebugName = "some.string",
            allowBlankString = true,
        )
        assertThat(error).isNull()
    }

    @Test
    fun `STRING non-blank is accepted even when blanks are forbidden`() {
        val error = ValueValidator.validate(
            rawValue = "hello",
            type = SettingValueType.STRING,
            keyDebugName = "some.string",
            allowBlankString = false,
        )
        assertThat(error).isNull()
    }

    @Test
    fun `STRING uses allowBlankString default of true`() {
        // No allowBlankString argument — default path must accept a blank.
        val error = ValueValidator.validate(
            rawValue = " ",
            type = SettingValueType.STRING,
            keyDebugName = "some.string",
        )
        assertThat(error).isNull()
    }

    // ---- PASSWORD ----------------------------------------------------------

    @Test
    fun `PASSWORD blank is rejected when blanks are not allowed`() {
        val error = ValueValidator.validate(
            rawValue = "",
            type = SettingValueType.PASSWORD,
            keyDebugName = "smtp.password",
            allowBlankString = false,
        )
        assertThat(error).isEqualTo("value must not be blank")
    }

    @Test
    fun `PASSWORD blank is accepted when blanks are allowed`() {
        val error = ValueValidator.validate(
            rawValue = "",
            type = SettingValueType.PASSWORD,
            keyDebugName = "smtp.password",
            allowBlankString = true,
        )
        assertThat(error).isNull()
    }

    @Test
    fun `PASSWORD non-blank is accepted when blanks are forbidden`() {
        val error = ValueValidator.validate(
            rawValue = "s3cret",
            type = SettingValueType.PASSWORD,
            keyDebugName = "smtp.password",
            allowBlankString = false,
        )
        assertThat(error).isNull()
    }

    // ---- INTEGER -----------------------------------------------------------

    @Test
    fun `INTEGER rejects non-numeric input`() {
        val error = ValueValidator.validate(
            rawValue = "abc",
            type = SettingValueType.INTEGER,
            keyDebugName = "some.int",
        )
        assertThat(error).isEqualTo("value must be an integer, got 'abc'")
    }

    @Test
    fun `INTEGER rejects value below the minimum`() {
        val error = ValueValidator.validate(
            rawValue = "0",
            type = SettingValueType.INTEGER,
            keyDebugName = "some.int",
            minInt = 1,
            maxInt = 100,
        )
        assertThat(error).isEqualTo("value must be >= 1")
    }

    @Test
    fun `INTEGER rejects value above the maximum`() {
        val error = ValueValidator.validate(
            rawValue = "101",
            type = SettingValueType.INTEGER,
            keyDebugName = "some.int",
            minInt = 1,
            maxInt = 100,
        )
        assertThat(error).isEqualTo("value must be <= 100")
    }

    @Test
    fun `INTEGER accepts an in-range value`() {
        val error = ValueValidator.validate(
            rawValue = "50",
            type = SettingValueType.INTEGER,
            keyDebugName = "some.int",
            minInt = 1,
            maxInt = 100,
        )
        assertThat(error).isNull()
    }

    @Test
    fun `INTEGER accepts any parseable value when no bounds are declared`() {
        // minInt and maxInt both null — both bound checks short-circuit to the else arm.
        val error = ValueValidator.validate(
            rawValue = "-9999",
            type = SettingValueType.INTEGER,
            keyDebugName = "some.int",
        )
        assertThat(error).isNull()
    }

    // ---- BOOLEAN -----------------------------------------------------------

    @Test
    fun `BOOLEAN accepts the literal true`() {
        val error = ValueValidator.validate(
            rawValue = "true",
            type = SettingValueType.BOOLEAN,
            keyDebugName = "some.bool",
        )
        assertThat(error).isNull()
    }

    @Test
    fun `BOOLEAN accepts the literal false`() {
        val error = ValueValidator.validate(
            rawValue = "false",
            type = SettingValueType.BOOLEAN,
            keyDebugName = "some.bool",
        )
        assertThat(error).isNull()
    }

    @Test
    fun `BOOLEAN rejects anything other than true or false`() {
        val error = ValueValidator.validate(
            rawValue = "TRUE",
            type = SettingValueType.BOOLEAN,
            keyDebugName = "some.bool",
        )
        assertThat(error).isEqualTo("value must be 'true' or 'false', got 'TRUE'")
    }

    // ---- ENUM --------------------------------------------------------------

    @Test
    fun `ENUM with no allowedValues declared reports a misconfiguration naming the key`() {
        val error = ValueValidator.validate(
            rawValue = "anything",
            type = SettingValueType.ENUM,
            keyDebugName = "broken.enum",
            allowedValues = null,
        )
        assertThat(error).isEqualTo("ENUM key 'broken.enum' has no allowedValues declared")
    }

    @Test
    fun `ENUM rejects a value outside the allowed set`() {
        val error = ValueValidator.validate(
            rawValue = "purple",
            type = SettingValueType.ENUM,
            keyDebugName = "some.enum",
            allowedValues = setOf("red", "green"),
        )
        assertThat(error).isEqualTo("value must be one of [red, green], got 'purple'")
    }

    @Test
    fun `ENUM accepts a value inside the allowed set`() {
        val error = ValueValidator.validate(
            rawValue = "green",
            type = SettingValueType.ENUM,
            keyDebugName = "some.enum",
            allowedValues = setOf("red", "green"),
        )
        assertThat(error).isNull()
    }
}
