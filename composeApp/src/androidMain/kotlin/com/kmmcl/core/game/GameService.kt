
package com.kmmcl.core.game

import com.kmmcl.core.download.DownloadManager
import com.kmmcl.core.jre.DeviceArch
import com.kmmcl.data.model.Library
import com.kmmcl.data.model.Rule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.util.jar.JarFile

class GameService(
    private val versionService: VersionService,
    private val downloadManager: DownloadManager,
    private val gameDir: File
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun prepareGame(
        versionId: String,
        versionUrl: String,
        onProgress: (String) -> Unit = {}
    ): Result<File> = runCatching {
        val versionsDir = File(gameDir, "versions/$versionId")
        versionsDir.mkdirs()

        onProgress("正在获取版本元数据...")
        val detail = versionService.fetchVersionDetail(versionUrl).getOrThrow()

        // ---- Client JAR ----
        val clientUrl = MojangMirror.mirror(detail.downloads.client.url)
        if (clientUrl.isEmpty()) throw IllegalStateException("无法获取 $versionId 下载地址")

        val jarFile = File(versionsDir, "$versionId.jar")
        onProgress("正在下载 $versionId.jar...")
        downloadManager.downloadFile(clientUrl, jarFile.absolutePath) { pct ->
            onProgress("下载客户端 ${(pct * 100).toInt()}%")
        }.getOrThrow()

        // ---- Asset Index ----
        val assetIndex = detail.assetIndex
        if (assetIndex.url.isNotEmpty()) {
            val assetsIndexDir = File(gameDir, "assets/indexes")
            assetsIndexDir.mkdirs()
            val idxFile = File(assetsIndexDir, "${assetIndex.id}.json")
            if (!idxFile.exists()) {
                onProgress("正在下载资源索引...")
                downloadManager.downloadFile(
                    MojangMirror.mirror(assetIndex.url), idxFile.absolutePath
                ).getOrThrow()
            }
        }

        // ---- Libraries + Natives (filtered by rules, parallel 8) ----
        val libsDir = File(gameDir, "libraries")
        libsDir.mkdirs()
        val nativeKey = DeviceArch.nativeKey

        val filteredLibs = detail.libraries.filter { isLibraryAllowed(it.rules) }

        data class LibTask(val path: String, val url: String, val label: String)

        val tasks = mutableListOf<LibTask>()

        for (lib in filteredLibs) {
            val art = lib.downloads.artifact
            if (art.path.isNotEmpty() && art.url.isNotEmpty()) {
                if (!File(libsDir, art.path).exists()) {
                    tasks.add(LibTask(art.path, MojangMirror.mirror(art.url), "lib"))
                }
            }
            // Native classifiers
            if (lib.natives.isNotEmpty()) {
                val nativeArt = lib.downloads.classifiers[nativeKey]
                    ?: lib.downloads.classifiers["natives-linux"]
                if (nativeArt != null && nativeArt.path.isNotEmpty() && nativeArt.url.isNotEmpty()) {
                    if (!File(libsDir, nativeArt.path).exists()) {
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

        // ---- Extract Natives ----
        val nativesDir = File(versionsDir, "natives")
        extractNatives(filteredLibs, libsDir, nativesDir, nativeKey)

        // ---- Asset Objects ----
        val indexFile = File(gameDir, "assets/indexes/${assetIndex.id}.json")
        if (indexFile.exists()) {
            downloadAssetObjects(indexFile, File(gameDir, "assets"), onProgress)
        }

        // ---- Logging Config ----
        val loggingFile = detail.logging.client.file
        val logConfigFile = File(versionsDir, "logging.xml")
        if (!logConfigFile.exists() && loggingFile.url.isNotEmpty()) {
            onProgress("下载日志配置...")
            downloadManager.downloadFile(
                MojangMirror.mirror(loggingFile.url), logConfigFile.absolutePath
            ).getOrThrow()
        }

        onProgress("准备完成")
        gameDir
    }

    private fun isLibraryAllowed(rules: List<Rule>): Boolean {
        if (rules.isEmpty()) return true
        var allowed = true
        for (rule in rules) {
            if (rule.os != null) {
                val osMatches = rule.os.name in listOf("linux")
                if (osMatches) {
                    allowed = rule.action == "allow"
                }
            }
        }
        return allowed
    }

    private suspend fun extractNatives(
        libraries: List<Library>,
        libsDir: File,
        nativesDir: File,
        nativeKey: String
    ) {
        if (nativesDir.listFiles()?.isNotEmpty() == true) return // already extracted
        nativesDir.mkdirs()

        for (lib in libraries) {
            val nativeArt = lib.downloads.classifiers[nativeKey]
                ?: lib.downloads.classifiers["natives-linux"] ?: continue
            val jar = File(libsDir, nativeArt.path)
            if (!jar.exists()) continue

            withContext(Dispatchers.IO) {
                try {
                    JarFile(jar).use { jf ->
                        jf.entries().asSequence().forEach { entry ->
                            if (!entry.isDirectory && !entry.name.startsWith("META-INF")) {
                                val target = File(nativesDir, entry.name)
                                target.parentFile?.mkdirs()
                                if (!target.exists()) {
                                    jf.getInputStream(entry).use { ins ->
                                        target.outputStream().use { out -> ins.copyTo(out) }
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private suspend fun downloadAssetObjects(
        indexFile: File,
        assetsDir: File,
        onProgress: (String) -> Unit
    ) {
        val indexData = withContext(Dispatchers.IO) { indexFile.readText() }
        val indexJson = withContext(Dispatchers.Default) { json.parseToJsonElement(indexData).jsonObject }
        val objects = indexJson["objects"]?.jsonObject ?: return
        val total = objects.size
        var done = 0

        for ((hash, _) in objects) {
            val subDir = hash.substring(0, 2)
            val objFile = File(assetsDir, "objects/$subDir/$hash")
            if (!objFile.exists()) {
                objFile.parentFile?.mkdirs()
                val url = "https://resources.download.minecraft.net/$subDir/$hash"
                onProgress("资源 ${done + 1}/$total")
                downloadManager.downloadFile(
                    MojangMirror.mirror(url), objFile.absolutePath
                ).getOrThrow()
            }
            done++
        }
    }
}
