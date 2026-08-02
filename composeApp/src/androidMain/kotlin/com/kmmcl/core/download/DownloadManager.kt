package com.kmmcl.core.download

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.util.cio.*
import io.ktor.utils.io.*
import io.ktor.http.*
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

class DownloadManager(private val httpClient: HttpClient) {

    suspend fun downloadFile(
        url: String,
        destPath: String,
        onProgress: (Float) -> Unit = {}
    ): Result<File> = runCatching {
        val destFile = File(destPath)
        destFile.parentFile?.mkdirs()

        val response: HttpResponse = httpClient.get(url)
        val contentLength: Long? = response.headers[HttpHeaders.ContentLength]?.toLongOrNull()

        response.bodyAsChannel().toInputStream().use { input ->
            FileOutputStream(destFile).use { output ->
                val buffer = ByteArray(8192)
                var total = 0L
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    total += bytesRead
                    if (contentLength != null && contentLength > 0) {
                        onProgress(total.toFloat() / contentLength)
                    }
                }
            }
        }
        destFile
    }

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
