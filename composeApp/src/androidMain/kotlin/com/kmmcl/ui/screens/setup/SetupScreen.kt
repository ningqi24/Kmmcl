
package com.kmmcl.ui.screens.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SetupScreen(
    viewModel: SetupViewModel,
    onComplete: () -> Unit
) {
    val state by viewModel.state.collectAsState()

    // Auto-trigger download on first composition
    LaunchedEffect(Unit) {
        viewModel.startSetup()
    }

    // Navigate away when done
    LaunchedEffect(state.step) {
        if (state.step is SetupStep.Done) {
            onComplete()
        }
    }

    when (val step = state.step) {
        is SetupStep.Error -> {
            SetupContent(
                title = "出错了",
                message = step.message,
                progress = 0f,
                showRetry = true,
                onRetry = { viewModel.startSetup() }
            )
        }
        is SetupStep.CheckJre -> SetupContent(
            title = "正在检查环境...",
            message = state.progressText.ifEmpty { "请稍候..." },
            progress = state.progressPct,
            showRetry = false,
            onRetry = {}
        )
        is SetupStep.DownloadJre -> SetupContent(
            title = "正在下载 Java 运行环境",
            message = state.progressText.ifEmpty { "请稍候..." },
            progress = state.progressPct,
            showRetry = false,
            onRetry = {}
        )
        is SetupStep.DownloadGame -> SetupContent(
            title = "正在下载 Minecraft",
            message = state.progressText.ifEmpty { "请稍候..." },
            progress = state.progressPct,
            showRetry = false,
            onRetry = {}
        )
        is SetupStep.Done -> SetupContent(
            title = "准备完成",
            message = state.progressText.ifEmpty { "请稍候..." },
            progress = state.progressPct,
            showRetry = false,
            onRetry = {}
        )
    }
}

@Composable
private fun SetupContent(
    title: String,
    message: String,
    progress: Float,
    showRetry: Boolean,
    onRetry: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Kmmcl",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Minecraft 启动器",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(48.dp))
            Text(
                text = title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(12.dp))

            if (progress > 0f) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(12.dp))
            }

            Text(
                text = message,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (showRetry) {
                Spacer(Modifier.height(24.dp))
                Button(onClick = onRetry) {
                    Text("重试")
                }
            }
        }
    }
}
