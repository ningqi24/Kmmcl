package com.kmmcl.ui.screens.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kmmcl.core.game.LaunchState
import com.kmmcl.data.model.GameVersion
import com.kmmcl.ui.components.DownloadProgressBar
import com.kmmcl.ui.components.GlassCard
import com.kmmcl.ui.components.SectionHeader
import com.kmmcl.ui.screens.game.GameViewModel
import java.io.File

@Composable
fun HomeScreen(
    viewModel: GameViewModel,
    onSelectVersion: (GameVersion) -> Unit,
    onLaunch: (File) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val authState by viewModel.authState.collectAsState()
    val downloads by viewModel.downloadProgress.collectAsState()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header
        item {
            Text(
                text = "Kmmcl",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 32.sp
                ),
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = if (authState.isLoggedIn) "欢迎, ${authState.username}" else "离线启动器",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Selected version card
        item {
            GlassCard {
                SectionHeader("已选版本")
                if (uiState.selectedVersion != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = uiState.selectedVersion!!.id,
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = uiState.selectedVersion!!.type.uppercase(),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                } else {
                    Text(
                        text = "未选择版本",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Launch button
        item {
            Button(
                onClick = { onLaunch(File("/storage/emulated/0/Kmmcl")) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                enabled = uiState.selectedVersion != null &&
                    authState.isLoggedIn &&
                    uiState.launchStatus.state != LaunchState.RUNNING,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (uiState.launchStatus.state == LaunchState.RUNNING) "启动中..." else "启动游戏",
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }

        // Launch status
        if (uiState.launchStatus.state != LaunchState.IDLE) {
            item {
                GlassCard {
                    SectionHeader("启动状态")
                    Text(
                        text = uiState.launchStatus.currentStep.ifEmpty {
                            when (uiState.launchStatus.state) {
                                LaunchState.PREPARING -> "正在准备..."
                                LaunchState.DOWNLOADING -> "正在下载..."
                                LaunchState.EXTRACTING -> "正在解压..."
                                LaunchState.LAUNCHING -> "正在启动..."
                                LaunchState.RUNNING -> "游戏运行中"
                                else -> "就绪"
                            }
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    uiState.launchStatus.error?.let { error ->
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }

        // Active downloads
        val activeDownloads = downloads.values.filter { it.isActive }
        if (activeDownloads.isNotEmpty()) {
            item {
                GlassCard {
                    SectionHeader("下载进度")
                    activeDownloads.forEach { dl ->
                        DownloadProgressBar(
                            progress = dl.progress,
                            label = dl.url.take(40),
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                }
            }
        }

        // Version quick select
        item {
            SectionHeader("版本选择")
        }

        val displayVersions = if (uiState.showSnapshots) {
            uiState.versions
        } else {
            uiState.versions.filter { it.type == "release" }
        }

        items(displayVersions.take(8)) { version ->
            val isSelected = uiState.selectedVersion?.id == version.id
            GlassCard(
                modifier = Modifier.clickable { onSelectVersion(version) },
                borderColor = if (isSelected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                } else {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = version.id,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = version.type,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (isSelected) {
                        Text(
                            text = "当前",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        // Bottom spacer for nav bar
        item { Spacer(modifier = Modifier.height(80.dp)) }
    }
}
