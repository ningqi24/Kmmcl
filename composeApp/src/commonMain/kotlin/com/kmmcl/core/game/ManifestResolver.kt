package com.kmmcl.core.game

import com.kmmcl.data.model.*
import kotlinx.serialization.json.*

/**
 * Resolves Minecraft version inheritance chains.
 *
 * Reference: HMCL GameInstanceManifest.merge() + Resolved record.
 *
 * Mojang version JSONs use "inheritsFrom" to avoid duplicating base version data.
 * Example: 1.20.5 inheritsFrom 1.20.4, which inheritsFrom 1.20.3, ...
 * This resolver folds the entire chain into a single self-contained manifest.
 */
class ManifestResolver(private val versionService: VersionService) {
    /**
     * Result of resolving a version's inheritance chain.
     *
     * @param id           The version id (e.g. "1.20.5")
     * @param mainClass    Main class, resolved from chain: child ?: parent ?: default
     * @param libraries    Merged libraries from entire chain (child + all parents, deduplicated)
     * @param assetIndex   Asset index, child preferred over parent
     * @param javaVersion  Java version requirement, child preferred over parent
     * @param logging      Logging config, child preferred over parent
     * @param arguments    Structured JVM+game arguments (parsed from JSON)
     * @param minecraftArgs Legacy minecraftArguments string (pre-1.13 format)
     * @param downloads    Download info (client/server JAR URLs)
     * @param type         Release type (release/snapshot)
     */
    data class ResolvedManifest(
        val id: String,
        val mainClass: String,
        val libraries: List<Library>,
        val assetIndex: AssetIndex,
        val javaVersion: JavaVersion,
        val logging: LoggingConfig,
        val arguments: Arguments?,
        val minecraftArgs: String?,
        val downloads: Downloads,
        val type: String,
    )

    /**
     * Resolve a version's full inheritance chain.
     * @param versionUrl URL to the version's detail JSON (from manifest)
     */
    suspend fun resolve(versionUrl: String): Result<ResolvedManifest> = runCatching {
        val detail = versionService.fetchVersionDetail(versionUrl).getOrThrow()

        // Collect parent chain (bottom-up: root -> ... -> direct parent)
        val parents = mutableListOf<VersionDetail>()
        var parentId: String? = detail.inheritsFrom
        while (parentId != null) {
            val parentUrl = versionService.findVersionUrl(parentId)
                .getOrElse { throw IllegalStateException("Inheritance chain broken: parent version '$parentId' not found in manifest") }
            val parent = versionService.fetchVersionDetail(parentUrl)
                .getOrElse { throw IllegalStateException("Failed to fetch parent version detail for '$parentId'") }
            parents.add(parent)
            parentId = parent.inheritsFrom
        }

        // Fold: start from the root-most parent, merge each child on top
        val resolved = parents.foldRight(detail) { parent, child ->
            fold(child, parent)
        }

        buildResolved(resolved)
    }

    /**
     * Merge a child manifest on top of its parent.
     * Child fields take precedence; null/empty child fields fall back to parent.
     */
    private fun fold(child: VersionDetail, parent: VersionDetail): VersionDetail {
        return child.copy(
            mainClass = child.mainClass.ifEmpty { parent.mainClass },
            minecraftArguments = child.minecraftArguments ?: parent.minecraftArguments,
            assetIndex = if (child.assetIndex.url.isNotEmpty()) child.assetIndex else parent.assetIndex,
            javaVersion = if (child.javaVersion.majorVersion != 8 || child.javaVersion.component != "jre-legacy")
                child.javaVersion else parent.javaVersion,
            logging = if (child.logging.client.file.url.isNotEmpty()) child.logging else parent.logging,
            libraries = mergeLibraries(child.libraries, parent.libraries),
            // arguments: child preferred when present
            arguments = child.arguments ?: parent.arguments,
            // inheritsFrom cleared — this is a standalone manifest now
            inheritsFrom = null,
        )
    }

    /** Merge two library lists, deduplicating by name. Child libraries take precedence. */
    private fun mergeLibraries(child: List<Library>, parent: List<Library>): List<Library> {
        val seen = mutableSetOf<String>()
        val result = mutableListOf<Library>()

        for (lib in child) {
            if (seen.add(lib.name)) result.add(lib)
        }
        for (lib in parent) {
            if (seen.add(lib.name)) result.add(lib)
        }
        return result
    }

    /** Convert a resolved VersionDetail into the final ResolvedManifest. */
    private fun buildResolved(detail: VersionDetail): ResolvedManifest {
        // Try parsing structured arguments from JSON
        val arguments = try {
            detail.arguments?.let { element ->
                val obj = element.jsonObject
                Arguments(
                    game = obj["game"]?.jsonArray?.toList() ?: emptyList(),
                    jvm = obj["jvm"]?.jsonArray?.toList() ?: emptyList(),
                )
            }
        } catch (_: Exception) {
            null
        }

        return ResolvedManifest(
            id = detail.id.ifEmpty { "unknown" },
            mainClass = detail.mainClass.ifEmpty { "net.minecraft.client.main.Main" },
            libraries = detail.libraries,
            assetIndex = detail.assetIndex,
            javaVersion = detail.javaVersion,
            logging = detail.logging,
            arguments = arguments,
            minecraftArgs = detail.minecraftArguments,
            downloads = detail.downloads,
            type = detail.type.ifEmpty { "release" },
        )
    }
}
