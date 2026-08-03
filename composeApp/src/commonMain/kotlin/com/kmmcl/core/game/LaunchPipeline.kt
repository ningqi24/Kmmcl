package com.kmmcl.core.game

import com.kmmcl.core.game.ManifestResolver.ResolvedManifest
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Builds the Minecraft launch command line from a resolved manifest.
 *
 * Reference: HMCL DefaultLauncher 15-step pipeline with log4j RCE patch,
 * JVM tuning flags, argument template substitution, and library classpath assembly.
 */
object LaunchPipeline {

    /** Full result of building a launch command. */
    data class LaunchCommand(
        val commandLine: List<String>,
        val mainClass: String,
        val jvmArgs: List<String>,
        val gameArgs: List<String>,
        val classpath: List<String>,
    )

    /**
     * Build the complete launch command for a resolved version manifest.
     *
     * @param manifest     Fully resolved version manifest (inheritance chain folded)
     * @param gameDir      .minecraft directory
     * @param javaPath     Path to the Java executable
     * @param authName     Player account name for --username
     * @param authToken    Access token for --token (pass empty for offline)
     * @param authUuid     Player UUID
     * @param nativesDir   Directory containing extracted native libraries
     * @param libsDir      Directory containing downloaded library JARs
     * @param versionJar   Path to the version client JAR
     * @param ramMin       Minimum heap in MB (default 512)
     * @param ramMax       Maximum heap in MB (default 4096)
     * @param windowWidth  Game window width
     * @param windowHeight Game window height
     */
    fun build(
        manifest: ResolvedManifest,
        gameDir: String,
        javaPath: String = "java",
        authName: String,
        authToken: String = "",
        authUuid: String,
        nativesDir: String,
        libsDir: String,
        versionJar: String,
        ramMin: Int = 512,
        ramMax: Int = 4096,
        windowWidth: Int = 854,
        windowHeight: Int = 480,
    ): LaunchCommand {
        val jvmArgs = mutableListOf<String>()
        val gameArgs = mutableListOf<String>()

        // ── Step 1: JVM heap ───────────────────────────────────────
        jvmArgs += "-Xms${ramMin}M"
        jvmArgs += "-Xmx${ramMax}M"

        // ── Step 2: Garbage collector (G1GC for Java 9+) ───────────
        if (manifest.javaVersion.majorVersion >= 9) {
            jvmArgs += "-XX:+UseG1GC"
            jvmArgs += "-XX:+UnlockExperimentalVMOptions"
            jvmArgs += "-XX:G1NewSizePercent=20"
            jvmArgs += "-XX:G1ReservePercent=20"
            jvmArgs += "-XX:MaxGCPauseMillis=50"
            jvmArgs += "-XX:G1HeapRegionSize=16m"
        }

        // ── Step 3: Native library path ────────────────────────────
        jvmArgs += "-Djava.library.path=$nativesDir"

        // ── Step 4: Log4j RCE patch (CVE-2021-44228) ──────────────
        jvmArgs += "-Dlog4j2.formatMsgNoLookups=true"
        jvmArgs += "-Dlog4j.configurationFile=$gameDir/assets/log_configs/client-1.12.xml"

        // ── Step 5: Classpath assembly (all library JARs + version JAR)
        val classpath = buildClasspath(libsDir, versionJar, manifest.libraries)
        jvmArgs += "-cp"
        jvmArgs += classpath.joinToString(System.getProperty("path.separator", ":"))

        // ── Step 6: Logging arguments from manifest ────────────────
        manifest.logging.client.argument.split(" ").filter { it.isNotBlank() }.forEach {
            jvmArgs += it.replace("\${game_directory}", gameDir)
        }

        // ── Step 7: Main class ─────────────────────────────────────
        val mainClass = manifest.mainClass

        // ── Step 8: Prepare template vars for arguments ────────────
        val templateVars = mapOf(
            "auth_player_name" to authName,
            "auth_token" to authToken,
            "auth_uuid" to authUuid,
            "version_name" to manifest.id,
            "game_directory" to gameDir,
            "assets_root" to "$gameDir/assets",
            "assets_index_name" to manifest.assetIndex.id,
            "version_type" to manifest.type,
            "user_type" to "mojang",
            "resolution_width" to windowWidth.toString(),
            "resolution_height" to windowHeight.toString(),
            "launcher_name" to "Kmmcl",
            "launcher_version" to "0.1.0",
        )

        // ── Step 9: Build game args from structured arguments ──────
        val legacyArgs = manifest.minecraftArgs
        if (manifest.arguments != null) {
            // Use new-style structured arguments if present
            extractStringsOnly(manifest.arguments.game, templateVars).forEach { arg ->
                gameArgs += resolveTemplates(arg, templateVars)
            }
        } else if (legacyArgs != null) {
            // Fall back to legacy minecraftArguments string (pre-1.13)
            legacyArgs.split(" ").filter { it.isNotBlank() }.forEach { arg ->
                gameArgs += resolveTemplates(arg, templateVars)
            }
        }

        // ── Step 10: Build JVM args from structured arguments ─────
        if (manifest.arguments != null) {
            extractStringsOnly(manifest.arguments.jvm, templateVars).forEach { arg ->
                jvmArgs += resolveTemplates(arg, templateVars)
            }
        }

        // ── Step 11: Assemble full command line ───────────────────
        val commandLine = listOf(javaPath) + jvmArgs + mainClass + gameArgs

        return LaunchCommand(
            commandLine = commandLine,
            mainClass = mainClass,
            jvmArgs = jvmArgs,
            gameArgs = gameArgs,
            classpath = classpath,
        )
    }

