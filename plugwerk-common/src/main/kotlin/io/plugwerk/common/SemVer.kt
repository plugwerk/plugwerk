package io.plugwerk.common

/**
 * Semantic versioning data class.
 * Full implementation with parsing, comparison, and range matching
 * will be added in Milestone 2 (T-2.3).
 */
data class SemVer(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val preRelease: String? = null,
) : Comparable<SemVer> {

    override fun compareTo(other: SemVer): Int {
        val majorCmp = major.compareTo(other.major)
        if (majorCmp != 0) return majorCmp
        val minorCmp = minor.compareTo(other.minor)
        if (minorCmp != 0) return minorCmp
        return patch.compareTo(other.patch)
    }

    override fun toString(): String =
        "$major.$minor.$patch${preRelease?.let { "-$it" } ?: ""}"

    companion object {
        fun parse(version: String): SemVer {
            val parts = version.split("-", limit = 2)
            val numbers = parts[0].split(".")
            require(numbers.size == 3) { "Invalid SemVer format: $version" }
            return SemVer(
                major = numbers[0].toInt(),
                minor = numbers[1].toInt(),
                patch = numbers[2].toInt(),
                preRelease = parts.getOrNull(1),
            )
        }
    }
}
