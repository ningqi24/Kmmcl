package com.kmmcl.core.game

import com.kmmcl.core.auth.AuthState
import com.kmmcl.data.model.GameVersion

enum class LaunchState {
    IDLE, PREPARING, DOWNLOADING, EXTRACTING, LAUNCHING, RUNNING, ERROR
}

data class GameLaunchStatus(
    val state: LaunchState = LaunchState.IDLE,
    val progress: Float = 0f,
    val currentStep: String = "",
    val error: String? = null
)

class GameService {

    suspend fun fetchVersions(): Result<List<GameVersion>> {
        return try {
            // Mokt meta: fetch version manifest
            // val meta = MoktMeta.getVersionManifest()
            // val versions = meta.versions.map { GameVersion(it.id, it.type, it.url, it.releaseTime) }

            val versions = listOf(
                GameVersion("1.21.4", "release", "https://piston-meta.mojang.com/v1/packages/...", "2025-06-15"),
                GameVersion("1.21.3", "release", "https://piston-meta.mojang.com/v1/packages/...", "2025-04-10"),
                GameVersion("1.21.1", "release", "https://piston-meta.mojang.com/v1/packages/...", "2024-10-15"),
                GameVersion("1.20.6", "release", "https://piston-meta.mojang.com/v1/packages/...", "2024-05-10"),
                GameVersion("1.20.4", "release", "https://piston-meta.mojang.com/v1/packages/...", "2023-12-07"),
                GameVersion("1.19.4", "release", "https://piston-meta.mojang.com/v1/packages/...", "2023-03-14"),
                GameVersion("24w35a", "snapshot", "https://piston-meta.mojang.com/v1/packages/...", "2024-08-28"),
                GameVersion("24w33a", "snapshot", "https://piston-meta.mojang.com/v1/packages/...", "2024-08-14")
            )
            Result.success(versions)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun buildLaunchArgs(
        version: GameVersion,
        auth: AuthState,
        gameDir: String
    ): List<String> {
        return listOf(
            "--username", auth.username,
            "--uuid", auth.uuid,
            "--accessToken", auth.accessToken,
            "--version", version.id,
            "--gameDir", gameDir,
            "--assetsDir", "$gameDir/assets",
            "--assetIndex", version.id,
            "--userType", "mojang",
            "--versionType", version.type
        )
    }
}
