package com.kmmcl.core.jre

import com.kmmcl.core.download.DownloadSource
import com.kmmcl.core.download.RetryPolicy
import com.kmmcl.core.platform.Architecture
import com.kmmcl.core.platform.ArchitectureDetector
import com.kmmcl.core.platform.ChecksumVerifier
import kotlinx.coroutines.delay

/**
 * Download progress update.
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
 * 1. Detect CPU architecture via [archDetector]
 * 2. Resolve JRE archives via platform [jreSource]
 * 3. Build mirror chains and retry with fallback via [Downloader]
 * 4. SHA-256 verification via [checksumVerifier]
 */
class JreDownloader(
    private val archDetector: ArchitectureDetector,
    private val jreSource: JreSource,
    private val downloader: Downloader,
    private val checksumVerifier: ChecksumVerifier,
    private val retryPolicy: RetryPolicy = RetryPolicy(),
) {

    suspend fun download(
        destDir: String,
        onProgress: (DownloadProgress) -> Unit = {},
        onStage: (String) -> Unit = {},
    ): Result<String> = runCatching {
        val arch = archDetector.detect()
        onStage("检测到架构: ${arch.name}")

        val archives = jreSource.resolveArchives()
        val candidate = archives.firstOrNull { it.arch == arch }
            ?: throw IllegalStateException(
                "没有找到 $arch 架构的 JRE（共 ${archives.size} 个可用项）"
            )

        onStage("Java ${jreSource.javaMajorVersion} | ${arch.name}")

        val source = DownloadSource(
            url = candidate.primaryUrl,
            sha256 = candidate.sha256,
            fallbackUrls = candidate.mirrors,
        )

        val destPath = downloadWithFallback(source, destDir, arch, onProgress, onStage)

        candidate.sha256?.let { sha ->
            onStage("校验 SHA-256 ...")
            val actual = checksumVerifier.sha256Hex(destPath)
            if (!actual.equals(sha, ignoreCase = true)) {
                throw SecurityException("SHA-256 不匹配！期望: $sha 实际: $actual")
            }
            onStage("校验通过")
        }

        destPath
    }

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
                val result = downloader.downloadToFile(url, destDir, arch, onProgress)
                if (result.isSuccess) return result.getOrThrow()
                if (attempt < retryPolicy.maxAttemptsPerSource - 1) {
                    val delayMs = retryPolicy.backoffDelays.getOrElse(attempt) { 4_000L }
                    onStage("重试 #${attempt + 1}，等待 ${delayMs / 1000}s ...")
                    delay(delayMs)
                }
            }
        }
        throw IllegalStateException(
            "所有源均下载失败（${urls.size} 个源 x ${retryPolicy.maxAttemptsPerSource} 次重试）"
        )
    }
}

/**
 * Platform-specific file download primitive.
 */
interface Downloader {
    suspend fun downloadToFile(
        url: String,
        destDir: String,
        arch: Architecture,
        onProgress: (DownloadProgress) -> Unit,
    ): Result<String>
}
