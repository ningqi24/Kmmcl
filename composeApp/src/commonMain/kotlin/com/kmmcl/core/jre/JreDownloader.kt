package com.kmmcl.core.jre

import com.kmmcl.core.download.DownloadSource
import com.kmmcl.core.download.RetryPolicy
import com.kmmcl.core.platform.Architecture
import com.kmmcl.core.platform.ArchitectureDetector
import com.kmmcl.core.platform.ChecksumVerifier
import kotlinx.coroutines.delay

/**
 * Download progress callback.
 *
 * @param fraction  0.0 – 1.0 (may be 0 when total size is unknown)
 * @param bytes     bytes downloaded so far
 * @param total     total bytes, or -1 if unknown
 */
data class DownloadProgress(
    val fraction: Float,
    val bytes: Long,
    val total: Long,
)

/**
 * Cross-platform JRE download orchestrator.
 *
 * Responsibilities (all in commonMain):
 * 1. Ask [archDetector] which CPU architecture we're on
 * 2. Ask [jreSource] for matching [JreArchive] candidates
 * 3. Build mirror chains for each candidate
 * 4. Execute retry-with-fallback download via platform [Downloader]
 * 5. Verify SHA-256 via [checksumVerifier] (if present)
 *
 * Platform-specific concerns (Android / Desktop) are injected as
 * [ArchitectureDetector], [JreSource], [Downloader], [ChecksumVerifier],
 * and the extraction step (handled by platform code after this returns).
 *
 * Reference: HMCL BMCLAPIDownloadProvider URL rewriting +
 * PCL2 DlSourceLoader multi-source fallback.
 */
class JreDownloader(
    private val archDetector: ArchitectureDetector,
    private val jreSource: JreSource,
    private val downloader: Downloader,
    private val checksumVerifier: ChecksumVerifier,
    private val retryPolicy: RetryPolicy = RetryPolicy(),
) {
    /**
     * Orchestrate the full JRE download:
     * detect arch → pick archive → try mirrors → download → verify → return path.
     *
     * @param destDir  directory to place the downloaded archive
     * @param onProgress  called periodically with download progress
     * @return path to the downloaded (and verified) archive
     */
    suspend fun download(
        destDir: String,
        onProgress: (DownloadProgress) -> Unit = {},
        onStage: (String) -> Unit = {},
    ): Result<String> = runCatching {
        // 1. Detect architecture
        val arch = archDetector.detect()
        onStage("检测到架构: ${arch.name}")

        // 2. Resolve JRE archives from platform source
        val archives = jreSource.resolveArchives()
        val candidate = archives.firstOrNull { it.arch == arch }
            ?: throw IllegalStateException(
                "没有找到 $arch 架构的 JRE（共 ${archives.size} 个可用项，架构: " +
                    archives.map { it.arch.name }.joinToString())
            )

        onStage("Java ${jreSource.javaMajorVersion} | 目标架构: ${arch.name}")

        // 3. Build full URL chain: primary → mirrors
        val source = DownloadSource(
            url = candidate.primaryUrl,
            sha256 = candidate.sha256,
            fallbackUrls = candidate.mirrors,
        )

        val destPath = downloadWithFallback(source, destDir, arch, onProgress, onStage)

        // 4. Verify checksum if available
        val sha = candidate.sha256
        if (sha != null) {
            onStage("校验 SHA-256 ...")
            val actual = checksumVerifier.sha256Hex(destPath)
            if (!actual.equals(sha, ignoreCase = true)) {
                throw SecurityException(
                    "SHA-256 不匹配！\n期望: $sha\n实际: $actual"
                )
            }
            onStage("校验通过 ✓")
        }

        destPath
    }

    // ── Private: retry + mirror fallback ──────────────────────────────

    private suspend fun downloadWithFallback(
        source: DownloadSource,
        destDir: String,
        arch: Architecture,
        onProgress: (DownloadProgress) -> Unit,
        onStage: (String) -> Unit,
    ): String {
        val urls = listOf(source.url) + source.fallbackUrls

        for ((mirrorIdx, url) in urls.withIndex()) {
            val label = if (mirrorIdx == 0) "主源" else "镜像 #$mirrorIdx"
            onStage("尝试 $label: $url")

            for (attempt in 0 until retryPolicy.maxAttemptsPerSource) {
                val result = tryDownload(url, destDir, arch, onProgress)
                if (result.isSuccess) {
                    return result.getOrThrow()
                }
                if (attempt < retryPolicy.maxAttemptsPerSource - 1) {
                    val delayMs = retryPolicy.backoffDelays.getOrElse(attempt) { 4_000L }
                    onStage("重试 #${attempt + 1}，等待 ${delayMs / 1000}s ...")
                    delay(delayMs)
                }
            }
        }

        throw IllegalStateException(
            "所有源均下载失败（${urls.size} 个源 × ${retryPolicy.maxAttemptsPerSource} 次重试）"
        )
    }

    private suspend fun tryDownload(
        url: String,
        destDir: String,
        arch: Architecture,
        onProgress: (DownloadProgress) -> Unit,
    ): Result<String> {
        return downloader.downloadToFile(url, destDir, arch, onProgress)
    }
}

/**
 * Platform-specific file download primitive.
 *
 * Each platform injects its own implementation (Ktor HttpClient on Android/JVM,
 * platform HTTP stack on native). The common [JreDownloader] only calls this;
 * it doesn't know about HTTP libraries.
 */
interface Downloader {
    /**
     * Download [url] to a file under [destDir] and return the absolute path.
     *
     * @param url       remote URL
     * @param destDir   target directory
     * @param arch      target architecture (for naming)
     * @param onProgress  progress callback
     */
    suspend fun downloadToFile(
        url: String,
        destDir: String,
        arch: Architecture,
        onProgress: (DownloadProgress) -> Unit,
    ): Result<String>
}
