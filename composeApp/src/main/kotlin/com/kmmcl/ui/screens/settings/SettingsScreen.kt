package com.kmmcl.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kmmcl.core.auth.AuthType
import com.kmmcl.ui.components.GlassCard
import com.kmmcl.ui.components.SectionHeader
import com.kmmcl.ui.screens.game.GameViewModel

@Composable
fun SettingsScreen(
    viewModel: GameViewModel,
    modifier: Modifier = Modifier
) {
    val authState by viewModel.authState.collectAsState()
    var showLoginDialog by remember { mutableStateOf(false) }
    var showOfflineDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "设置",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        // Account section
        item { SectionHeader("账户") }

        item {
            GlassCard {
                if (authState.isLoggedIn) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = authState.username,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = when (authState.authType) {
                                    AuthType.MICROSOFT -> "微软正版"
                                    AuthType.OFFLINE -> "离线模式"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        TextButton(onClick = { viewModel.logout() }) {
                            Icon(Icons.Default.ExitToApp, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("登出")
                        }
                    }
                } else {
                    Text(
                        text = "未登录",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = { showLoginDialog = true },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("微软登录")
                        }
                        OutlinedButton(
                            onClick = { showOfflineDialog = true },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("离线登录")
                        }
                    }
                }
            }
        }

        // Game settings section
        item { SectionHeader("游戏设置") }

        item {
            GlassCard {
                Column {
                    SettingRow(label = "最大内存") {
                        Text(
                            text = "2048 MB",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
                    )
                    SettingRow(label = "游戏目录") {
                        Text(
                            text = "/storage/emulated/0/Kmmcl",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // About section
        item { SectionHeader("关于") }

        item {
            GlassCard {
                Column {
                    SettingRow(label = "版本") {
                        Text(
                            text = "1.0.0",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
                    )
                    SettingRow(label = "技术栈") {
                        Text(
                            text = "KMP + Compose Multiplatform",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(80.dp)) }
    }

    // Microsoft login dialog
    if (showLoginDialog) {
        AlertDialog(
            onDismissRequest = { showLoginDialog = false },
            title = { Text("微软正版登录") },
            text = { Text("将打开浏览器完成 Microsoft OAuth 认证，请确保已安装 Minecraft 正版。") },
            confirmButton = {
                Button(onClick = {
                    viewModel.loginMicrosoft()
                    showLoginDialog = false
                }) { Text("继续") }
            },
            dismissButton = {
                TextButton(onClick = { showLoginDialog = false }) { Text("取消") }
            }
        )
    }

    // Offline login dialog
    if (showOfflineDialog) {
        var username by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showOfflineDialog = false },
            title = { Text("离线登录") },
            text = {
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("玩家名") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
                )
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.loginOffline(username.ifBlank { "Player" })
                    showOfflineDialog = false
                }) { Text("登录") }
            },
            dismissButton = {
                TextButton(onClick = { showOfflineDialog = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun SettingRow(
    label: String,
    value: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        value()
    }
}
