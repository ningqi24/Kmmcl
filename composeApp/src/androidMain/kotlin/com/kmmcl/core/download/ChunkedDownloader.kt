package com.kmmcl.core.download

import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.head
import io.ktor.client.request.headers
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.atomic.AtomicLong

/**
 * Multi-threaded chunked download engine.
 *
 * Reference: PCL 32–128 thread chunked download (NetRequestByClient + Range requests).
 *
 * For files smaller than minFileSizeForChunk, falls back to single-stream download.
 * For larger files, splits into chunkCount parallel Range requests and writes
 * via RandomAccessFile to avoid contention.
 */
class ChunkedDownloader(
    private val httpClient: HttpClient,
    private val chunkCount: Int = 32,
    private val minFileSizeForChunk: Long = 4 * 1024 * 1024, // 4 MB
) {
    companion object {
        private const val TAG = "ChunkedDownloader"
    }

    data class DownloadProgress(
        val fraction: Float,   // 0.0 – 1.0
        val bytesDownloaded: Long,
        val totalBytes: Long,
    )

    /**
     * Download a file, automatically choosing chunked vs single-stream.
     */
    suspend fun download(
        url: String,
        destPath: String,
        onProgress: (DownloadProgress) -> Unit = {},
    ): Result<File> = runCatching {
        val contentLength = queryContentLength(url)
        if (contentLength == null || contentLength < minFileSizeForChunk) {
            singleDownload(url, destPath, contentLength, onProgress)
        } else {
            chunkedDownload(url, destPath, contentLength, onProgress)
        }
    }

    // ── Single-stream fallback ────────────────────────────────────────────

    private suspend fun singleDownload(
        url: String,
        destPath: String,
        contentLength: Long?,
        onProgress: (DownloadProgress) -> Unit,
    ): File {
        val destFile = File(destPath)
        destFile.parentFile?.mkdirs()

        val response = httpClient.get(url)
        // Use Content-Length from the response itself (not the HEAD probe)
        val actualLength = response.headers[HttpHeaders.ContentLength]?.toLongOrNull()
            ?: contentLength
        val channel = response.bodyAsChannel()
        var total = 0L

        destFile.outputStream().use { out ->
            val buf = ByteArray(8192)
            while (true) {
                val read = channel.readAvailable(buf, 0, buf.size)
                if (read < 0) break
                out.write(buf, 0, read)
                total += read
                if (actualLength != null && actualLength > 0) {
                    onProgress(DownloadProgress(total.toFloat() / actualLength, total, actualLength))
                }
            }
        }
        return destFile
    }

    // ── Chunked parallel download ─────────────────────────────────────────

    private suspend fun chunkedDownload(
        url: String,
        destPath: String,
        totalSize: Long,
        onProgress: (DownloadProgress) -> Unit,
    ): File {
        val destFile = File(destPath)
        destFile.parentFile?.mkdirs()

        val chunkSize = (totalSize + chunkCount - 1) / chunkCount
        val semaphore = Semaphore(chunkCount)  // allow all chunks in parallel
        val completed = AtomicLong(0)

        RandomAccessFile(destFile, "rw").use { raf ->
            raf.setLength(totalSize)

            coroutineScope {
                (0 until chunkCount).map { idx ->
                    async {
                        val start = idx * chunkSize
                        val end = minOf(start + chunkSize - 1, totalSize - 1)
                        if (start >= totalSize) return@async

                        semaphore.withPermit {
                            downloadChunk(url, start, end, raf, completed, totalSize, onProgress)
                        }
                    }
                }.awaitAll()
            }
        }
        return destFile
    }

    private suspend fun downloadChunk(
        url: String,
        start: Long,
        end: Long,
        raf: RandomAccessFile,
        completed: AtomicLong,
        totalSize: Long,
        onProgress: (DownloadProgress) -> Unit,
    ) {
        httpClient.get(url) {
            headers { append(HttpHeaders.Range, "bytes=$start-$end") }
        }.bodyAsChannel().use { channel ->
            val buf = ByteArray(8192)
            var pos = start
            while (true) {
                val read = channel.readAvailable(buf, 0, buf.size)
                if (read < 0) break
                synchronized(raf) {
                    raf.seek(pos)
                    raf.write(buf, 0, read)
                }
                pos += read
                val done = completed.addAndGet(read.toLong())
                onProgress(DownloadProgress(done.toFloat() / totalSize, done, totalSize))
            }
        }
    }

    // ── Content-Length query ──────────────────────────────────────────────

    /**
     * Query Content-Length with HEAD first (avoids downloading the body twice),
     * falling back to GET only when HEAD fails or doesn't return the header.
     */
    private suspend fun queryContentLength(url: String): Long? {
        // Try HEAD first — zero body overhead
        try {
            httpClient.head(url).headers[HttpHeaders.ContentLength]?.toLongOrNull()?.let { return it }
        } catch (e: Exception) {
            Log.w(TAG, "HEAD failed for $url: ${e.message}, falling back to GET")
        }
        // Fallback: GET, but signal that we only need the header
        return try {
            httpClient.get(url).headers[HttpHeaders.ContentLength]?.toLongOrNull()
        } catch (e: Exception) {
            Log.w(TAG, "Content-Length query failed for $url: ${e.message}")
            null
        }
    }
}
