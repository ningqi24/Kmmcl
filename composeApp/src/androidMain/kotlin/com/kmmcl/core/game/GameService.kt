
package com.kmmcl.core.game

import com.kmmcl.core.download.DownloadManager
import com.kmmcl.core.jre.DeviceArch
import java.io.File
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

class GameService(
    private val versionService: VersionService,
    private val downloadManager: DownloadManager,
    private val gameDir: File
) {
    suspend fun prepareGame(
        versionId: String,
        versionUrl: String,
        onProgress: (String) -> Unit = {}
    ): Result<File> = runCatching {
        val versionsDir = File(gameDir, "versions/$versionId")
        versionsDir.mkdirs()

        onProgress("正在获取版本元数据...")
        val detail = versionService.fetchVersionDetail(versionUrl).getOrThrow()

        // Download client.jar
        val clientUrl = MojangMirror.mirror(detail.downloads.client.url)
        if (clientUrl.isEmpty()) throw IllegalStateException("无法获取 $versionId 下载地址")

        val jarFile = File(versionsDir, "$versionId.jar")
        onProgress("正在下载 $versionId.jar...")
        downloadManager.downloadFile(clientUrl, jarFile.absolutePath) { pct ->
            onProgress("下载客户端 ${(pct * 100).toInt()}%")
        }.getOrThrow()

        // Download asset index
        val assetIndex = detail.assetIndex
        if (assetIndex.url.isNotEmpty()) {
            val assetsDir = File(gameDir, "assets/indexes")
            assetsDir.mkdirs()
            val idxFile = File(assetsDir, "${assetIndex.id}.json")
            if (!idxFile.exists()) {
                onProgress("正在下载资源索引...")
                downloadManager.downloadFile(
                    MojangMirror.mirror(assetIndex.url), idxFile.absolutePath
                ).getOrThrow()
            }
        }

        // Download libraries + native classifiers in parallel (max 8 concurrent)
        val libsDir = File(gameDir, "libraries")
        libsDir.mkdirs()

        // Collect all download tasks: main artifacts + native classifiers
        data class LibTask(val path: String, val url: String, val label: String)

        val tasks = mutableListOf<LibTask>()
        val nativeKey = DeviceArch.nativeKey

        for (lib in detail.libraries) {
            // Main artifact
            val art = lib.downloads.artifact
            if (art.path.isNotEmpty() && art.url.isNotEmpty()) {
                if (!File(libsDir, art.path).exists()) {
                    tasks.add(LibTask(art.path, MojangMirror.mirror(art.url), "lib"))
                }
            }
            // Native classifiers
            if (lib.natives.isNotEmpty()) {
                val nativeSuffix = lib.natives["linux"] ?: continue
                val classifiers = lib.downloads.classifiers
                // Try arch-specific first, then generic
                val classifierKey = "$nativeSuffix-$nativeKey"
                val nativeArt = classifiers[classifierKey] ?: classifiers[nativeSuffix]
                if (nativeArt != null && nativeArt.path.isNotEmpty() && nativeArt.url.isNotEmpty()) {
                    val f = File(libsDir, nativeArt.path)
                    if (!f.exists()) {
                        tasks.add(LibTask(nativeArt.path, MojangMirror.mirror(nativeArt.url), "native"))
                    }
                }
            }
        }

        val totalTasks = tasks.size
        if (totalTasks > 0) {
            val semaphore = Semaphore(8)
            var completed = 0
            coroutineScope {
                tasks.map { task ->
                    async {
                        semaphore.withPermit {
                            val target = File(libsDir, task.path)
                            target.parentFile?.mkdirs()
                            downloadManager.downloadFile(task.url, target.absolutePath).getOrThrow()
                            completed++
                            onProgress("依赖库 $completed/$totalTasks")
                        }
                    }
                }.awaitAll()
            }
        }

        onProgress("准备完成")
        gameDir
    }
}
