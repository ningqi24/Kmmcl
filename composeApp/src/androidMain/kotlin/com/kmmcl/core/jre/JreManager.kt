package com.kmmcl.core.jre

import com.kmmcl.core.download.DownloadManager
import org.tukaani.xz.XZInputStream
import java.io.*
import java.util.zip.GZIPInputStream

class JreManager(
    private val downloadManager: DownloadManager,
    private val gameDir: File
) {
    val jreDir: File get() = File(gameDir, "jre")

    val javaBin: File
        get() {
            val direct = File(jreDir, "bin/java")
            if (direct.exists() && direct.canExecute()) return direct
            jreDir.listFiles()?.forEach { child ->
                if (child.isDirectory) {
                    val nested = File(child, "bin/java")
                    if (nested.exists() && nested.canExecute()) return nested
                }
            }
            return direct
        }

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

        // Flatten: if jreDir contains a single subdirectory, move contents up
        val children = jreDir.listFiles() ?: emptyArray()
        if (children.size == 1 && children[0].isDirectory) {
            val topDir = children[0]
            topDir.listFiles()?.forEach { f ->
                f.renameTo(File(jreDir, f.name))
            }
            topDir.delete()
        }

        val java = javaBin
        if (!java.exists()) throw IllegalStateException("JRE 解压后未找到 bin/java，路径: ${java.absolutePath}")
        if (!java.setExecutable(true)) {
            throw IllegalStateException("无法设置 java 执行权限")
        }

        onProgress("JRE 准备完成")
        jreDir
    }

    // ---- TAR extraction (self-implemented, no commons-compress dep) ----

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
                            copyNBytes(`in`, out, entry.size)
                        }
                        skipToNextBlock(`in`, entry.size)
                    }
                    TarEntryType.SYMLINK -> {
                        target.parentFile?.mkdirs()
                        runCatching {
                            java.nio.file.Files.createSymbolicLink(
                                target.toPath(),
                                java.nio.file.Paths.get(entry.linkName)
                            )
                        }
                    }
                    else -> {}
                }
                entry = readTarHeader(`in`)
            }
        }
    }

    private fun copyNBytes(input: InputStream, out: OutputStream, size: Long) {
        val buf = ByteArray(8192)
        var remaining = size
        while (remaining > 0) {
            val n = input.read(buf, 0, minOf(buf.size.toLong(), remaining).toInt())
            if (n < 0) break
            out.write(buf, 0, n)
            remaining -= n
        }
    }

    private fun skipToNextBlock(input: InputStream, size: Long) {
        val padding = ((size + 511) / 512) * 512 - size
        if (padding > 0) {
            val padBuf = ByteArray(padding.toInt())
            var remaining = padding
            while (remaining > 0) {
                val n = input.read(padBuf, 0, minOf(padBuf.size.toLong(), remaining).toInt())
                if (n < 0) break
                remaining -= n.toLong()
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

        return TarEntry(name, size, type, linkName)
    }
}
