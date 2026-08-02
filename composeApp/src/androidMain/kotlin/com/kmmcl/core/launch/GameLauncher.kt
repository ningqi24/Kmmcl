
package com.kmmcl.core.launch

import android.util.Log
import com.kmmcl.core.download.DownloadManager
import com.kmmcl.core.jre.JreManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class GameLauncher(
    private val jreManager: JreManager,
    private val downloadManager: DownloadManager
) {
    companion object {
        private const val TAG = "GameLauncher"
    }

    suspend fun launch(
        versionId: String,
        gameDir: File,
        onLog: (String) -> Unit = {}
    ): Result<Process> = runCatching {
        val versionDir = File(gameDir, "versions/$versionId")
        val clientJar = File(versionDir, "$versionId.jar")
        if (!clientJar.exists()) throw IllegalStateException("找不到客户端 JAR: ${clientJar.absolutePath}")

        val nativesDir = File(versionDir, "natives")
        val librariesDir = File(gameDir, "libraries")
        val assetsDir = File(gameDir, "assets")
        val loggingConfig = File(versionDir, "logging.xml")
        val java = jreManager.javaBin

        if (!java.exists()) throw IllegalStateException("JRE 尚未就绪: ${java.absolutePath}")

        // Use Class-Path from classpath.txt if available
        val classpathFile = File(versionDir, "classpath.txt")
        val classpath = if (classpathFile.exists()) {
            classpathFile.readText().trim() + File.pathSeparator + clientJar.absolutePath
        } else {
            clientJar.absolutePath
        }

        val args = buildList {
            add(java.absolutePath)
            // Memory
            add("-Xmx2G")
            add("-Xms512M")
            // Logging
            if (loggingConfig.exists()) {
                add("-Dlog4j.configurationFile=${loggingConfig.absolutePath}")
            }
            // JVM opts
            add("-Djava.library.path=${nativesDir.absolutePath}")
            add("-Dminecraft.client.jar=${clientJar.absolutePath}")
            add("-Dminecraft.launcher.brand=kmmcl")
            add("-Dminecraft.launcher.version=1.0")
            // Classpath
            add("-cp")
            add(classpath)
            // Main class - use as-is, no splitting
            add("net.minecraft.client.main.Main")
            // Game args
            add("--username")
            add("Player")
            add("--version")
            add(versionId)
            add("--gameDir")
            add(gameDir.absolutePath)
            add("--assetsDir")
            add(assetsDir.absolutePath)
            add("--assetIndex")
            add(versionId)
            add("--uuid")
            add("00000000-0000-0000-0000-000000000000")
            add("--accessToken")
            add("0")
            add("--userType")
            add("mojang")
            add("--versionType")
            add("release")
        }

        onLog("启动命令: ${args.joinToString(" ")}")

        val pb = ProcessBuilder(args)
            .directory(versionDir)
            .redirectErrorStream(true)

        val process = pb.start()

        // Pipe output
        process.inputStream.bufferedReader().useLines { lines ->
            lines.forEach { line ->
                Log.i(TAG, line)
                onLog(line)
            }
        }

        process
    }
}
