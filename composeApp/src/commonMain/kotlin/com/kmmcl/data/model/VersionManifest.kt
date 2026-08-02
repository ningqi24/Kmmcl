
package com.kmmcl.data.model

import kotlinx.serialization.Serializable

@Serializable
data class VersionManifest(
    val latest: LatestVersions = LatestVersions(),
    val versions: List<VersionEntry> = emptyList()
)

@Serializable
data class LatestVersions(
    val release: String = "",
    val snapshot: String = ""
)

@Serializable
data class VersionEntry(
    val id: String = "",
    val type: String = "",
    val url: String = "",
    val time: String = "",
    val releaseTime: String = ""
)

@Serializable
data class VersionDetail(
    val downloads: Downloads = Downloads(),
    val libraries: List<Library> = emptyList(),
    val mainClass: String = "",
    val assetIndex: AssetIndex = AssetIndex()
)

@Serializable
data class Downloads(
    val client: DownloadInfo = DownloadInfo(),
    val server: DownloadInfo = DownloadInfo()
)

@Serializable
data class DownloadInfo(
    val sha1: String = "",
    val size: Long = 0,
    val url: String = ""
)

@Serializable
data class Library(
    val name: String = "",
    val downloads: LibraryDownloads = LibraryDownloads(),
    val natives: Map<String, String> = emptyMap(),
    val rules: List<Rule> = emptyList()
)

@Serializable
data class LibraryDownloads(
    val artifact: ArtifactInfo = ArtifactInfo()
)

@Serializable
data class ArtifactInfo(
    val path: String = "",
    val sha1: String = "",
    val size: Long = 0,
    val url: String = ""
)

@Serializable
data class Rule(
    val action: String = "",
    val os: OsRule? = null
)

@Serializable
data class OsRule(
    val name: String = ""
)

@Serializable
data class AssetIndex(
    val id: String = "",
    val sha1: String = "",
    val size: Long = 0,
    val url: String = "",
    val totalSize: Long = 0
)
