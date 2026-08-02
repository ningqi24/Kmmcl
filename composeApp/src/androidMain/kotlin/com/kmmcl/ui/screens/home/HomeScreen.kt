package com.kmmcl.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kmmcl.core.auth.AuthService
import com.kmmcl.core.auth.AuthState
import com.kmmcl.data.model.GameVersion
import com.kmmcl.ui.screens.game.GameViewModel
import com.kmmcl.ui.screens.game.GameUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    authService: AuthService,
    gameViewModel: GameViewModel,
    onNavigateToVersions: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToServer: () -> Unit
) {
    val authState by remember { mutableStateOf(authService.currentAuth) }
    val uiState by gameViewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        gameViewModel.loadVersions()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Minecraft Launcher", fontWeight = FontWeight.Bold) }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.surface)
        ) {
            AuthCard(
                authState = authState,
                modifier = Modifier.padding(16.dp)
            )

            Text(
                "最新版本",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            if (uiState.isLoading && uiState.versions.isEmpty()) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
            } else if (uiState.error != null && uiState.versions.isEmpty()) {
                Text(
                    uiState.error!!,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp)
                )
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(uiState.versions.take(10)) { version ->
                        VersionItem(version = version, onClick = {
                            gameViewModel.selectVersion(version.id)
                            onNavigateToVersions()
                        })
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(onClick = onNavigateToServer, modifier = Modifier.weight(1f)) {
                    Text("服务器")
                }
                OutlinedButton(onClick = onNavigateToVersions, modifier = Modifier.weight(1f)) {
                    Text("版本")
                }
                Button(onClick = onNavigateToSettings, modifier = Modifier.weight(1f)) {
                    Text("设置")
                }
            }
        }
    }
}

@Composable
fun AuthCard(authState: AuthState, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                if (authState.isLoggedIn) "已登录" else "未登录",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            if (authState.isLoggedIn) {
                Text(
                    "角色: ${authState.username}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
fun VersionItem(version: GameVersion, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = version.id,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SuggestionChip(
                    onClick = {},
                    label = { Text(version.type, fontSize = 12.sp) }
                )
                if (version.releaseTime.isNotEmpty()) {
                    Text(
                        version.releaseTime.take(10),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
