package com.kmmcl.core.game

import com.kmmcl.core.download.DownloadManager
import com.kmmcl.data.model.GameVersion
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
private data class VersionManifest(
    val latest: LatestVersions = LatestVersions(),
    val versions: List<VersionEntry> = emptyList()
)

@Serializable
private data class LatestVersions(
    val release: String = "",
    val snapshot: String = ""
)

@Serializable
private data class VersionEntry(
    val id: String = "",
    val type: String = "",
    val url: String = "",
    val time: String = "",
    val releaseTime: String = ""
)

@Serializable
private data class VersionDetail(
    val downloads: Downloads = Downloads()
)

@Serializable
private data class Downloads(
    val client: DownloadInfo = DownloadInfo()
)

@Serializable
private data class DownloadInfo(
    val url: String = ""
)

class GameService(
    private val httpClient: HttpClient,
    private val downloadManager: DownloadManager
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetchVersions(): Result<List<GameVersion>> = runCatching {
        val response: HttpResponse = httpClient.get(
            "https://launchermeta.mojang.com/mc/game/version_manifest_v2.json"
        )
        val body = response.bodyAsText()
        val manifest = json.decodeFromString<VersionManifest>(body)
        manifest.versions.map { entry ->
            GameVersion(
                id = entry.id,
                type = entry.type,
                url = entry.url,
                releaseTime = entry.releaseTime.ifEmpty { entry.time }
            )
        }
    }

    suspend fun prepareGame(
        versionId: String,
        gameDir: File,
        onProgress: (String) -> Unit = {}
    ): Result<File> = runCatching {
        val versionsDir = File(gameDir, "versions/$versionId")
        versionsDir.mkdirs()

        onProgress("正在获取版本列表...")
        val versions = fetchVersions().getOrThrow()
        val target = versions.find { it.id == versionId }
            ?: throw IllegalStateException("版本 $versionId 不存在")

        onProgress("正在获取版本元数据...")
        val metaResponse: HttpResponse = httpClient.get(target.url)
        val metaBody = metaResponse.bodyAsText()
        val detail = json.decodeFromString<VersionDetail>(metaBody)

        val clientUrl = detail.downloads.client.url
        if (clientUrl.isEmpty()) throw IllegalStateException("无法获取 $versionId 下载地址")

        val jarFile = File(versionsDir, "$versionId.jar")
        onProgress("正在下载 $versionId.jar...")
        downloadManager.downloadFile(clientUrl, jarFile.absolutePath) { pct ->
            onProgress("下载 ${(pct * 100).toInt()}%")
        }.getOrThrow()

        gameDir
    }
}
