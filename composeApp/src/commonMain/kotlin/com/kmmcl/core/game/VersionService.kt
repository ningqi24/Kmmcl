
package com.kmmcl.core.game

import com.kmmcl.core.download.DownloadProvider
import com.kmmcl.core.download.remapUrl
import com.kmmcl.data.model.GameVersion
import com.kmmcl.data.model.VersionDetail
import com.kmmcl.data.model.VersionManifest
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json

class VersionService(
    private val httpClient: HttpClient,
    private val provider: DownloadProvider = DownloadProvider.BMCLAPI,
) {
    private val json = Json { ignoreUnknownKeys = true }

    // Cache the manifest so findVersionUrl doesn't refetch every time
    @Volatile
    private var cachedManifest: VersionManifest? = null

    suspend fun fetchVersions(): Result<List<GameVersion>> = runCatching {
        val url = provider.versionManifestUrl
        val resp: HttpResponse = httpClient.get(url)
        val manifest = json.decodeFromString<VersionManifest>(resp.bodyAsText())
        cachedManifest = manifest
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
        val url = remapUrl(versionUrl, provider)
        val resp: HttpResponse = httpClient.get(url)
        json.decodeFromString<VersionDetail>(resp.bodyAsText())
    }

    /** Look up a version's detail URL by its id. Used by ManifestResolver for inheritance chains. */
    suspend fun findVersionUrl(versionId: String): Result<String> {
        // Use cache if available — fetchVersions() already populates it
        var manifest = cachedManifest
        if (manifest == null) {
            fetchVersions().getOrThrow()
            manifest = cachedManifest
        }
        if (manifest != null) {
            val entry = manifest.versions.find { it.id == versionId }
            if (entry != null) return Result.success(entry.url)
        }
        return Result.failure(
            NoSuchElementException("Version '$versionId' not found in version manifest. The version list may need refreshing.")
        )
    }
}
