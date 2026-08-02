
package com.kmmcl.data.model

import kotlinx.serialization.Serializable

@Serializable
data class JreInfo(
    val version: String,
    val arm64Url: String,
    val armeabiUrl: String,
    val x8664Url: String
) {
    companion object {
        val JRE_21 = JreInfo(
            version = "21",
            arm64Url  = "https://github.com/PojavLauncherTeam/android-openjdk-build-multiarch/releases/download/jre21-rollback-20240505/jre21-arm64-v8a-release.tar.xz",
            armeabiUrl = "https://github.com/PojavLauncherTeam/android-openjdk-build-multiarch/releases/download/jre21-rollback-20240505/jre21-armeabi-v7a-release.tar.xz",
            x8664Url  = "https://github.com/PojavLauncherTeam/android-openjdk-build-multiarch/releases/download/jre21-rollback-20240505/jre21-x86_64-release.tar.xz",
        )
    }
}
