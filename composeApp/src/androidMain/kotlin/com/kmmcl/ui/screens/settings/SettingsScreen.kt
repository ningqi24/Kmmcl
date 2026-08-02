package com.kmmcl.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kmmcl.core.auth.AuthService

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    authService: AuthService,
    onBack: () -> Unit
) {
    val authState by remember { mutableStateOf(authService.currentAuth) }
    var playerName by remember { mutableStateOf("") }
    var loginResult by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("返回") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Text("账户", style = MaterialTheme.typography.titleMedium)

            if (authState.isLoggedIn) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("已登录 (离线模式)", style = MaterialTheme.typography.bodyMedium)
                        Text("用户名: ${authState.username}", style = MaterialTheme.typography.bodySmall)
                        Text("UUID: ${authState.uuid}", style = MaterialTheme.typography.bodySmall)

                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(onClick = {
                            authService.logout()
                        }) {
                            Text("登出")
                        }
                    }
                }
            } else {
                OutlinedTextField(
                    value = playerName,
                    onValueChange = { playerName = it },
                    label = { Text("玩家名称") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        val result = authService.loginOffline(playerName)
                        loginResult = if (result.isLoggedIn) {
                            "登录成功: ${result.username}"
                        } else {
                            "登录失败"
                        }
                    },
                    enabled = playerName.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("离线登录")
                }

                loginResult?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                }
            }

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
