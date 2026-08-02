package com.kmmcl.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kmmcl.core.auth.AuthService
import com.kmmcl.core.auth.AuthState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    authService: AuthService,
    onBack: () -> Unit
) {
    val accounts by remember { mutableStateOf(authService.accounts.toList()) }
    val currentIndex by remember { mutableStateOf(authService.currentIndex) }
    var playerName by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = { TextButton(onClick = onBack) { Text("返回") } }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            // Account header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("账户管理", style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = { showAddDialog = true }) { Text("添加") }
                }
            }

            // Account list
            if (accounts.isEmpty()) {
                item {
                    Text(
                        "暂无账户，请添加一个离线登录",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                }
            } else {
                itemsIndexed(accounts) { index, account ->
                    AccountItem(
                        account = account,
                        isCurrent = index == currentIndex,
                        onSelect = { authService.switchAccount(index) },
                        onDelete = { authService.deleteAccount(index) }
                    )
                }
            }

            // About
            item {
                Spacer(modifier = Modifier.height(24.dp))
                Text("关于", style = MaterialTheme.typography.titleMedium)
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Kmmcl Minecraft Launcher", style = MaterialTheme.typography.bodyMedium)
                        Text("版本 1.0.0", style = MaterialTheme.typography.bodySmall)
                        Text("基于 Compose Multiplatform", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }

    // Add account dialog
    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("离线登录") },
            text = {
                OutlinedTextField(
                    value = playerName,
                    onValueChange = { playerName = it },
                    label = { Text("玩家名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        authService.loginOffline(playerName)
                        playerName = ""
                        showAddDialog = false
                    },
                    enabled = playerName.isNotBlank()
                ) { Text("登录") }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text("取消") }
            }
        )
    }
}

@Composable
fun AccountItem(
    account: AuthState,
    isCurrent: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onSelect() },
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrent)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Person,
                contentDescription = null,
                tint = if (isCurrent) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(account.username, fontWeight = FontWeight.SemiBold)
                Text(
                    if (isCurrent) "当前账户" else account.authType.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (isCurrent) {
                SuggestionChip(onClick = {}, label = { Text("使用中") })
            }
            if (!isCurrent) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, "删除", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
