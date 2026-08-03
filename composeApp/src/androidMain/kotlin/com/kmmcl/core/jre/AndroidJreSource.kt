package com.kmmcl.core.jre

import com.kmmcl.core.platform.Architecture

/**
 * Android JRE source — PojavLauncherTeam android-openjdk-build-multiarch
 * GitHub Releases.
 *
 * Each architecture gets its own asset URL. Mirrors are prefixed with
 * common GitHub acceleration proxies.
 */
class AndroidJreSource : JreSource {

    override val javaMajorVersion: Int = 17

    companion object {
        // ── Primary URLs (PojavLauncherTeam GitHub Releases) ──────
        private const val BASE_URL = "https://github.com/PojavLauncherTeam/android-openjdk-build-multiarch/releases/download/jre17-ec2853d"

        private val RAW_URLS: Map<Architecture, String> = mapOf(
            Architecture.ARM64  to "$BASE_URL/jre17-aarch64.tar.xz",
            Architecture.ARM32  to "$BASE_URL/jre17-arm.tar.xz",
            Architecture.X86_64 to "$BASE_URL/jre17-x86_64.tar.xz",
            Architecture.X86    to "$BASE_URL/jre17-x86.tar.xz",
        )

        // ── Mirror prefixes (GitHub acceleration proxies) ────────
        val MIRROR_PREFIXES: List<String> = listOf(
            "https://mirror.ghproxy.com/",
            "https://gh.con.sh/",
        )
    }

    override suspend fun resolveArchives(): List<JreArchive> {
        return RAW_URLS.map { (arch, url) ->
            JreArchive(
                arch = arch,
                primaryUrl = url,
                mirrors = MIRROR_PREFIXES.map { "$it$url" },
                sha256 = null,    // PojavLauncherTeam releases don't publish checksums
            )
        }
    }
}
