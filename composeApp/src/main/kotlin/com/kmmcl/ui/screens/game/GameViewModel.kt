package com.kmmcl.ui.screens.game

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kmmcl.core.auth.AuthService
import com.kmmcl.core.auth.AuthState
import com.kmmcl.core.download.DownloadManager
import com.kmmcl.core.download.DownloadTask
import com.kmmcl.core.game.GameLaunchStatus
import com.kmmcl.core.game.GameService
import com.kmmcl.core.game.LaunchState
import com.kmmcl.data.model.GameVersion
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class GameUiState(
    val versions: List<GameVersion> = emptyList(),
    val selectedVersion: GameVersion? = null,
    val launchStatus: GameLaunchStatus = GameLaunchStatus(),
    val isVersionsLoading: Boolean = false,
    val versionsError: String? = null,
    val showSnapshots: Boolean = true
)

class GameViewModel(
    private val gameService: GameService,
    private val authService: AuthService,
    private val downloadManager: DownloadManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(GameUiState())
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    val authState: StateFlow<AuthState> = authService.authState
    val downloadProgress = downloadManager.downloads

    init {
        loadVersions()
    }

    fun loadVersions() {
        viewModelScope.launch {
            _uiState.update { it.copy(isVersionsLoading = true, versionsError = null) }
            gameService.fetchVersions()
                .onSuccess { versions ->
                    _uiState.update {
                        it.copy(
                            versions = versions,
                            isVersionsLoading = false
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(
                            isVersionsLoading = false,
                            versionsError = e.message ?: "Failed to load versions"
                        )
                    }
                }
        }
    }

    fun selectVersion(version: GameVersion) {
        _uiState.update { it.copy(selectedVersion = version) }
    }

    fun toggleShowSnapshots() {
        _uiState.update { it.copy(showSnapshots = !it.showSnapshots) }
    }

    fun launch(gameDir: File) {
        val version = _uiState.value.selectedVersion ?: return
        val auth = authState.value

        if (!auth.isLoggedIn) {
            _uiState.update {
                it.copy(launchStatus = GameLaunchStatus(
                    state = LaunchState.ERROR,
                    error = "请先登录"
                ))
            }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(launchStatus = GameLaunchStatus(state = LaunchState.PREPARING, currentStep = "正在准备..."))
            }

            // Build launch arguments
            gameService.buildLaunchArgs(version, auth, gameDir.absolutePath)

            _uiState.update {
                it.copy(launchStatus = GameLaunchStatus(state = LaunchState.RUNNING, currentStep = "正在启动 Minecraft ${version.id}"))
            }
        }
    }

    fun loginMicrosoft() {
        viewModelScope.launch {
            authService.loginMicrosoft()
        }
    }

    fun loginOffline(username: String) {
        authService.loginOffline(username)
    }

    fun logout() {
        authService.logout()
    }
}
