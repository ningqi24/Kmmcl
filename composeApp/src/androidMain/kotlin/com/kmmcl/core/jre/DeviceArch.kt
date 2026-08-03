package com.kmmcl.core.jre

import android.os.Build
import com.kmmcl.core.platform.Architecture
import com.kmmcl.core.platform.AndroidArchitectureDetector
import com.kmmcl.data.model.JRE_17
import com.kmmcl.data.model.MIRROR_PREFIXES

/**
 * Legacy arch-detection helper, now delegating to [AndroidArchitectureDetector].
 *
 * This file is kept for minimal backward compatibility during the migration.
 * New code should inject [AndroidArchitectureDetector] directly.
 */
@Deprecated(
    "Inject AndroidArchitectureDetector instead",
    ReplaceWith("AndroidArchitectureDetector", "com.kmmcl.core.platform.AndroidArchitectureDetector")
)
object DeviceArch {

    private val detector = AndroidArchitectureDetector

    /** Resolve [Architecture] from the current Android device. */
    fun detect(): Architecture = detector.detect()

    /** All possible JRE URLs for the current device architecture. */
    val jreUrls: List<String> by lazy {
        val arch = detect()
        val baseUrl = JRE_17.archMap[arch]
        if (baseUrl != null) {
            listOf(baseUrl) + MIRROR_PREFIXES.map { "$it$baseUrl" }
        } else {
            emptyList()
        }
    }
}
