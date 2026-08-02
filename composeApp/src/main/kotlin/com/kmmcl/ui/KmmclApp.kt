package com.kmmcl.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.kmmcl.data.model.GameVersion
import com.kmmcl.ui.components.GlassNavigationBar
import com.kmmcl.ui.screens.game.GameViewModel
import com.kmmcl.ui.screens.home.HomeScreen
import com.kmmcl.ui.screens.settings.SettingsScreen
import com.kmmcl.ui.screens.versions.VersionScreen
import com.kmmcl.ui.theme.KmmclTheme
import org.koin.compose.koinInject
import java.io.File

enum class BottomTab(val label: String, val icon: ImageVector) {
    HOME("首页", Icons.Default.Home),
    VERSIONS("版本管理", Icons.Default.List),
    SETTINGS("设置", Icons.Default.Settings)
}

@Composable
fun KmmclApp() {
    KmmclTheme(darkTheme = true) {
        val viewModel: GameViewModel = koinInject()
        var selectedTab by remember { mutableStateOf(BottomTab.HOME) }

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                GlassNavigationBar(
                    backgroundColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                ) {
                    BottomTab.entries.forEach { tab ->
                        val isSelected = selectedTab == tab
                        NavigationBarItem(
                            selected = isSelected,
                            onClick = { selectedTab = tab },
                            icon = {
                                Icon(
                                    imageVector = tab.icon,
                                    contentDescription = tab.label,
                                    tint = if (isSelected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    }
                                )
                            },
                            label = {
                                Text(
                                    text = tab.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    }
                                )
                            }
                        )
                    }
                }
            }
        ) { innerPadding ->
            Box(modifier = Modifier.padding(innerPadding)) {
                when (selectedTab) {
                    BottomTab.HOME -> HomeScreen(
                        viewModel = viewModel,
                        onSelectVersion = { version: GameVersion -> viewModel.selectVersion(version) },
                        onLaunch = { gameDir: File -> viewModel.launch(gameDir) }
                    )
                    BottomTab.VERSIONS -> VersionScreen(
                        viewModel = viewModel,
                        onSelectVersion = { version: GameVersion -> viewModel.selectVersion(version) }
                    )
                    BottomTab.SETTINGS -> SettingsScreen(
                        viewModel = viewModel
                    )
                }
            }
        }
    }
}
