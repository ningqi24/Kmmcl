package com.kmmcl.ui.screens.server

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kmmcl.data.model.ServerInfo
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("kmmcl_servers", Context.MODE_PRIVATE) }
    val json = remember { Json { ignoreUnknownKeys = true } }

    var servers by remember {
        mutableStateOf(loadServers(prefs, json))
    }

    var showAddDialog by remember { mutableStateOf(false) }
    var editName by remember { mutableStateOf("") }
    var editHost by remember { mutableStateOf("") }
    var editPort by remember { mutableStateOf("25565") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("服务器列表") },
                navigationIcon = { TextButton(onClick = onBack) { Text("返回") } },
                actions = {
                    IconButton(onClick = {
                        editName = ""
                        editHost = ""
                        editPort = "25565"
                        showAddDialog = true
                    }) {
                        Icon(Icons.Default.Add, "添加服务器")
                    }
                }
            )
        }
    ) { padding ->
        if (servers.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Public, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("暂无服务器", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("点击右上角 + 添加", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
                itemsIndexed(servers) { index, server ->
                    ServerItem(
                        server = server,
                        onDelete = {
                            servers = servers.toMutableList().apply { removeAt(index) }
                            saveServers(prefs, json, servers)
                        }
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("添加服务器") },
            text = {
                Column {
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("服务器名称") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = editHost,
                        onValueChange = { editHost = it },
                        label = { Text("IP 地址") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = editPort,
                        onValueChange = { editPort = it.filter { c -> c.isDigit() } },
                        label = { Text("端口") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.width(120.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val port = editPort.toIntOrNull() ?: 25565
                        servers = servers + ServerInfo(editName, editHost, port)
                        saveServers(prefs, json, servers)
                        showAddDialog = false
                    },
                    enabled = editName.isNotBlank() && editHost.isNotBlank()
                ) { Text("添加") }
            },
            dismissButton = { TextButton(onClick = { showAddDialog = false }) { Text("取消") } }
        )
    }
}

@Composable
fun ServerItem(server: ServerInfo, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Public, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(server.name, fontWeight = FontWeight.SemiBold)
                Text(
                    "${server.host}:${server.port}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, "删除", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

private fun loadServers(prefs: android.content.SharedPreferences, json: Json): List<ServerInfo> {
    val raw = prefs.getString("servers", null) ?: return emptyList()
    return try {
        json.decodeFromString<List<ServerInfo>>(raw)
    } catch (_: Exception) {
        emptyList()
    }
}

private fun saveServers(prefs: android.content.SharedPreferences, json: Json, servers: List<ServerInfo>) {
    prefs.edit().putString("servers", json.encodeToString(servers)).apply()
}
