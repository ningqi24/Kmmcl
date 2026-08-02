
package com.kmmcl.ui.screens.game

import android.app.Application
import com.kmmcl.core.game.GameService
import com.kmmcl.data.model.GameVersion
import com.kmmcl.data.repository.GameRepository
import com.kmmcl.util.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class GameUiState(
    val versions: List<GameVersion> = emptyList(),
    val isLoading: Boolean = false,
    val logLines: List<String> = emptyList(),
    val selectedVersionId: String = "",
    val selectedVersionUrl: String = "",
    val error: String? = null
)

class GameViewModel(
    private val application: Application,
    private val repository: GameRepository,
    private val gameService: GameService
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
                        selectedVersionId = versions.firstOrNull()?.id ?: "",
                        selectedVersionUrl = versions.firstOrNull()?.url ?: ""
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
        val v = _uiState.value.versions.find { it.id == id }
        _uiState.value = _uiState.value.copy(
            selectedVersionId = id,
            selectedVersionUrl = v?.url ?: ""
        )
    }

    fun prepareGame() {
        val versionId = _uiState.value.selectedVersionId
        val versionUrl = _uiState.value.selectedVersionUrl
        if (versionId.isBlank() || versionUrl.isBlank()) return

        scope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, logLines = emptyList())
            val logs = mutableListOf<String>()

            NotificationHelper.showProgress(application, versionId, 0)

            gameService.prepareGame(versionId, versionUrl) { msg ->
                logs.add(msg)
                _uiState.value = _uiState.value.copy(logLines = logs.toList())
                val pct = Regex("""(\d+)%""").find(msg)?.groupValues?.get(1)?.toIntOrNull()
                if (pct != null) {
                    NotificationHelper.showProgress(application, versionId, pct)
                }
            }.onSuccess {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    logLines = logs + "准备完成"
                )
                NotificationHelper.showComplete(application, versionId)
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "准备失败"
                )
                NotificationHelper.showError(application, e.message ?: "下载失败")
            }
        }
    }
}
