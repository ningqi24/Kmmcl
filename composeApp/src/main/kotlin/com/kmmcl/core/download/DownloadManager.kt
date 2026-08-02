package com.kmmcl.core.download

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

data class DownloadProgress(
    val url: String = "",
    val totalBytes: Long = 0,
    val downloadedBytes: Long = 0,
    val progress: Float = 0f,
    val isActive: Boolean = false,
    val isCompleted: Boolean = false,
    val error: String? = null
)

data class DownloadTask(
    val id: String,
    val name: String,
    val url: String,
    val destination: File,
    val totalSize: Long = 0
)

class DownloadManager {

    private val _downloads = MutableStateFlow<Map<String, DownloadProgress>>(emptyMap())
    val downloads: StateFlow<Map<String, DownloadProgress>> = _downloads.asStateFlow()

    fun getProgress(taskId: String): DownloadProgress {
        return _downloads.value[taskId] ?: DownloadProgress()
    }

    suspend fun download(task: DownloadTask, onProgress: ((Float) -> Unit)? = null): Result<File> {
        val progress = DownloadProgress(
            url = task.url,
            isActive = true
        )
        _downloads.value = _downloads.value + (task.id to progress)

        return try {
            // KDownloadFiles integration
            // val downloader = KDownloadFiles.Builder()
            //     .url(task.url)
            //     .destination(task.destination)
            //     .onProgress { bytes, total ->
            //         val pct = bytes.toFloat() / total
            //         onProgress?.invoke(pct)
            //         val updated = DownloadProgress(
            //             url = task.url,
            //             totalBytes = total,
            //             downloadedBytes = bytes,
            //             progress = pct,
            //             isActive = true
            //         )
            //         _downloads.value = _downloads.value + (task.id to updated)
            //     }
            //     .enableResume(true) // 断点续传
            //     .build()
            // val result = downloader.start()

            // Placeholder: simulate download completion
            val dir = task.destination.parentFile
            if (dir != null && !dir.exists()) dir.mkdirs()
            task.destination.createNewFile()

            val completed = DownloadProgress(
                url = task.url,
                totalBytes = task.totalSize,
                downloadedBytes = task.totalSize,
                progress = 1f,
                isActive = false,
                isCompleted = true
            )
            _downloads.value = _downloads.value + (task.id to completed)
            onProgress?.invoke(1f)

            Result.success(task.destination)
        } catch (e: Exception) {
            val failed = progress.copy(
                isActive = false,
                error = e.message
            )
            _downloads.value = _downloads.value + (task.id to failed)
            Result.failure(e)
        }
    }

    suspend fun extractZip(zipFile: File, destination: File): Result<File> {
        return try {
            // KZip integration
            // KZip.unzip(zipFile, destination)
            if (!destination.exists()) destination.mkdirs()
            Result.success(destination)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
