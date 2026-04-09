/*
 * Copyright (c) 2025-present devtank42 GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.plugwerk.spi.version

import org.semver4j.Semver

/**
 * Parses this string as a SemVer version.
 *
 * @return the parsed [Semver] instance
 * @throws IllegalArgumentException if the string is blank or not a valid SemVer version
 */
fun String.toSemVer(): Semver {
    require(isNotBlank()) { "Version string must not be blank" }
    val parsed = Semver.parse(this)
    requireNotNull(parsed) { "Invalid SemVer version: '$this'" }
    return parsed
}

/**
 * Parses this string as a SemVer version, returning `null` instead of throwing on invalid input.
 *
 * @return the parsed [Semver] instance, or `null` if the string is blank or not a valid SemVer
 */
fun String.toSemVerOrNull(): Semver? {
    if (isBlank()) return null
    return Semver.parse(this)
}

/**
 * Returns `true` if this string is a valid SemVer version string.
 */
fun String.isValidSemVer(): Boolean = toSemVerOrNull() != null

/**
 * Checks whether this SemVer version string satisfies a SemVer range expression.
 *
 * Supports the Plugwerk range syntax where `&` is used as AND operator between constraints
 * (e.g. `">=2.0.0 & <4.0.0"`), which is normalised to the semver4j-compatible form before
 * evaluation.
 *
 * @param range  SemVer range expression (e.g. `">=1.0.0"`, `">=2.0.0 & <4.0.0"`)
 * @return `true` if this version falls within the given range
 * @throws IllegalArgumentException if this string is not a valid SemVer version
 */
fun String.satisfiesSemVerRange(range: String): Boolean {
    val version = toSemVer()
    val normalizedRange = normalizeRange(range)
    return version.satisfies(normalizedRange)
}

/**
 * Compares two SemVer version strings.
 *
 * Follows standard SemVer ordering: `1.0.0 < 1.0.1 < 1.1.0 < 2.0.0`.
 * Pre-release versions (e.g. `1.0.0-alpha`) are ordered before the release (i.e. `< 1.0.0`).
 *
 * @param v1 first SemVer version string
 * @param v2 second SemVer version string
 * @return negative if [v1] < [v2], zero if equal, positive if [v1] > [v2]
 * @throws IllegalArgumentException if either string is not a valid SemVer version
 */
fun compareSemVer(v1: String, v2: String): Int {
    val semver1 = v1.toSemVer()
    val semver2 = v2.toSemVer()
    return semver1.compareTo(semver2)
}

/**
 * Normalises a Plugwerk range expression to the form expected by semver4j.
 *
 * Replaces ` & ` (with optional surrounding whitespace) with a single space,
 * which semver4j interprets as an AND conjunction.
 */
internal fun normalizeRange(range: String): String = range.replace(Regex("\\s*&\\s*"), " ")
