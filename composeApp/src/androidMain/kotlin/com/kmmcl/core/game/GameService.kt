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
import kotlinx.serialization.json.JsonObject
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
        onProgress: (String, Float) -> Unit = { _, _ -> }
    ): Result<File> = runCatching {
        val versionsDir = File(gameDir, "versions/$versionId")
        versionsDir.mkdirs()

        onProgress("正在获取版本元数据...", 0.02f)
        val detail = versionService.fetchVersionDetail(versionUrl).getOrThrow()

        // Phase 1: client JAR (2-25%)
        val clientUrl = MojangMirror.mirror(detail.downloads.client.url)
        if (clientUrl.isEmpty()) throw IllegalStateException("无法获取 $versionId 下载地址")

        val jarFile = File(versionsDir, "$versionId.jar")
        downloadManager.downloadFile(clientUrl, jarFile.absolutePath) { pct ->
            onProgress("下载客户端 ${(pct * 100).toInt()}%", 0.02f + pct * 0.23f)
        }.getOrThrow()

        // Phase 2: asset index (25-28%)
        val assetIndex = detail.assetIndex
        if (assetIndex.url.isNotEmpty()) {
            val assetsIndexDir = File(gameDir, "assets/indexes")
            assetsIndexDir.mkdirs()
            val idxFile = File(assetsIndexDir, "${assetIndex.id}.json")
            if (!idxFile.exists()) {
                onProgress("正在下载资源索引...", 0.26f)
                downloadManager.downloadFile(
                    MojangMirror.mirror(assetIndex.url), idxFile.absolutePath
                ).getOrThrow()
            }
        }

        // Phase 3: libraries + natives, parallel 8 (28-75%)
        val libsDir = File(gameDir, "libraries")
        libsDir.mkdirs()

        val filteredLibs = detail.libraries.filter { isLibraryAllowed(it.rules) }

        data class LibTask(val path: String, val url: String)

        val tasks = mutableListOf<LibTask>()

        for (lib in filteredLibs) {
            val art = lib.downloads.artifact
            if (art.path.isNotEmpty() && art.url.isNotEmpty()) {
                if (!File(libsDir, art.path).exists()) {
                    tasks.add(LibTask(art.path, MojangMirror.mirror(art.url)))
                }
            }
            if (lib.natives.isNotEmpty()) {
                val nativeSuffix = lib.natives["linux"] ?: continue
                val archClassifierKey = "$nativeSuffix-${DeviceArch.nativeKey}"
                val nativeArt = lib.downloads.classifiers[archClassifierKey]
                    ?: lib.downloads.classifiers[nativeSuffix]
                if (nativeArt != null && nativeArt.path.isNotEmpty() && nativeArt.url.isNotEmpty()) {
                    if (!File(libsDir, nativeArt.path).exists()) {
                        tasks.add(LibTask(nativeArt.path, MojangMirror.mirror(nativeArt.url)))
                    }
                }
            }
        }

        val totalLibs = tasks.size
        if (totalLibs > 0) {
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
                            val fraction = 0.28f + (completed.toFloat() / totalLibs) * 0.47f
                            onProgress("依赖库 $completed/$totalLibs", fraction)
                        }
                    }
                }.awaitAll()
            }
        }

        // Phase 4: extract natives (75-80%)
        val nativesDir = File(versionsDir, "natives")
        onProgress("解压原生库...", 0.77f)
        extractNatives(filteredLibs, libsDir, nativesDir)

        // Phase 5: asset objects (80-96%)
        val indexFile = File(gameDir, "assets/indexes/${assetIndex.id}.json")
        if (indexFile.exists()) {
            downloadAssetObjects(indexFile, File(gameDir, "assets")) { text, fraction ->
                onProgress(text, 0.80f + fraction * 0.16f)
            }
        }

        // Phase 6: logging config (96-98%)
        val loggingFile = detail.logging.client.file
        val logConfigFile = File(versionsDir, "logging.xml")
        if (!logConfigFile.exists() && loggingFile.url.isNotEmpty()) {
            onProgress("下载日志配置...", 0.97f)
            downloadManager.downloadFile(
                MojangMirror.mirror(loggingFile.url), logConfigFile.absolutePath
            ).getOrThrow()
        }

        // Phase 7: generate classpath.txt for GameLauncher
        onProgress("生成类路径...", 0.99f)
        generateClasspath(versionsDir, libsDir, filteredLibs, versionId)

        onProgress("准备完成", 1f)
        gameDir
    }

    private fun isLibraryAllowed(rules: List<Rule>): Boolean {
        if (rules.isEmpty()) return true
        var allowed = true
        for (rule in rules) {
            if (rule.os != null) {
                if (rule.os.name in listOf("linux")) {
                    allowed = rule.action == "allow"
                }
            }
        }
        return allowed
    }

    private fun generateClasspath(
        versionsDir: File,
        libsDir: File,
        libraries: List<Library>,
        versionId: String
    ) {
        val parts = mutableListOf<String>()
        parts.add(File(versionsDir, "$versionId.jar").absolutePath)
        for (lib in libraries) {
            val art = lib.downloads.artifact
            if (art.path.isNotEmpty()) {
                parts.add(File(libsDir, art.path).absolutePath)
            }
        }
        File(versionsDir, "classpath.txt").writeText(parts.joinToString(File.pathSeparator))
    }

    private suspend fun extractNatives(
        libraries: List<Library>,
        libsDir: File,
        nativesDir: File
    ) {
        if (nativesDir.listFiles()?.isNotEmpty() == true) return
        nativesDir.mkdirs()

        for (lib in libraries) {
            if (lib.natives.isEmpty()) continue
            val nativeSuffix = lib.natives["linux"] ?: continue
            val archClassifierKey = "$nativeSuffix-${DeviceArch.nativeKey}"
            val nativeArt = lib.downloads.classifiers[archClassifierKey]
                ?: lib.downloads.classifiers[nativeSuffix] ?: continue

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
        onProgress: (String, Float) -> Unit
    ) {
        val indexData = withContext(Dispatchers.IO) { indexFile.readText() }
        val indexJson: JsonObject = withContext(Dispatchers.Default) {
            json.parseToJsonElement(indexData).jsonObject
        }
        val objects: JsonObject = indexJson["objects"]?.jsonObject ?: run {
            onProgress("无资源对象", 1f)
            return
        }
        val total = objects.size
        var done = 0

        for (hash in objects.keys) {
            val subDir = hash.substring(0, 2)
            val objFile = File(assetsDir, "objects/$subDir/$hash")
            if (!objFile.exists()) {
                objFile.parentFile?.mkdirs()
                val url = "https://resources.download.minecraft.net/$subDir/$hash"
                downloadManager.downloadFile(
                    MojangMirror.mirror(url), objFile.absolutePath
                ).getOrThrow()
            }
            done++
            val fraction = done.toFloat() / total
            onProgress("资源 $done/$total", fraction)
        }
    }
}
