package com.kmmcl.core.download

import android.util.Log
import com.kmmcl.core.jre.DownloadProgress
import com.kmmcl.core.jre.Downloader
import com.kmmcl.core.platform.Architecture
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.utils.io.readAvailable
import java.io.File

/**
 * Android [Downloader] — Ktor HttpClient HTTP download.
 *
 * For large files (> 4 MB) delegates to [ChunkedDownloader] for
 * multi-threaded Range-request parallelism (PCL-style).
 */
class AndroidDownloader(
    private val httpClient: HttpClient,
    private val chunkedDownloader: ChunkedDownloader = ChunkedDownloader(httpClient),
) : Downloader {

    companion object {
        private const val TAG = "AndroidDownloader"
    }

    override suspend fun downloadToFile(
        url: String,
        destDir: String,
        arch: Architecture,
        onProgress: (DownloadProgress) -> Unit,
    ): Result<String> = runCatching {
        val fileName = "jre-${arch.name.lowercase()}.tar.xz"
        val destPath = File(destDir, fileName).also { it.parentFile?.mkdirs() }.absolutePath

        val result = chunkedDownloader.download(url, destPath) { chunkProgress ->
            onProgress(DownloadProgress(chunkProgress.fraction, chunkProgress.bytesDownloaded, chunkProgress.totalBytes))
        }
        result.getOrThrow().absolutePath
    }
}