    // ── Private helpers ─────────────────────────────────────────────

    /** Extract only plain string entries from a mixed argument list, ignoring rule-object entries. */
    private fun extractStringsOnly(
        elements: List<kotlinx.serialization.json.JsonElement>,
        templateVars: Map<String, String>,
    ): List<String> {
        return elements.mapNotNull { elem ->
            when {
                elem is kotlinx.serialization.json.JsonPrimitive && elem.isString ->
                    elem.content
                // Rules with values: if the rules apply (os check), extract nested values
                elem is kotlinx.serialization.json.JsonObject -> {
                    val rules = elem["rules"]?.jsonArray
                    val value = elem["value"]
                    if (rules != null && value != null && isAllowed(rules)) {
                        when {
                            value is kotlinx.serialization.json.JsonPrimitive && value.isString -> value.content
                            value is kotlinx.serialization.json.JsonArray -> value.joinToString(" ") {
                                if (it is kotlinx.serialization.json.JsonPrimitive) it.content else ""
                            }
                            else -> null
                        }
                    } else null
                }
                else -> null
            }
        }
    }

    /** Normalised OS name for rule matching (Mojang uses "linux" / "osx" / "windows"). */
    private val currentOs: String by lazy {
        val raw = System.getProperty("os.name", "").lowercase()
        when {
            raw.contains("linux") || raw.contains("android") -> "linux"
            raw.contains("mac") || raw.contains("darwin") -> "osx"
            raw.contains("windows") || raw.contains("win") -> "windows"
            else -> "linux" // safest fallback for headless/JVM
        }
    }

    /** Check if the current platform is allowed by the rules array. */
    private fun isAllowed(rules: kotlinx.serialization.json.JsonArray): Boolean {
        var result = false
        var hasRule = false
        for (rule in rules) {
            rule as? kotlinx.serialization.json.JsonObject ?: continue
            val action = rule["action"]?.let {
                (it as? kotlinx.serialization.json.JsonPrimitive)?.content
            } ?: continue
            val os = rule["os"]?.jsonObject
            if (os != null) {
                val osName = os["name"]?.let {
                    (it as? kotlinx.serialization.json.JsonPrimitive)?.content
                }
                if (osName == currentOs) {
                    result = action == "allow"
                    hasRule = true
                }
            }
        }
        return if (hasRule) result else true // allow by default
    }

    /** Replace ${key} / $key template placeholders in an argument string. */
    private fun resolveTemplates(arg: String, vars: Map<String, String>): String {
        var result = arg
        // ${key} form
        val pattern1 = Regex("\\$\\{(\\w+)\\}")
        result = pattern1.replace(result) { mr ->
            vars[mr.groupValues[1]] ?: mr.value
        }
        // $key form (only at start or after space)
        val pattern2 = Regex("\\$(\\w+)")
        result = pattern2.replace(result) { mr ->
            vars[mr.groupValues[1]] ?: mr.value
        }
        return result
    }

    /** Build the full classpath string from library paths and the version JAR. */
    private fun buildClasspath(
        libsDir: String,
        versionJar: String,
        libraries: List<com.kmmcl.data.model.Library>,
    ): List<String> {
        val cp = mutableListOf<String>()
        cp.add(versionJar)
        for (lib in libraries) {
            val path = lib.downloads.artifact.path
            if (path.isNotEmpty()) {
                cp.add("$libsDir/$path")
            }
        }
        return cp
    }
}
