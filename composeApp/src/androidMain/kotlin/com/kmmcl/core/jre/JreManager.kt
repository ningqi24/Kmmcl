package com.kmmcl.core.jre

import android.util.Log
import com.kmmcl.core.download.AndroidDownloader
import com.kmmcl.core.download.RetryPolicy
import com.kmmcl.core.platform.AndroidArchitectureDetector
import com.kmmcl.core.platform.AndroidChecksumVerifier
import io.ktor.client.HttpClient
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * Android JRE manager — downloads + extracts Java runtime for Minecraft.
 *
 * v0.2: Refactored to delegate download orchestration to [JreDownloader]
 * (commonMain), keeping only Android-specific tar.xz extraction here.
 * This prepares for desktop where extraction logic will differ (zip / system tar).
 */
class JreManager(
    private val httpClient: HttpClient,
) {
    companion object {
        private const val TAG = "JreManager"

        /** Output directory name under gameDir for extracted JRE */
        const val JRE_DIR = "jre"
    }

    // ── Injected platform components ──────────────────────────────

    private val jreDownloader = JreDownloader(
        archDetector = AndroidArchitectureDetector,
        jreSource = AndroidJreSource(),
        downloader = AndroidDownloader(httpClient),
        checksumVerifier = AndroidChecksumVerifier,
        retryPolicy = RetryPolicy(),
    )

    /** Path to the java executable after extraction */
    val javaBin: File
        get() = File(jreInstallDir, "bin/java")

    private val jreInstallDir: File
        get() = File(gameDir, JRE_DIR)

    /** Current game directory (must be set before download). */
    var gameDir: File = File(".")

    /** Check whether JRE is already extracted and ready. */
    fun isReady(): Boolean = javaBin.exists() && javaBin.canExecute()

    /**
     * Download and extract the JRE if not already present.
     *
     * Steps:
     * 1. Call [JreDownloader.download] (commonMain: retry + mirror + checksum)
     * 2. Extract tar.xz archive (Android-specific)
     * 3. Clean up archive
     */
    suspend fun ensureJre(
        onProgress: (Float) -> Unit = {},
        onStage: (String) -> Unit = {},
    ): Result<File> = runCatching {
        if (isReady()) {
            Log.i(TAG, "JRE already ready at ${javaBin.absolutePath}")
            return Result.success(javaBin)
        }

        val destDir = gameDir.absolutePath

        // 1. Download via common orchestrator
        val archivePath = jreDownloader.download(
            destDir = destDir,
            onProgress = { p -> onProgress(p.fraction) },
            onStage = onStage,
        ).getOrThrow()

        // 2. Extract tar.xz (Android-specific — PojavLauncherTeam archives)
        onStage("解压 JRE ...")
        extractTarXz(archivePath, jreInstallDir)

        // 3. Clean up archive to save space
        File(archivePath).delete()
        Log.i(TAG, "Cleaned up archive: $archivePath")

        if (!javaBin.setExecutable(true)) {
            Log.w(TAG, "Failed to set executable bit on ${javaBin.absolutePath}")
        }

        onStage("JRE 就绪")
        javaBin
    }

    /**
     * Extract a .tar.xz archive to [destDir].
     *
     * This is Android-specific; PojavLauncherTeam publishes tar.xz files.
     * Desktop (Mojang JRE) uses .tar.gz or .zip — a separate implementation
     * will be wired via a platform-specific extractor interface when needed.
     */
    private fun extractTarXz(archivePath: String, destDir: File) {
        if (!destDir.exists()) destDir.mkdirs()

        // Decompress XZ → TAR stream
        val xzIn = org.tukaani.xz.XZInputStream(BufferedInputStream(FileInputStream(archivePath)))
        val tarIn = org.apache.commons.compress.archivers.tar.TarArchiveInputStream(xzIn)

        try {
            var entry = tarIn.nextEntry
            while (entry != null) {
                val entryFile = File(destDir, entry.name)

                if (entry.isDirectory) {
                    entryFile.mkdirs()
                } else {
                    entryFile.parentFile?.mkdirs()
                    FileOutputStream(entryFile).use { out ->
                        tarIn.copyTo(out)
                    }
                    // Restore executable bits from TAR entry mode
                    if (entry.mode and 0b001_000_000 != 0) entryFile.setExecutable(true, false)
                    if (entry.mode and 0b000_001_000 != 0) entryFile.setExecutable(true, false)
                    if (entry.mode and 0b000_000_001 != 0) entryFile.setExecutable(true, false)
                }
                entry = tarIn.nextEntry
            }
        } finally {
            tarIn.close()
        }
    }
}
