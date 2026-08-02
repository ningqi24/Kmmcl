package com.kmmcl.ui.screens.versions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kmmcl.ui.screens.game.GameViewModel
import com.kmmcl.ui.screens.home.VersionItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VersionScreen(
    gameViewModel: GameViewModel,
    onBack: () -> Unit,
    onNavigateToLog: () -> Unit
) {
    val uiState by gameViewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        gameViewModel.loadVersions()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("全部版本") },
                navigationIcon = { TextButton(onClick = onBack) { Text("返回") } }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (uiState.isLoading && uiState.versions.isEmpty()) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(16.dp))
            }

            if (uiState.error != null) {
                Text(
                    uiState.error!!,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp)
                )
            }

            LazyColumn(modifier = Modifier.weight(1f)) {
                items(uiState.versions) { version ->
                    VersionItem(
                        version = version,
                        onClick = { gameViewModel.selectVersion(version.id) }
                    )
                }
            }

            if (uiState.selectedVersionId.isNotEmpty()) {
                Surface(modifier = Modifier.fillMaxWidth(), shadowElevation = 8.dp) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("已选择: ${uiState.selectedVersionId}", style = MaterialTheme.typography.bodyMedium)

                        if (uiState.logLines.isNotEmpty()) {
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    uiState.logLines.takeLast(5).forEach { line ->
                                        Text(line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }

                            TextButton(onClick = onNavigateToLog) {
                                Text("查看完整日志")
                            }
                        }

                        Button(
                            onClick = { gameViewModel.prepareGame() },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !uiState.isLoading
                        ) {
                            if (uiState.isLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text("准备游戏")
                        }
                    }
                }
            }
        }
    }
}
