package com.kmmcl.core.launch

import android.util.Log
import com.kmmcl.core.game.LaunchPipeline
import com.kmmcl.core.game.ManifestResolver
import com.kmmcl.core.jre.JreManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class GameLauncher(
    private val jreManager: JreManager,
    private val manifestResolver: ManifestResolver,
    private val gameDir: File,
) {
    companion object {
        private const val TAG = "GameLauncher"
        // Dedicated scope that outlives a single launch() call,
        // so process output reading runs truly in the background.
        private val outputScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    /**
     * Launch Minecraft using the LaunchPipeline.
     *
     * @param versionUrl  URL to the version detail JSON (will be resolved via manifest)
     * @param onLog       callback for each line of game output
     * @param onExit      callback when the process exits, with exit code
     * @return the running [Process]
     */
    suspend fun launch(
        versionUrl: String,
        onLog: (String) -> Unit = {},
        onExit: ((Int) -> Unit)? = null,
    ): Result<Process> = runCatching {
        val resolved = manifestResolver.resolve(versionUrl).getOrThrow()

        val versionId = resolved.id
        val versionDir = File(gameDir, "versions/$versionId")

        val clientJar = File(versionDir, "$versionId.jar")
        if (!clientJar.exists()) throw IllegalStateException("找不到客户端 JAR: ${clientJar.absolutePath}")

        val nativesDir = File(versionDir, "natives")
        val assetsDir = File(gameDir, "assets")
        val libsDir = File(gameDir, "libraries")
        val java = jreManager.javaBin

        if (!java.exists()) throw IllegalStateException("JRE 尚未就绪: ${java.absolutePath}")

        val cmd = LaunchPipeline.build(
            manifest = resolved,
            gameDir = gameDir.absolutePath,
            javaPath = java.absolutePath,
            authName = "Player",
            authUuid = "00000000-0000-0000-0000-000000000000",
            nativesDir = nativesDir.absolutePath,
            libsDir = libsDir.absolutePath,
            versionJar = clientJar.absolutePath,
            ramMin = 512,
            ramMax = 2048,
        )

        onLog("启动命令: ${cmd.commandLine.joinToString(" ")}")

        val pb = ProcessBuilder(cmd.commandLine)
            .directory(versionDir)
            .redirectErrorStream(true)

        val process = withContext(Dispatchers.IO) { pb.start() }

        // Non-blocking: pipe output in a background coroutine
        outputScope.launch {
            try {
                process.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        Log.i(TAG, line)
                        onLog(line)
                    }
                }
            } catch (_: Exception) {
                // Process ended, stream closed
            }
            val exitCode = try { process.waitFor() } catch (_: Exception) { -1 }
            onExit?.invoke(exitCode)
        }

        process
    }
}
