package com.kmmcl.data.repository

import com.kmmcl.core.game.GameService
import com.kmmcl.data.model.GameVersion

class GameRepository(private val gameService: GameService) {

    private var cachedVersions: List<GameVersion>? = null

    suspend fun getVersions(): Result<List<GameVersion>> {
        return if (cachedVersions != null) {
            Result.success(cachedVersions!!)
        } else {
            gameService.fetchVersions().onSuccess {
                cachedVersions = it
            }
        }
    }

    fun getVersionById(id: String): GameVersion? {
        return cachedVersions?.find { it.id == id }
    }

    suspend fun refreshVersions(): Result<List<GameVersion>> {
        cachedVersions = null
        return getVersions()
    }
}
