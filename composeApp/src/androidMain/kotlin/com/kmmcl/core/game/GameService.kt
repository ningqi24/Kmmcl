
package com.kmmcl.core.game

import com.kmmcl.core.download.DownloadManager
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

        // Download libraries in parallel (max 8 concurrent)
        val libsDir = File(gameDir, "libraries")
        libsDir.mkdirs()
        val pending = detail.libraries.filter { lib ->
            val artifact = lib.downloads.artifact
            artifact.path.isNotEmpty() && artifact.url.isNotEmpty() &&
                !File(libsDir, artifact.path).exists()
        }
        val totalLibs = pending.size

        if (totalLibs > 0) {
            val semaphore = Semaphore(8)
            var completed = 0
            coroutineScope {
                pending.map { lib ->
                    async {
                        semaphore.withPermit {
                            val artifact = lib.downloads.artifact
                            val libFile = File(libsDir, artifact.path)
                            libFile.parentFile?.mkdirs()
                            downloadManager.downloadFile(
                                MojangMirror.mirror(artifact.url), libFile.absolutePath
                            ).getOrThrow()
                            completed++
                            onProgress("依赖库 $completed/$totalLibs")
                        }
                    }
                }.awaitAll()
            }
        }

        onProgress("准备完成")
        gameDir
    }
}
