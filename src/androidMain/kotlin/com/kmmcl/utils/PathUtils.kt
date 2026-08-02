package com.kmmcl.utils

import android.content.Context
import java.io.File

object PathUtils {

    fun getGameDirectory(context: Context): File {
        val dir = File(context.getExternalFilesDir(null), "minecraft")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getVersionsDir(context: Context): File {
        val dir = File(getGameDirectory(context), "versions")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getAssetsDir(context: Context): File {
        val dir = File(getGameDirectory(context), "assets")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getLibrariesDir(context: Context): File {
        val dir = File(getGameDirectory(context), "libraries")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getDownloadsDir(context: Context): File {
        val dir = File(context.cacheDir, "downloads")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }
}
