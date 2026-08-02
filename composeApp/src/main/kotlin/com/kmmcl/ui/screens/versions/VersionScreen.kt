package com.kmmcl.ui.screens.versions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kmmcl.data.model.GameVersion
import com.kmmcl.ui.components.GlassCard
import com.kmmcl.ui.components.SectionHeader
import com.kmmcl.ui.screens.game.GameViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VersionScreen(
    viewModel: GameViewModel,
    onSelectVersion: (GameVersion) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "版本管理",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onBackground
                )
                IconButton(onClick = { viewModel.loadVersions() }) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "刷新",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "显示快照版本",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Switch(
                    checked = uiState.showSnapshots,
                    onCheckedChange = { viewModel.toggleShowSnapshots() }
                )
            }
        }

        if (uiState.isVersionsLoading) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }

        uiState.versionsError?.let { error ->
            item {
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        val displayVersions = if (uiState.showSnapshots) {
            uiState.versions
        } else {
            uiState.versions.filter { it.type == "release" }
        }

        val releases = displayVersions.filter { it.type == "release" }
        val snapshots = displayVersions.filter { it.type == "snapshot" }

        if (releases.isNotEmpty()) {
            item { SectionHeader("正式版") }
            items(releases) { version ->
                VersionItem(
                    version = version,
                    isSelected = uiState.selectedVersion?.id == version.id,
                    onClick = { onSelectVersion(version) }
                )
            }
        }

        if (snapshots.isNotEmpty() && uiState.showSnapshots) {
            item { SectionHeader("快照版") }
            items(snapshots) { version ->
                VersionItem(
                    version = version,
                    isSelected = uiState.selectedVersion?.id == version.id,
                    onClick = { onSelectVersion(version) }
                )
            }
        }

        item { Spacer(modifier = Modifier.height(80.dp)) }
    }
}

@Composable
private fun VersionItem(
    version: GameVersion,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    GlassCard(
        modifier = modifier.clickable { onClick() },
        borderColor = if (isSelected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
        } else {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)
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
                    text = version.releaseTime,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = if (version.type == "release") {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                    } else {
                        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f)
                    }
                ) {
                    Text(
                        text = version.type.uppercase(),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (version.type == "release") {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.tertiary
                        }
                    )
                }
                if (isSelected) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "当前",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
