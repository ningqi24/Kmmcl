package com.kmmcl.data.repository

import com.kmmcl.core.game.GameService
import com.kmmcl.data.model.GameVersion

class GameRepository(private val gameService: GameService) {

    private var cachedVersions: List<GameVersion>? = null

    suspend fun getVersions(): Result<List<GameVersion>> {
        cachedVersions?.let { return Result.success(it) }
        return gameService.fetchVersions().onSuccess { cachedVersions = it }
    }

    suspend fun refreshVersions(): Result<List<GameVersion>> {
        cachedVersions = null
        return getVersions()
    }
}
