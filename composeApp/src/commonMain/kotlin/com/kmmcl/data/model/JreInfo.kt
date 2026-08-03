
package com.kmmcl.data.model

import kotlinx.serialization.Serializable

@Serializable
data class JreInfo(
    val version: String,
    val arm64Url: String,
    val armUrl: String,
    val x86Url: String,
    val x8664Url: String
) {
    companion object {
        val JRE_17 = JreInfo(
            version = "17",
            arm64Url  = "https://github.com/PojavLauncherTeam/android-openjdk-build-multiarch/releases/download/jre17-ec28559/jre17-arm64-20210825-release.tar.xz",
            armUrl    = "https://github.com/PojavLauncherTeam/android-openjdk-build-multiarch/releases/download/jre17-ec28559/jre17-arm-20210914-release.tar.xz",
            x86Url    = "https://github.com/PojavLauncherTeam/android-openjdk-build-multiarch/releases/download/jre17-ec28559/jre17-x86-20220225-release.tar.xz",
            x8664Url  = "https://github.com/PojavLauncherTeam/android-openjdk-build-multiarch/releases/download/jre17-ec28559/jre17-x86_64-20210825-release.tar.xz",
        )

        /** GitHub 加速镜像前缀。空字符串 = 直连，其余为代理前缀。 */
        val MIRROR_PREFIXES = listOf(
            "",                                    // 直连 GitHub
            "https://mirror.ghproxy.com/",          // ghproxy 官方镜像（国内可用）
            "https://gh.con.sh/",                   // gh.con.sh 加速
            "https://download.fastgit.org/",        // FastGit 镜像
        )
    }
}
