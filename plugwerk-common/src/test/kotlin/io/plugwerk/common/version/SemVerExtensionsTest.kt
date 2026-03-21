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
package io.plugwerk.common.version

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class SemVerExtensionsTest {

    @Test
    fun `toSemVer parses valid version`() {
        val version = "1.2.3".toSemVer()
        assertEquals(1, version.major)
        assertEquals(2, version.minor)
        assertEquals(3, version.patch)
    }

    @Test
    fun `toSemVer parses version with pre-release`() {
        val version = "1.0.0-beta.1".toSemVer()
        assertEquals(1, version.major)
        assertEquals(0, version.minor)
        assertEquals(0, version.patch)
    }

    @Test
    fun `toSemVer parses version with build metadata`() {
        val version = "1.0.0+build.123".toSemVer()
        assertEquals(1, version.major)
    }

    @Test
    fun `toSemVer throws on invalid version`() {
        assertThrows<IllegalArgumentException> {
            "invalid".toSemVer()
        }
    }

    @Test
    fun `toSemVer throws on blank string`() {
        assertThrows<IllegalArgumentException> {
            "".toSemVer()
        }
    }

    @Test
    fun `toSemVerOrNull returns Semver for valid version`() {
        val version = "2.0.0".toSemVerOrNull()
        assertNotNull(version)
        assertEquals(2, version?.major)
    }

    @Test
    fun `toSemVerOrNull returns null for invalid version`() {
        assertNull("invalid".toSemVerOrNull())
    }

    @Test
    fun `toSemVerOrNull returns null for blank string`() {
        assertNull("".toSemVerOrNull())
    }

    @Test
    fun `isValidSemVer returns true for valid version`() {
        assertTrue("1.2.3".isValidSemVer())
        assertTrue("0.0.1".isValidSemVer())
        assertTrue("1.0.0-alpha".isValidSemVer())
        assertTrue("1.0.0+build".isValidSemVer())
    }

    @Test
    fun `isValidSemVer returns false for invalid version`() {
        assertFalse("abc".isValidSemVer())
        assertFalse("1.2".isValidSemVer())
        assertFalse("".isValidSemVer())
    }

    @Test
    fun `satisfiesSemVerRange returns true when version is in range`() {
        assertTrue("1.2.3".satisfiesSemVerRange(">=1.0.0 <2.0.0"))
        assertTrue("2.5.0".satisfiesSemVerRange(">=2.0.0 <4.0.0"))
    }

    @Test
    fun `satisfiesSemVerRange returns false when version is outside range`() {
        assertFalse("2.0.0".satisfiesSemVerRange(">=1.0.0 <2.0.0"))
        assertFalse("1.2.3".satisfiesSemVerRange(">=2.0.0 <4.0.0"))
    }

    @Test
    fun `satisfiesSemVerRange handles single constraint`() {
        assertTrue("2.0.0".satisfiesSemVerRange(">=1.0.0"))
        assertFalse("0.9.0".satisfiesSemVerRange(">=1.0.0"))
    }

    @Test
    fun `satisfiesSemVerRange throws on invalid version`() {
        assertThrows<IllegalArgumentException> {
            "invalid".satisfiesSemVerRange(">=1.0.0")
        }
    }

    @Test
    fun `compareSemVer returns negative when first is less`() {
        assertTrue(compareSemVer("1.2.3", "1.2.4") < 0)
        assertTrue(compareSemVer("1.0.0", "2.0.0") < 0)
    }

    @Test
    fun `compareSemVer returns positive when first is greater`() {
        assertTrue(compareSemVer("2.0.0", "1.9.9") > 0)
        assertTrue(compareSemVer("1.2.4", "1.2.3") > 0)
    }

    @Test
    fun `compareSemVer returns zero for equal versions`() {
        assertEquals(0, compareSemVer("1.0.0", "1.0.0"))
    }

    @Test
    fun `compareSemVer throws on invalid version`() {
        assertThrows<IllegalArgumentException> {
            compareSemVer("invalid", "1.0.0")
        }
        assertThrows<IllegalArgumentException> {
            compareSemVer("1.0.0", "invalid")
        }
    }

    @Test
    fun `satisfiesSemVerRange works with ampersand range notation`() {
        assertTrue("2.5.0".satisfiesSemVerRange(">=2.0.0 & <4.0.0"))
        assertFalse("4.0.0".satisfiesSemVerRange(">=2.0.0 & <4.0.0"))
    }
}
