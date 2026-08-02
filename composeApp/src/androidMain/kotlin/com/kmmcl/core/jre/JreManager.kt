
package com.kmmcl.core.jre

import android.os.Build
import com.kmmcl.core.download.DownloadManager
import com.kmmcl.data.model.JreInfo
import org.tukaani.xz.XZInputStream
import java.io.*
import java.util.zip.GZIPInputStream

object DeviceArch {
    val ABI: String get() = Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"
    val nativeKey: String get() = when {
        ABI.contains("arm64") -> "linux-arm64"
        ABI.contains("armeabi") -> "linux-arm32"
        ABI.contains("x86_64") -> "linux-x86_64"
        else -> "linux"
    }
    val jreUrl: String get() = when {
        ABI.contains("arm64") -> JreInfo.JRE_21.arm64Url
        ABI.contains("armeabi") -> JreInfo.JRE_21.armeabiUrl
        ABI.contains("x86_64") -> JreInfo.JRE_21.x8664Url
        else -> JreInfo.JRE_21.arm64Url
    }
}

class JreManager(
    private val downloadManager: DownloadManager,
    private val gameDir: File
) {
    val jreDir: File get() = File(gameDir, "jre")
    val javaBin: File get() = File(jreDir, "bin/java")
    val isReady: Boolean get() = javaBin.exists() && javaBin.canExecute()

    suspend fun downloadJre(
        onProgress: (String) -> Unit = {}
    ): Result<File> = runCatching {
        if (isReady) return Result.success(jreDir)

        val url = DeviceArch.jreUrl
        val tmpFile = File(gameDir, "jre_download.tmp")
        tmpFile.parentFile?.mkdirs()

        onProgress("正在下载 JRE (${DeviceArch.ABI})...")
        downloadManager.downloadFile(url, tmpFile.absolutePath) { pct ->
            onProgress("下载 JRE ${(pct * 100).toInt()}%")
        }.getOrThrow()

        onProgress("正在解压 JRE...")
        jreDir.mkdirs()
        extractJre(tmpFile, jreDir)
        tmpFile.delete()

        if (!javaBin.setExecutable(true)) {
            throw IllegalStateException("无法设置 java 执行权限")
        }

        onProgress("JRE 准备完成")
        jreDir
    }

    private fun extractJre(archive: File, dest: File) {
        val input = when {
            archive.name.endsWith(".tar.xz") -> XZInputStream(archive.inputStream().buffered())
            archive.name.endsWith(".tar.gz") || archive.name.endsWith(".tgz") ->
                GZIPInputStream(archive.inputStream().buffered())
            else -> throw IllegalArgumentException("不支持的 JRE 压缩格式: ${archive.name}")
        }

        input.use { `in` ->
            var entry = readTarHeader(`in`)
            while (entry != null) {
                val target = File(dest, entry.name.trimStart('/'))
                when (entry.type) {
                    TarEntryType.DIRECTORY -> target.mkdirs()
                    TarEntryType.FILE -> {
                        target.parentFile?.mkdirs()
                        target.outputStream().buffered().use { out ->
                            `in`.copyTo(out, entry.size)
                        }
                    }
                    TarEntryType.SYMLINK -> {
                        target.parentFile?.mkdirs()
                        // Create symlink if supported, else copy target
                        runCatching {
                            java.nio.file.Files.createSymbolicLink(
                                target.toPath(),
                                java.nio.file.Paths.get(entry.linkName)
                            )
                        }.onFailure {
                            // Fallback: if link target exists, copy as regular file
                        }
                    }
                    else -> {}
                }
                entry = readTarHeader(`in`)
            }
        }
    }

    private data class TarEntry(
        val name: String, val size: Long, val type: TarEntryType, val linkName: String = ""
    )

    private enum class TarEntryType { FILE, DIRECTORY, SYMLINK, OTHER }

    private fun readTarHeader(input: InputStream): TarEntry? {
        val buf = ByteArray(512)
        var read = 0
        while (read < 512) {
            val n = input.read(buf, read, 512 - read)
            if (n < 0) break
            read += n
        }
        if (read < 512) return null

        // Check if all zeroes (end of archive)
        if (buf.all { it == 0.toByte() }) return null

        val name = buf.copyOfRange(0, 100).decodeToString().trimEnd('\u0000')
        val sizeStr = buf.copyOfRange(124, 136).decodeToString().trimEnd('\u0000')
        val size = sizeStr.toLongOrNull(8) ?: 0L
        val typeFlag = buf[156].toInt().toChar()
        val linkName = buf.copyOfRange(157, 257).decodeToString().trimEnd('\u0000')

        val type = when (typeFlag) {
            '0', '\u0000' -> TarEntryType.FILE
            '5' -> TarEntryType.DIRECTORY
            '2' -> TarEntryType.SYMLINK
            else -> TarEntryType.OTHER
        }

        // Skip data blocks to next header
        val skip = if (size > 0) ((size + 511) / 512) * 512 else 0L
        if (skip > 0) {
            val skipBuf = ByteArray(8192)
            var remaining = skip
            while (remaining > 0) {
                val n = input.read(skipBuf, 0, minOf(skipBuf.size.toLong(), remaining).toInt())
                if (n < 0) break
                remaining -= n
            }
        }

        return TarEntry(name, size, type, linkName)
    }
}
