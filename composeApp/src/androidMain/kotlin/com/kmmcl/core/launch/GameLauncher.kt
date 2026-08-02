
package com.kmmcl.core.launch

import com.kmmcl.core.game.MojangMirror
import com.kmmcl.core.game.VersionService
import com.kmmcl.core.jre.DeviceArch
import com.kmmcl.core.jre.JreManager
import com.kmmcl.data.model.VersionDetail
import java.io.File
import java.util.zip.ZipInputStream

class GameLauncher(
    private val jreManager: JreManager,
    private val versionService: VersionService,
    private val gameDir: File
) {
    suspend fun launch(
        versionId: String,
        versionUrl: String,
        username: String,
        maxMemory: String = "2048M",
        onProgress: (String) -> Unit = {}
    ): Result<Process> = runCatching {
        val detail = versionService.fetchVersionDetail(versionUrl).getOrThrow()
        val versionsDir = File(gameDir, "versions/$versionId")
        val nativesDir = File(versionsDir, "natives")
        val libsDir = File(gameDir, "libraries")
        val assetsDir = File(gameDir, "assets")

        nativesDir.mkdirs()

        // Download and extract native libraries
        onProgress("正在准备运行库...")
        extractNatives(detail, libsDir, nativesDir)

        // Build classpath
        val classpath = buildClasspath(versionsDir, libsDir, detail)

        // Build JVM arguments
        val jvmArgs = buildJvmArgs(
            versionId = versionId,
            nativesDir = nativesDir,
            classpath = classpath,
            maxMemory = maxMemory,
            detail = detail,
            gameDir = gameDir,
            assetsDir = assetsDir
        )

        // Build game arguments
        val gameArgs = buildGameArgs(
            username = username,
            versionId = versionId,
            gameDir = gameDir,
            assetsDir = assetsDir
        )

        val javaBin = jreManager.javaBin.absolutePath
        val command = listOf(javaBin) + jvmArgs + detail.mainClass + gameArgs

        onProgress("正在启动 Minecraft $versionId...")

        val pb = ProcessBuilder(command)
        pb.directory(gameDir)
        pb.redirectErrorStream(true)
        pb.environment().apply {
            put("HOME", gameDir.absolutePath)
            put("TMPDIR", File(gameDir, "tmp").also { it.mkdirs() }.absolutePath)
        }

        pb.start()
    }

    private suspend fun extractNatives(detail: VersionDetail, libsDir: File, nativesDir: File) {
        for (lib in detail.libraries) {
            if (lib.natives.isEmpty()) continue

            // Determine native classifier
            val osKey = "linux" // Android runs on Linux kernel
            val nativeSuffix = lib.natives[osKey] ?: continue

            // Try arch-specific first, then generic
            val classifiers = lib.downloads.classifiers
            val classifierKey = "$nativeSuffix-${DeviceArch.nativeKey}"
            val artifact = classifiers[classifierKey] ?: classifiers[nativeSuffix] ?: continue

            val jarFile = File(libsDir, artifact.path)
            if (!jarFile.exists()) continue

            // Extract .so files from native jar
            extractSoFromJar(jarFile, nativesDir)
        }
    }

    private fun extractSoFromJar(jar: File, dest: File) {
        ZipInputStream(jar.inputStream().buffered()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val name = entry.name
                if ((name.endsWith(".so") || name.endsWith(".dylib")) && !entry.isDirectory) {
                    val target = File(dest, File(name).name)
                    if (!target.exists()) {
                        target.outputStream().buffered().use { out -> zip.copyTo(out) }
                    }
                }
                entry = zip.nextEntry
            }
        }
    }

    private fun buildClasspath(versionsDir: File, libsDir: File, detail: VersionDetail): String {
        val parts = mutableListOf<String>()
        // Client jar
        parts.add(File(versionsDir, "${detail.assetIndex.id}.jar").absolutePath)
        // All libraries
        for (lib in detail.libraries) {
            val a = lib.downloads.artifact
            if (a.path.isNotEmpty()) {
                val f = File(libsDir, a.path)
                if (f.exists()) parts.add(f.absolutePath)
            }
        }
        return parts.joinToString(File.pathSeparator)
    }

    private fun buildJvmArgs(
        versionId: String,
        nativesDir: File,
        classpath: String,
        maxMemory: String,
        detail: VersionDetail,
        gameDir: File,
        assetsDir: File
    ): List<String> {
        return mutableListOf(
            "-Xmx$maxMemory",
            "-Djava.library.path=${nativesDir.absolutePath}",
            "-Dminecraft.launcher.brand=kmmcl",
            "-Dminecraft.launcher.version=1.0.0",
            "-Dfml.ignoreInvalidMinecraftCertificates=true",
            "-Dfml.ignorePatchDiscrepancies=true",
            "-Duser.home=${gameDir.absolutePath}",
            "-cp", classpath
        )
    }

    private fun buildGameArgs(
        username: String,
        versionId: String,
        gameDir: File,
        assetsDir: File
    ): List<String> {
        return listOf(
            "--username", username,
            "--version", versionId,
            "--gameDir", gameDir.absolutePath,
            "--assetsDir", assetsDir.absolutePath,
            "--assetIndex", versionId,
            "--uuid", "00000000-0000-0000-0000-000000000000",
            "--accessToken", "0",
            "--userType", "legacy",
            "--versionType", "release"
        )
    }
}
