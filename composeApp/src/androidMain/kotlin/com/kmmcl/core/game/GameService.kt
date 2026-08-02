
package com.kmmcl.core.game

import android.os.Build
import com.kmmcl.core.download.DownloadManager
import com.kmmcl.core.jre.DeviceArch
import com.kmmcl.data.model.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

class GameService(
    private val downloadManager: DownloadManager,
    private val gameDir: File
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val client = HttpClient()

    suspend fun fetchVersions(): Result<List<VersionEntry>> = runCatching {
        val manifest: VersionManifest = client.get("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json").body()
        manifest.versions.filter { it.type == "release" }
    }

    suspend fun downloadVersion(versionEntry: VersionEntry, onProgress: (String) -> Unit): Result<Unit> = runCatching {
        val detail: VersionDetail = client.get(versionEntry.url).body()
        val versionDir = File(gameDir, "versions/${versionEntry.id}")
        versionDir.mkdirs()

        // --- Client JAR ---
        val clientUrl = detail.downloads.client.url
        val clientJar = File(versionDir, "${versionEntry.id}.jar")
        if (!clientJar.exists()) {
            onProgress("下载 ${versionEntry.id}.jar")
            downloadManager.downloadFile(clientUrl, clientJar.absolutePath) {}.getOrThrow()
        }

        // --- Libraries ---
        val libDir = File(gameDir, "libraries")
        val filteredLibs = detail.libraries.filter { lib ->
            isLibraryAllowed(lib.rules)
        }
        val classpathParts = mutableListOf<String>()

        for ((i, lib) in filteredLibs.withIndex()) {
            val artifact = lib.downloads.artifact
            val libFile = File(libDir, artifact.path)
            if (!libFile.exists()) {
                val pct = if (filteredLibs.isNotEmpty()) i.toFloat() / filteredLibs.size else 0f
                onProgress("下载库 ${(pct * 100).toInt()}% - ${lib.name}")
                libFile.parentFile?.mkdirs()
                downloadManager.downloadFile(artifact.url, libFile.absolutePath) {}.getOrThrow()
            }
            classpathParts.add(libFile.absolutePath)

            // Native libraries
            val nativeKey = DeviceArch.nativeKey
            val nativeArtifact = lib.downloads.classifiers[nativeKey]
            if (nativeArtifact != null && lib.natives.containsKey(nativeKey)) {
                val nativeFile = File(libDir, nativeArtifact.path)
                if (!nativeFile.exists()) {
                    nativeFile.parentFile?.mkdirs()
                    downloadManager.downloadFile(nativeArtifact.url, nativeFile.absolutePath) {}.getOrThrow()
                }
            }
        }

        // Write classpath file for launcher
        val classpath = classpathParts.joinToString(File.pathSeparator.toString())
        File(versionDir, "classpath.txt").writeText(classpath)

        // Extract natives
        extractNatives(filteredLibs, libDir, File(versionDir, "natives"))

        // --- Asset Index ---
        val assetIndex = detail.assetIndex
        val assetsDir = File(gameDir, "assets")
        val indexFile = File(assetsDir, "indexes/${assetIndex.id}.json")
        indexFile.parentFile?.mkdirs()
        if (!indexFile.exists()) {
            onProgress("下载资源索引")
            downloadManager.downloadFile(assetIndex.url, indexFile.absolutePath) {}.getOrThrow()
        }

        // --- Asset Objects ---
        downloadAssetObjects(indexFile, assetsDir, onProgress)

        // --- Logging Config ---
        val loggingFile = detail.logging.client.file
        val logConfigFile = File(versionDir, "logging.xml")
        if (!logConfigFile.exists() && loggingFile.url.isNotEmpty()) {
            onProgress("下载 Logging 配置")
            downloadManager.downloadFile(loggingFile.url, logConfigFile.absolutePath) {}.getOrThrow()
        }
    }

    private fun isLibraryAllowed(rules: List<Rule>): Boolean {
        if (rules.isEmpty()) return true
        var allowed = true
        for (rule in rules) {
            if (rule.os != null) {
                val osMatches = when (rule.os.name) {
                    "linux" -> true
                    "osx", "macos" -> false
                    "windows" -> false
                    else -> false
                }
                if (osMatches) {
                    allowed = rule.action == "allow"
                }
            }
        }
        return allowed
    }

    private suspend fun extractNatives(
        libraries: List<Library>,
        libDir: File,
        nativesDir: File
    ) {
        nativesDir.mkdirs()
        val nativeKey = DeviceArch.nativeKey
        for (lib in libraries) {
            val classifier = lib.natives[nativeKey] ?: continue
            val artifact = lib.downloads.classifiers[nativeKey] ?: continue
            val jar = File(libDir, artifact.path)
            if (!jar.exists()) continue
            withContext(Dispatchers.IO) {
                try {
                    java.util.jar.JarFile(jar).use { jf ->
                        jf.entries().asSequence().forEach { entry ->
                            if (!entry.isDirectory && !entry.name.startsWith("META-INF")) {
                                val target = File(nativesDir, entry.name)
                                target.parentFile?.mkdirs()
                                jf.getInputStream(entry).use { ins ->
                                    target.outputStream().use { out -> ins.copyTo(out) }
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
        if (!indexFile.exists()) return

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
                onProgress("下载资源 ${done + 1}/$total")
                downloadManager.downloadFile(url, objFile.absolutePath) {}.getOrThrow()
            }
            done++
        }
    }
}
