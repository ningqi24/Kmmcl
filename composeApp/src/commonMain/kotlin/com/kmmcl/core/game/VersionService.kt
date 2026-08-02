
package com.kmmcl.core.game

import com.kmmcl.data.model.GameVersion
import com.kmmcl.data.model.VersionDetail
import com.kmmcl.data.model.VersionManifest
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json

class VersionService(private val httpClient: HttpClient) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetchVersions(): Result<List<GameVersion>> = runCatching {
        val resp: HttpResponse = httpClient.get(
            "https://launchermeta.mojang.com/mc/game/version_manifest_v2.json"
        )
        val manifest = json.decodeFromString<VersionManifest>(resp.bodyAsText())
        manifest.versions.map { entry ->
            GameVersion(
                id = entry.id,
                type = entry.type,
                url = entry.url,
                releaseTime = entry.releaseTime.ifEmpty { entry.time }
            )
        }
    }

    suspend fun fetchVersionDetail(versionUrl: String): Result<VersionDetail> = runCatching {
        val resp: HttpResponse = httpClient.get(versionUrl)
        json.decodeFromString<VersionDetail>(resp.bodyAsText())
    }
}
