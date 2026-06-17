package com.example.contesttracker.update

/**
 * Compares semantic version strings such as "1.0.0", "v1.2.3-beta", "2.0.0-alpha.1".
 *
 * Ordering rules (highest → lowest):
 *   stable (no pre-release label) > beta > alpha > any other label
 *
 * Never uses string comparison on whole version tags.
 */
object VersionComparator {

    data class ParsedVersion(
        val major: Int,
        val minor: Int,
        val patch: Int,
        /** null = stable; non-null = pre-release label such as "beta", "alpha.1" */
        val preRelease: String?
    ) : Comparable<ParsedVersion> {

        override fun compareTo(other: ParsedVersion): Int {
            if (major != other.major) return major.compareTo(other.major)
            if (minor != other.minor) return minor.compareTo(other.minor)
            if (patch != other.patch) return patch.compareTo(other.patch)

            // Stable release beats any pre-release
            return when {
                preRelease == null && other.preRelease == null -> 0
                preRelease == null -> 1           // this is stable, other is pre-release
                other.preRelease == null -> -1    // other is stable, this is pre-release
                else -> preRelease.compareTo(other.preRelease)
            }
        }
    }

    /**
     * Parses a version string into a [ParsedVersion].
     * Accepts formats: "1.0", "1.0.0", "v1.2.3", "v2.0.0-beta", "v1.3.0-beta.2"
     * Returns null if the string cannot be parsed at all.
     */
    fun parse(version: String): ParsedVersion? {
        val clean = version.trimStart('v', 'V').ifBlank { return null }
        val dashIndex = clean.indexOf('-')
        val versionPart = if (dashIndex >= 0) clean.substring(0, dashIndex) else clean
        val preRelease  = if (dashIndex >= 0) clean.substring(dashIndex + 1) else null

        val numbers = versionPart.split(".").mapNotNull { it.toIntOrNull() }
        if (numbers.isEmpty()) return null

        return ParsedVersion(
            major      = numbers.getOrElse(0) { 0 },
            minor      = numbers.getOrElse(1) { 0 },
            patch      = numbers.getOrElse(2) { 0 },
            preRelease = preRelease?.takeIf { it.isNotBlank() }
        )
    }

    /**
     * Returns true if [latestVersion] is strictly newer than [currentVersion].
     * If either string cannot be parsed, returns false (safe default — no spurious update prompts).
     */
    fun isNewerVersion(currentVersion: String, latestVersion: String): Boolean {
        val current = parse(currentVersion) ?: return false
        val latest  = parse(latestVersion)  ?: return false
        return latest > current
    }
}
