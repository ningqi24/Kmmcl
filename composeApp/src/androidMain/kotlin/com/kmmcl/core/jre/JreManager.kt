package com.kmmcl.core.jre

import android.util.Log
import com.kmmcl.core.download.AndroidDownloader
import com.kmmcl.core.download.RetryPolicy
import com.kmmcl.core.platform.AndroidArchitectureDetector
import com.kmmcl.core.platform.AndroidChecksumVerifier
import io.ktor.client.HttpClient
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.tukaani.xz.XZInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class JreManager(
    private val httpClient: HttpClient,
) {
    companion object {
        private const val TAG = "JreManager"
        const val JRE_DIR = "jre"
    }

    private val jreDownloader = JreDownloader(
        archDetector = AndroidArchitectureDetector,
        jreSource = AndroidJreSource(),
        downloader = AndroidDownloader(httpClient),
        checksumVerifier = AndroidChecksumVerifier,
        retryPolicy = RetryPolicy(),
    )

    val javaBin: File get() = File(jreInstallDir, "bin/java")
    private val jreInstallDir: File get() = File(gameDir, JRE_DIR)
    var gameDir: File = File(".")

    val isReady: Boolean get() = javaBin.exists() && javaBin.canExecute()

    /** @deprecated Use [ensureJre] instead. */
    @Deprecated("Use ensureJre", ReplaceWith("ensureJre(onStage = onStage)"))
    suspend fun downloadJre(onStage: (String) -> Unit = {}): Result<File> =
        ensureJre(onStage = onStage)

    suspend fun ensureJre(
        onProgress: (Float) -> Unit = {},
        onStage: (String) -> Unit = {},
    ): Result<File> = runCatching {
        if (isReady) {
            Log.i(TAG, "JRE already ready at ${javaBin.absolutePath}")
            return Result.success(javaBin)
        }

        val archivePath = jreDownloader.download(
            destDir = gameDir.absolutePath,
            onProgress = { p -> onProgress(p.fraction) },
            onStage = onStage,
        ).getOrThrow()

        onStage("解压 JRE ...")
        extractTarXz(archivePath, jreInstallDir)

        File(archivePath).delete()
        Log.i(TAG, "Cleaned up archive: $archivePath")

        if (!javaBin.setExecutable(true)) {
            Log.w(TAG, "Failed to set executable bit on ${javaBin.absolutePath}")
        }

        onStage("JRE 就绪")
        javaBin
    }

    private fun extractTarXz(archivePath: String, destDir: File) {
        if (!destDir.exists()) destDir.mkdirs()

        XZInputStream(BufferedInputStream(FileInputStream(archivePath))).use { xzIn ->
            TarArchiveInputStream(xzIn).use { tarIn ->
                var entry: TarArchiveEntry? = tarIn.nextEntry
                while (entry != null) {
                    val entryFile = File(destDir, entry!!.name)
                    if (entry!!.isDirectory) {
                        entryFile.mkdirs()
                    } else {
                        entryFile.parentFile?.mkdirs()
                        FileOutputStream(entryFile).use { out -> tarIn.copyTo(out) }
                        val mode = entry!!.mode
                        if (mode and 0b001_000_000 != 0) entryFile.setExecutable(true, false)
                        if (mode and 0b000_001_000 != 0) entryFile.setExecutable(true, false)
                        if (mode and 0b000_000_001 != 0) entryFile.setExecutable(true, false)
                    }
                    entry = tarIn.nextEntry
                }
            }
        }
    }
}
