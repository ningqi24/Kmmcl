package com.kmmcl.ui.screens.game

import com.kmmcl.core.game.GameService
import com.kmmcl.data.model.GameVersion
import com.kmmcl.data.repository.GameRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

data class GameUiState(
    val versions: List<GameVersion> = emptyList(),
    val isLoading: Boolean = false,
    val logLines: List<String> = emptyList(),
    val selectedVersionId: String = "",
    val error: String? = null
)

class GameViewModel(
    private val repository: GameRepository,
    private val gameService: GameService,
    private val gameDir: File = File("game")
) {
    private val scope = CoroutineScope(Dispatchers.Default)
    private val _uiState = MutableStateFlow(GameUiState())
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    fun loadVersions() {
        scope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            repository.refreshVersions()
                .onSuccess { versions ->
                    _uiState.value = _uiState.value.copy(
                        versions = versions,
                        isLoading = false,
                        selectedVersionId = versions.firstOrNull()?.id ?: ""
                    )
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = e.message ?: "获取版本失败"
                    )
                }
        }
    }

    fun selectVersion(id: String) {
        _uiState.value = _uiState.value.copy(selectedVersionId = id)
    }

    fun prepareGame() {
        val versionId = _uiState.value.selectedVersionId
        if (versionId.isBlank()) return
        scope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, logLines = emptyList())
            val logs = mutableListOf<String>()
            gameService.prepareGame(versionId, gameDir) { msg ->
                logs.add(msg)
                _uiState.value = _uiState.value.copy(logLines = logs.toList())
            }.onSuccess {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    logLines = logs + "准备完成！"
                )
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "准备失败"
                )
            }
        }
    }
}
