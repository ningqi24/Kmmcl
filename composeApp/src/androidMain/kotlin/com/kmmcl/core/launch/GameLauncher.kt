
package com.kmmcl.core.launch

import android.util.Log
import com.kmmcl.core.download.DownloadManager
import com.kmmcl.core.game.VersionService
import com.kmmcl.core.jre.JreManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class GameLauncher(
    private val jreManager: JreManager,
    private val versionService: VersionService,
    private val downloadManager: DownloadManager
) {
    companion object {
        private const val TAG = "GameLauncher"
    }

    suspend fun launch(
        versionId: String,
        versionUrl: String,
        gameDir: File,
        onLog: (String) -> Unit = {}
    ): Result<Process> = runCatching {
        val versionDir = File(gameDir, "versions/$versionId")

        // Fetch version detail for mainClass and other metadata
        val detail = versionService.fetchVersionDetail(versionUrl).getOrThrow()
        val mainClass = detail.mainClass.ifEmpty { "net.minecraft.client.main.Main" }

        val clientJar = File(versionDir, "$versionId.jar")
        if (!clientJar.exists()) throw IllegalStateException("找不到客户端 JAR: ${clientJar.absolutePath}")

        val nativesDir = File(versionDir, "natives")
        val assetsDir = File(gameDir, "assets")
        val loggingConfig = File(versionDir, "logging.xml")
        val java = jreManager.javaBin

        if (!java.exists()) throw IllegalStateException("JRE 尚未就绪: ${java.absolutePath}")

        // Use generated classpath.txt from GameService, fallback to client jar only
        val classpathFile = File(versionDir, "classpath.txt")
        val classpath = if (classpathFile.exists()) {
            classpathFile.readText().trim()
        } else {
            clientJar.absolutePath
        }

        val args = buildList {
            add(java.absolutePath)
            add("-Xmx2G")
            add("-Xms512M")
            if (loggingConfig.exists()) {
                add("-Dlog4j.configurationFile=${loggingConfig.absolutePath}")
            }
            add("-Djava.library.path=${nativesDir.absolutePath}")
            add("-Dminecraft.client.jar=${clientJar.absolutePath}")
            add("-Dminecraft.launcher.brand=kmmcl")
            add("-Dminecraft.launcher.version=1.0")
            add("-cp")
            add(classpath)
            add(mainClass)
            add("--username")
            add("Player")
            add("--version")
            add(versionId)
            add("--gameDir")
            add(gameDir.absolutePath)
            add("--assetsDir")
            add(assetsDir.absolutePath)
            add("--assetIndex")
            add(detail.assetIndex.id.ifEmpty { versionId })
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

        val process = withContext(Dispatchers.IO) { pb.start() }

        // Pipe output in background
        process.inputStream.bufferedReader().useLines { lines ->
            lines.forEach { line ->
                Log.i(TAG, line)
                onLog(line)
            }
        }

        process
    }
}
