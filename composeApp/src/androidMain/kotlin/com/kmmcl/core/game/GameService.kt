package com.kmmcl.core.game

import com.kmmcl.core.download.DownloadManager
import com.kmmcl.core.download.DownloadProvider
import com.kmmcl.core.download.ProgressReporter
import com.kmmcl.core.download.remapUrl
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
import kotlinx.serialization.json.jsonObject
import java.io.File
import java.util.jar.JarFile

class GameService(
    private val manifestResolver: ManifestResolver,
    private val downloadManager: DownloadManager,
    private val gameDir: File,
    private val provider: DownloadProvider = DownloadProvider.BMCLAPI,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun prepareGame(
        versionUrl: String,
        onProgress: (String, Float) -> Unit = { _, _ -> },
    ): Result<File> = runCatching {
        val reporter = ProgressReporter(onProgress)

        // Phase 0: Resolve inheritance chain (0-3%)
        reporter.startPhase("解析版本继承链", 0.03f)
        val resolved = manifestResolver.resolve(versionUrl).getOrThrow()
        reporter.endPhase()

        val versionId = resolved.id
        val versionsDir = File(gameDir, "versions/$versionId")
        versionsDir.mkdirs()

        // Phase 1: client JAR (3-25%)
        reporter.startPhase("下载客户端", 0.22f)
        val clientUrl = remapUrl(resolved.downloads.client.url, provider)
        if (clientUrl.isEmpty()) throw IllegalStateException("无法获取 $versionId 下载地址")

        val jarFile = File(versionsDir, "$versionId.jar")
        downloadManager.downloadFile(clientUrl, jarFile.absolutePath) { pct ->
            reporter.reportFraction(pct, "下载客户端 ${(pct * 100).toInt()}%")
        }.getOrThrow()
        reporter.endPhase()

        // Phase 2: asset index (25-28%)
        reporter.startPhase("下载资源索引", 0.03f)
        val assetIndex = resolved.assetIndex
        if (assetIndex.url.isNotEmpty()) {
            val assetsIndexDir = File(gameDir, "assets/indexes")
            assetsIndexDir.mkdirs()
            val idxFile = File(assetsIndexDir, "${assetIndex.id}.json")
            if (!idxFile.exists()) {
                downloadManager.downloadFile(
                    remapUrl(assetIndex.url, provider), idxFile.absolutePath
                ).getOrThrow()
            }
        }
        reporter.endPhase()

        // Phase 3: libraries + natives, parallel 8 (28-70%)
        reporter.startPhase("下载依赖库", 0.42f)
        val libsDir = File(gameDir, "libraries")
        libsDir.mkdirs()

        val filteredLibs = resolved.libraries.filter { isLibraryAllowed(it.rules) }

        data class LibTask(val path: String, val url: String)

        val tasks = mutableListOf<LibTask>()
        for (lib in filteredLibs) {
            val art = lib.downloads.artifact
            if (art.path.isNotEmpty() && art.url.isNotEmpty()) {
                if (!File(libsDir, art.path).exists()) {
                    tasks.add(LibTask(art.path, remapUrl(art.url, provider)))
                }
            }
            if (lib.natives.isNotEmpty()) {
                val nativeSuffix = lib.natives["linux"] ?: continue
                val archClassifierKey = "$nativeSuffix-${DeviceArch.nativeKey}"
                val nativeArt = lib.downloads.classifiers[archClassifierKey]
                    ?: lib.downloads.classifiers[nativeSuffix]
                if (nativeArt != null && nativeArt.path.isNotEmpty() && nativeArt.url.isNotEmpty()) {
                    if (!File(libsDir, nativeArt.path).exists()) {
                        tasks.add(LibTask(nativeArt.path, remapUrl(nativeArt.url, provider)))
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
                            reporter.reportStep(completed, totalLibs, "依赖库 $completed/$totalLibs")
                        }
                    }
                }.awaitAll()
            }
        }
        reporter.endPhase()

        // Phase 4: extract natives (70-75%)
        reporter.startPhase("解压原生库", 0.05f)
        val nativesDir = File(versionsDir, "natives")
        extractNatives(filteredLibs, libsDir, nativesDir)
        reporter.endPhase()

        // Phase 5: asset objects (75-90%)
        reporter.startPhase("下载资源", 0.15f)
        val indexFile = File(gameDir, "assets/indexes/${assetIndex.id}.json")
        if (indexFile.exists()) {
            downloadAssetObjects(indexFile, File(gameDir, "assets")) { label, stepFraction ->
                reporter.reportFraction(stepFraction, label)
            }
        }
        reporter.endPhase()

        // Phase 6: Log4j patch config (90-93%)
        reporter.startPhase("写入日志配置", 0.03f)
        Log4jPatcher.ensureConfigWritten(gameDir.absolutePath) { path, content ->
            val f = File(path)
            if (!f.exists()) {
                f.parentFile?.mkdirs()
                f.writeText(content)
            }
        }
        reporter.endPhase()

        // Phase 7: generate classpath + manifest (93-99%)
        reporter.startPhase("生成类路径", 0.06f)
        generateClasspath(versionsDir, libsDir, filteredLibs, versionId)

        // Also persist a resolved manifest JSON for GameLauncher reference
        val manifestFile = File(versionsDir, "resolved_manifest.json")
        val manifestJson = kotlinx.serialization.json.buildJsonObject {
            put("id", resolved.id)
            put("mainClass", resolved.mainClass)
            put("type", resolved.type)
            put("assetIndexId", resolved.assetIndex.id)
        }
        manifestFile.writeText(manifestJson.toString())
        reporter.endPhase()

        reporter.reportFraction(1f, "准备完成")
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
        versionId: String,
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
        nativesDir: File,
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
        onProgress: (String, Float) -> Unit,
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
                val url = remapUrl(
                    "https://resources.download.minecraft.net/$subDir/$hash", provider
                )
                downloadManager.downloadFile(url, objFile.absolutePath).getOrThrow()
            }
            done++
            onProgress("资源 $done/$total", done.toFloat() / total)
        }
    }

    /** 
     * Lightweight version detail fetch for direct use (bypasses inheritance chain). 
     * Used by GameViewModel when the user just wants to browse version info. 
     */
    suspend fun fetchVersionDetail(versionUrl: String) =
        manifestResolver.resolve(versionUrl)
}
