
package com.kmmcl.data.repository

import com.kmmcl.core.game.VersionService
import com.kmmcl.data.model.GameVersion

class GameRepository(private val versionService: VersionService) {

    private var cachedVersions: List<GameVersion>? = null

    suspend fun getVersions(): Result<List<GameVersion>> {
        cachedVersions?.let { return Result.success(it) }
        return versionService.fetchVersions().onSuccess { cachedVersions = it }
    }

    suspend fun refreshVersions(): Result<List<GameVersion>> {
        cachedVersions = null
        return getVersions()
    }
}
