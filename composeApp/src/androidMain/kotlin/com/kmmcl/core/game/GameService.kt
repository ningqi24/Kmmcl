
package com.kmmcl.core.game

import com.kmmcl.core.download.DownloadManager
import com.kmmcl.data.model.VersionDetail
import java.io.File

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

        val clientUrl = MojangMirror.mirror(detail.downloads.client.url)
        if (clientUrl.isEmpty()) throw IllegalStateException("无法获取 $versionId 下载地址")

        val jarFile = File(versionsDir, "$versionId.jar")
        onProgress("正在下载 $versionId.jar...")
        downloadManager.downloadFile(clientUrl, jarFile.absolutePath) { pct ->
            onProgress("下载客户端 ${(pct * 100).toInt()}%")
        }.getOrThrow()

        // download asset index
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

        // download libraries
        val libsDir = File(gameDir, "libraries")
        libsDir.mkdirs()
        var libIdx = 0
        val totalLibs = detail.libraries.size
        for (lib in detail.libraries) {
            val artifact = lib.downloads.artifact
            if (artifact.path.isNotEmpty() && artifact.url.isNotEmpty()) {
                val libFile = File(libsDir, artifact.path)
                if (!libFile.exists()) {
                    libIdx++
                    onProgress("下载依赖库 $libIdx/$totalLibs: ${lib.name}")
                    libFile.parentFile?.mkdirs()
                    downloadManager.downloadFile(
                        MojangMirror.mirror(artifact.url), libFile.absolutePath
                    ).getOrThrow()
                }
            }
        }

        onProgress("准备完成")
        gameDir
    }
}
