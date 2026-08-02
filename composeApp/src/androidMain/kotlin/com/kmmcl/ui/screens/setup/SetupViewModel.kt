
package com.kmmcl.ui.screens.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kmmcl.core.game.GameService
import com.kmmcl.core.game.VersionService
import com.kmmcl.core.jre.JreManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class SetupStep {
    data object CheckJre : SetupStep()
    data object DownloadJre : SetupStep()
    data object DownloadGame : SetupStep()
    data object Done : SetupStep()
    data class Error(val message: String) : SetupStep()
}

data class SetupState(
    val step: SetupStep = SetupStep.CheckJre,
    val progressText: String = "",
    val progressPct: Float = 0f,
)

class SetupViewModel(
    private val jreManager: JreManager,
    private val versionService: VersionService,
    private val gameService: GameService,
) : ViewModel() {

    private val _state = MutableStateFlow(SetupState())
    val state: StateFlow<SetupState> = _state

    fun startSetup(versionId: String = "1.20.1") {
        viewModelScope.launch {
            try {
                // Step 1: JRE
                if (!jreManager.isReady) {
                    _state.value = SetupState(SetupStep.DownloadJre, "正在准备 JRE...")
                    jreManager.downloadJre { msg ->
                        _state.value = _state.value.copy(progressText = msg)
                    }.getOrThrow()
                }

                // Step 2: Game files
                _state.value = SetupState(SetupStep.DownloadGame, "正在获取版本列表...")
                val manifest = versionService.fetchManifest().getOrThrow()
                val entry = manifest.versions.find { it.id == versionId }
                    ?: throw IllegalStateException("未找到版本 $versionId")
                val versionUrl = com.kmmcl.core.game.MojangMirror.mirror(entry.url)

                gameService.prepareGame(versionId, versionUrl) { msg ->
                    _state.value = _state.value.copy(progressText = msg)
                }.getOrThrow()

                _state.value = SetupState(SetupStep.Done, "准备完成")
            } catch (e: Exception) {
                _state.value = SetupState(SetupStep.Error("下载失败: ${e.message}"))
            }
        }
    }
}
