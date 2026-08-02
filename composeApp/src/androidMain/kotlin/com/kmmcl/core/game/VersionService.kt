
package com.kmmcl.core.game

import com.kmmcl.data.model.VersionDetail
import com.kmmcl.data.model.VersionManifest
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json

class VersionService(
    private val client: HttpClient
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetchManifest(): Result<VersionManifest> = runCatching {
        val body = client.get(MojangMirror.mirror("https://launchermeta.mojang.com/mc/game/version_manifest.json"))
            .bodyAsText()
        json.decodeFromString<VersionManifest>(body)
    }

    suspend fun fetchVersionDetail(url: String): Result<VersionDetail> = runCatching {
        val body = client.get(url).bodyAsText()
        json.decodeFromString<VersionDetail>(body)
    }
}
