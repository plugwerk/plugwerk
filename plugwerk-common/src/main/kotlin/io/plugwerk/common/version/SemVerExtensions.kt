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

import org.semver4j.Semver

fun String.toSemVer(): Semver {
    require(isNotBlank()) { "Version string must not be blank" }
    val parsed = Semver.parse(this)
    requireNotNull(parsed) { "Invalid SemVer version: '$this'" }
    return parsed
}

fun String.toSemVerOrNull(): Semver? {
    if (isBlank()) return null
    return Semver.parse(this)
}

fun String.isValidSemVer(): Boolean = toSemVerOrNull() != null

fun String.satisfiesSemVerRange(range: String): Boolean {
    val version = toSemVer()
    val normalizedRange = normalizeRange(range)
    return version.satisfies(normalizedRange)
}

fun compareSemVer(v1: String, v2: String): Int {
    val semver1 = v1.toSemVer()
    val semver2 = v2.toSemVer()
    return semver1.compareTo(semver2)
}

internal fun normalizeRange(range: String): String = range.replace(Regex("\\s*&\\s*"), " ")
