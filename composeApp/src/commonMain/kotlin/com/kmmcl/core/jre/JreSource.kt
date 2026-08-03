package com.kmmcl.core.jre

import com.kmmcl.core.platform.Architecture

/**
 * A single JRE download candidate for one architecture.
 *
 * Mirrors are tried in order after the primary URL fails.
 */
data class JreArchive(
    val arch: Architecture,
    val primaryUrl: String,
    val sha256: String? = null,
    /** Fallback mirrors, tried in order */
    val mirrors: List<String> = emptyList(),
)

/**
 * Platform-specific JRE source.
 *
 * Provides the list of download candidates for the current target.
 * Each platform wires its own source:
 * - Android → PojavLauncherTeam GitHub Releases
 * - Desktop → Mojang official piston-meta API
 */
interface JreSource {
    /** Target Java major version (e.g. 17 for Java 17) */
    val javaMajorVersion: Int

    /**
     * Fetch available JRE archives for the current platform.
     *
     * Platform source sets wire their own implementation;
     * the common [JreDownloader] calls this without knowing the origin.
     */
    suspend fun resolveArchives(): List<JreArchive>
}
