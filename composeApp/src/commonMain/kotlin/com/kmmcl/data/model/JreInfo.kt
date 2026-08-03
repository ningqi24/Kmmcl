package com.kmmcl.data.model

import com.kmmcl.core.platform.Architecture

/**
 * Android JRE info for a specific Java major version.
 *
 * Each entry holds the download URL per architecture plus optional SHA-256.
 * Mirror prefixes are now managed by [com.kmmcl.core.jre.AndroidJreSource]
 * rather than duplicated here; this model is kept for backward compatibility
 * with existing UI/state code.
 *
 * Desktop will use Mojang's piston-meta JRE API (separate [JreSource]
 * implementation) and won't touch this model.
 */
data class JreInfo(
    val majorVersion: Int,
    val archMap: Map<Architecture, String>,
    val sha256: String? = null,
)

/** Mirror prefixes applied to all JRE URLs for GitHub acceleration. */
@Deprecated(
    "Use AndroidJreSource.MIRROR_PREFIXES instead; this will be removed in 0.3",
    ReplaceWith("AndroidJreSource.MIRROR_PREFIXES", "com.kmmcl.core.jre.AndroidJreSource")
)
val MIRROR_PREFIXES: List<String> = listOf(
    "https://mirror.ghproxy.com/",
    "https://gh.con.sh/",
)

/**
 * Pre-built JRE info for Java 17 Android binaries.
 *
 * @deprecated Use [com.kmmcl.core.jre.AndroidJreSource] for active downloads;
 *             kept only for UI display / version listing.
 */
@Deprecated(
    "Use AndroidJreSource instead for download orchestration",
    ReplaceWith("AndroidJreSource()", "com.kmmcl.core.jre.AndroidJreSource")
)
val JRE_17: JreInfo = JreInfo(
    majorVersion = 17,
    archMap = mapOf(
        Architecture.ARM64  to "https://github.com/PojavLauncherTeam/android-openjdk-build-multiarch/releases/download/jre17-ec2853d/jre17-aarch64.tar.xz",
        Architecture.ARM32  to "https://github.com/PojavLauncherTeam/android-openjdk-build-multiarch/releases/download/jre17-ec2853d/jre17-arm.tar.xz",
        Architecture.X86_64 to "https://github.com/PojavLauncherTeam/android-openjdk-build-multiarch/releases/download/jre17-ec2853d/jre17-x86_64.tar.xz",
        Architecture.X86    to "https://github.com/PojavLauncherTeam/android-openjdk-build-multiarch/releases/download/jre17-ec2853d/jre17-x86.tar.xz",
    ),
)
