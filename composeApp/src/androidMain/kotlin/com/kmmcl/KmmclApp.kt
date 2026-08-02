package com.kmmcl

import android.app.Application
import android.util.Log
import com.kmmcl.core.di.initKoin
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class KmmclApp : Application() {

    companion object {
        private const val TAG = "KmmclApp"
        lateinit var crashLogFile: File
    }

    override fun onCreate() {
        super.onCreate()

        // 配置崩溃日志文件
        crashLogFile = File(getExternalFilesDir(null), "crash.log")
        Log.i(TAG, "Crash log path: ${crashLogFile.absolutePath}")

        // 全局未捕获异常处理器
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val sw = StringWriter()
            throwable.printStackTrace(PrintWriter(sw))
            val crashMsg = """
                |=== Kmmcl Crash Report ===
                |Time: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())}
                |Thread: ${thread.name}
                |Exception: ${throwable.javaClass.name}: ${throwable.message}
                |Stacktrace:
                |${sw.toString()}
                |Caused by:
            """.trimMargin()

            val fullMsg = buildString {
                append(crashMsg)
                var cause = throwable.cause
                while (cause != null) {
                    val csw = StringWriter()
                    cause.printStackTrace(PrintWriter(csw))
                    append("\n--- Caused by: ${cause.javaClass.name}: ${cause.message} ---\n")
                    append(csw.toString())
                    cause = cause.cause
                }
            }

            try {
                crashLogFile.writeText(fullMsg)
                Log.e(TAG, "Crash logged to ${crashLogFile.absolutePath}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to write crash log", e)
            }

            defaultHandler?.uncaughtException(thread, throwable)
        }

        // 包裹主初始化
        try {
            Log.i(TAG, "Starting Koin initialization...")
            initKoin {
                this@KmmclApp
            }
            Log.i(TAG, "Koin initialization complete")
        } catch (e: Exception) {
            val sw = StringWriter()
            e.printStackTrace(PrintWriter(sw))
            val msg = """
                |=== Kmmcl Init Crash ===
                |Time: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())}
                |${sw.toString()}
            """.trimMargin()
            try {
                crashLogFile.writeText(msg)
            } catch (_: Exception) {}
            Log.e(TAG, "Init failed", e)
            throw e
        }
    }
}
