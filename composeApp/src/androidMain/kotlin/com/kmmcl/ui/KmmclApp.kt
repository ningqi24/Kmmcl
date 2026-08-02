
package com.kmmcl.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.kmmcl.core.auth.AuthService
import com.kmmcl.core.jre.JreManager
import com.kmmcl.core.launch.GameLauncher
import com.kmmcl.ui.screens.game.GameViewModel
import com.kmmcl.ui.screens.home.HomeScreen
import com.kmmcl.ui.screens.log.LogScreen
import com.kmmcl.ui.screens.server.ServerScreen
import com.kmmcl.ui.screens.settings.SettingsScreen
import com.kmmcl.ui.screens.setup.SetupScreen
import com.kmmcl.ui.screens.setup.SetupViewModel
import com.kmmcl.ui.screens.versions.VersionScreen
import com.kmmcl.ui.theme.KmmclTheme
import org.koin.compose.KoinContext
import org.koin.compose.koinInject

sealed class Screen {
    data object Setup : Screen()
    data object Home : Screen()
    data object Versions : Screen()
    data object Settings : Screen()
    data object Game : Screen()
    data object Log : Screen()
    data object Server : Screen()
}

@Composable
fun KmmclApp() {
    KoinContext {
        val authService = koinInject<AuthService>()
        val gameViewModel = koinInject<GameViewModel>()
        val setupViewModel = koinInject<SetupViewModel>()
        val gameLauncher = koinInject<GameLauncher>()

        KmmclTheme(darkTheme = true) {
            // On first launch, show setup if JRE or game files are missing
            var initialScreen by remember {
                val jreManager = koinInject<JreManager>()
                mutableStateOf(
                    if (jreManager.isReady) Screen.Home else Screen.Setup
                )
            }

            var currentScreen by remember { mutableStateOf(initialScreen) }

            AnimatedContent(
                targetState = currentScreen,
                modifier = Modifier.background(MaterialTheme.colorScheme.surface)
            ) { screen ->
                when (screen) {
                    Screen.Setup -> SetupScreen(
                        viewModel = setupViewModel,
                        onComplete = {
                            setupViewModel.startSetup()
                            currentScreen = Screen.Home
                        }
                    )
                    Screen.Home -> HomeScreen(
                        authService = authService,
                        gameViewModel = gameViewModel,
                        onNavigateToVersions = { currentScreen = Screen.Versions },
                        onNavigateToSettings = { currentScreen = Screen.Settings },
                        onNavigateToServer = { currentScreen = Screen.Server }
                    )
                    Screen.Versions -> VersionScreen(
                        gameViewModel = gameViewModel,
                        onBack = { currentScreen = Screen.Home },
                        onNavigateToLog = { currentScreen = Screen.Log }
                    )
                    Screen.Settings -> SettingsScreen(
                        authService = authService,
                        onBack = { currentScreen = Screen.Home }
                    )
                    Screen.Log -> LogScreen(
                        gameViewModel = gameViewModel,
                        onBack = { currentScreen = Screen.Versions }
                    )
                    Screen.Server -> ServerScreen(
                        onBack = { currentScreen = Screen.Home }
                    )
                    Screen.Game -> {}
                }
            }
        }
    }
}
