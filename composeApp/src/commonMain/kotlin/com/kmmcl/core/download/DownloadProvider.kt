package com.kmmcl.core.download

/**
 * Abstraction for Minecraft download sources.
 * Allows dynamic switching between Mojang official, BMCLAPI mirror, and custom mirrors.
 *
 * Reference: HMCL DownloadProvider / AutoDownloadProvider
 */
interface DownloadProvider {
    /** Full URL for version manifest V2 JSON. */
    val versionManifestUrl: String

    /** Base URL prefix for individual version detail JSONs. */
    val versionBaseUrl: String

    /** Base URL prefix for asset/resource downloads. */
    val resourceBaseUrl: String

    /** Base URL prefix for library (maven) downloads. */
    val libraryBaseUrl: String

    /** Human-readable provider name for logging. */
    val name: String

    companion object {
        val MOJANG = object : DownloadProvider {
            override val versionManifestUrl = "https://launchermeta.mojang.com/mc/game/version_manifest_v2.json"
            override val versionBaseUrl = "https://launchermeta.mojang.com"
            override val resourceBaseUrl = "https://resources.download.minecraft.net"
            override val libraryBaseUrl = "https://libraries.minecraft.net"
            override val name = "Mojang"
        }

        val BMCLAPI = object : DownloadProvider {
            override val versionManifestUrl = "https://bmclapi2.bangbang93.com/mc/game/version_manifest_v2.json"
            override val versionBaseUrl = "https://bmclapi2.bangbang93.com"
            override val resourceBaseUrl = "https://bmclapi2.bangbang93.com/assets"
            override val libraryBaseUrl = "https://bmclapi2.bangbang93.com/maven"
            override val name = "BMCLAPI"
        }
    }
}

/**
 * Auto-fallback download provider that tries providers in order.
 * On any download failure, automatically switches to the next provider.
 */
class AutoDownloadProvider(
    private val providers: List<DownloadProvider>
) : DownloadProvider {
    private var currentIndex = 0

    val current: DownloadProvider
        get() = providers[currentIndex]

    override val versionManifestUrl get() = current.versionManifestUrl
    override val versionBaseUrl get() = current.versionBaseUrl
    override val resourceBaseUrl get() = current.resourceBaseUrl
    override val libraryBaseUrl get() = current.libraryBaseUrl
    override val name get() = current.name

    /** Switch to the next provider. Returns false if already on the last one. */
    fun switchToNext(): Boolean {
        if (currentIndex < providers.size - 1) {
            currentIndex++
            return true
        }
        return false
    }

    /** Reset back to the first provider. */
    fun reset() {
        currentIndex = 0
    }
}

/**
 * Helper: remap a Mojang-origin URL to the active provider.
 * Only remaps URLs from known Mojang domains; passes through already-mirrored or unknown URLs.
 */
fun remapUrl(original: String, provider: DownloadProvider): String {
    return when {
        original.contains("launchermeta.mojang.com/mc/game/version_manifest") ->
            provider.versionManifestUrl
        original.contains("launchermeta.mojang.com") ->
            original.replace("https://launchermeta.mojang.com", provider.versionBaseUrl)
        original.contains("libraries.minecraft.net") ->
            original.replace("https://libraries.minecraft.net", provider.libraryBaseUrl)
        original.contains("resources.download.minecraft.net") ->
            original.replace("https://resources.download.minecraft.net", provider.resourceBaseUrl)
        else -> original
    }
}
