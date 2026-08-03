package com.kmmcl.core.download

/**
 * A downloadable resource with optional checksum and mirror chain.
 *
 * Used by [DownloadEngine] to try fallback mirrors when the primary URL fails.
 */
data class DownloadSource(
    val url: String,
    val sha256: String? = null,
    /**
     * Ordered fallback mirrors for this URL.
     * Each mirror is a complete alternative URL, not a prefix.
     * [DownloadEngine] will try url first, then mirrors in order.
     */
    val fallbackUrls: List<String> = emptyList(),
)
