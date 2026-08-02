package com.kmmcl.core.download

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.utils.io.*
import io.ktor.client.plugins.*
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

class DownloadManager(private val httpClient: HttpClient) {

    /**
     * 流式下载文件，通过 contentLength 和已写入字节数计算进度。
     */
    suspend fun downloadFile(
        url: String,
        destPath: String,
        onProgress: (Float) -> Unit = {}
    ): Result<File> = runCatching {
        val destFile = File(destPath)
        destFile.parentFile?.mkdirs()

        val response: HttpResponse = httpClient.get(url) {
            onDownload { bytesSentTotal, contentLength ->
                if (contentLength > 0) {
                    onProgress(bytesSentTotal.toFloat() / contentLength)
                }
            }
        }

        response.bodyAsChannel().use { channel ->
            FileOutputStream(destFile).use { output ->
                val buffer = ByteArray(8192)
                var total = 0L
                val length = response.contentLength() ?: -1
                while (true) {
                    val read = channel.readAvailable(buffer)
                    if (read <= 0) break
                    output.write(buffer, 0, read)
                    total += read
                    if (length > 0) {
                        onProgress(total.toFloat() / length)
                    }
                }
            }
        }
        destFile
    }

    /**
     * 使用 java.util.zip.ZipInputStream 解压 ZIP 到 destDir。
     */
    suspend fun extractZip(zipFile: File, destDir: File): Result<File> = runCatching {
        if (!destDir.exists()) destDir.mkdirs()

        ZipInputStream(zipFile.inputStream()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val entryFile = File(destDir, entry.name)
                if (entry.isDirectory) {
                    entryFile.mkdirs()
                } else {
                    entryFile.parentFile?.mkdirs()
                    entryFile.outputStream().use { fos ->
                        zis.copyTo(fos)
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        destDir
    }
}
